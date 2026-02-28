/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getExecutableName;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalEntryKind;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.runtime.ContextFactory;
import io.quasient.pal.common.runtime.Dispatcher;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.Classes;
import io.quasient.pal.core.intercept.AroundInterceptChain;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
import io.quasient.pal.core.intercept.InterceptCheckResult;
import io.quasient.pal.core.intercept.InterceptChecker;
import io.quasient.pal.core.intercept.InterceptPartition;
import io.quasient.pal.core.intercept.ParamTypeExtractor;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.replay.OperationSignature;
import io.quasient.pal.core.replay.ReplayCursor;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.SessionCommandType;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.ColferUtils;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.ConstructorSignature;

/**
 * Base dispatcher for execution messages, providing the common logic for handling local and RPC
 * execution requests within the PAL runtime. This class orchestrates the wrapping, dispatching,
 * invocation, and response handling of execution messages. Subclasses must implement abstract
 * methods to create specific message wrappers and handle invocation details.
 */
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "UCF_USELESS_CONTROL_FLOW"},
    justification =
        "Control flow for AspectJ join points; dead store for explicit variable tracking")
abstract class BaseExecMessageDispatcher extends AbstractDispatcher
    implements Dispatcher, ExecMessageDispatcher {

  /** Thread-local partition for separating intercepts by type without per-dispatch allocation. */
  private static final ThreadLocal<InterceptPartition> TL_PARTITION =
      ThreadLocal.withInitial(InterceptPartition::new);

  /**
   * Dispatches an execution request by performing pre-invocation messaging, invoking the target
   * accessible object, and sending post-invocation messaging.
   *
   * <p>The process involves creating a before-execution message, sending it, invoking the target,
   * storing the return value if applicable, and then wrapping and sending an after-execution
   * message. In case an invocation exception is encountered, this method rethrows the underlying
   * cause.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the executed operation, or null for void operations
   * @throws Throwable if an error occurs during invocation or processing phases
   */
  @Override
  public final Object dispatch(ProceedingJoinPoint pjp) throws Throwable {

    // Replay fast-path: bypass all normal dispatch logic (intercepts, WAL writing, PUB sending)
    if (replayContext != null
        && runOptions != null
        && runOptions.contains(RunOptions.WITH_REPLAY)) {
      return dispatchReplay(pjp);
    }

    // Track in-flight dispatch for intercept coordination (if enabled)
    // This allows intercepts to wait for in-flight calls to complete before activation
    final boolean trackingEnabled =
        runOptions != null && runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING);

    // Extract param types once from the signature for reuse across tracking, intercepts, and
    // callbacks. Uses signature-based caching so repeated calls to the same method skip extraction.
    final String[] paramTypeNames =
        ParamTypeExtractor.extractFromSignature(pjp.getStaticPart().getSignature());

    String className = null;
    String methodName = null;

    if (trackingEnabled) {
      className = getClassNameFromPjp(pjp);
      methodName = getMethodNameFromPjp(pjp);
      try {
        inFlightDispatchTracker.enterDispatch(className, methodName, paramTypeNames);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while entering dispatch", e);
      }
    }

    try {
      final Object sender = pjp.getThis();
      final Object[] args = pjp.getArgs();
      final Object target = pjp.getTarget();

      if (logger.isTraceEnabled()) {
        logger.trace("JoinPoint: {}", pjp.toLongString());
        logger.trace("dispatch:in w/sender: {}, target: {}, args: {}", sender, target, args);
      }

      // Check intercepts BEFORE creating Context/ExecMessage
      final MessageType beforeExecMsgType = getBeforeExecMessageType();
      final boolean isMessageInterceptable =
          InterceptChecker.isInterceptableType(beforeExecMsgType);

      InterceptCheckResult beforeInterceptCheck = null;
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
        beforeInterceptCheck =
            interceptChecker.checkIntercepts(pjp, beforeExecMsgType, ExecPhase.BEFORE);
      }

      // Decide if we need to create messages
      boolean withPubOrWal =
          runOptions.contains(RunOptions.WITH_WAL) || runOptions.contains(RunOptions.WITH_TCP_PUB);
      boolean needsBeforeMessages =
          withPubOrWal || (beforeInterceptCheck != null && beforeInterceptCheck.needsExecMessage());

      Context ctx = null;
      ExecMessage beforeExecMsg = null;

      if (needsBeforeMessages) {
        // Create (or get cached) context instance from join point
        ctx = ContextFactory.forJoinPoint(pjp.getStaticPart());

        // Determine if we should include declared exceptions
        // Include them only when intercepts are registered (to avoid message size increase)
        boolean includeDeclaredExceptions =
            beforeInterceptCheck != null && beforeInterceptCheck.hasAnyIntercepts();

        // 1. Wrap message
        beforeExecMsg =
            createBeforeExecMessage(ctx, sender, target, args, includeDeclaredExceptions);

        // 2. Send message (WAL/PUB only - intercepts handled separately)
        @SuppressWarnings("unused")
        final ExecMessage beforeExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(beforeExecMsg), ExecPhase.BEFORE);
      }

      // Handle LOCAL BEFORE intercepts (no ExecMessage needed for hot-path optimization)
      // Pre-compute param types list once for all intercept callbacks that need List<String>
      final List<String> paramTypesList = ParamTypeExtractor.asList(paramTypeNames);

      Object[] finalArgs = args;
      // Captured local AROUND intercepts from partition (for optimized chain building)
      List<InterceptMessage> localAroundIntercepts = List.of();
      if (beforeInterceptCheck != null && beforeInterceptCheck.hasLocalIntercepts()) {
        // Extract className/methodName once for all BEFORE intercept phases
        if (className == null) {
          className = getClassNameFromPjp(pjp);
          methodName = getMethodNameFromPjp(pjp);
        }

        // Single-pass partition of local intercepts by type
        InterceptPartition beforePartition = TL_PARTITION.get();
        beforePartition.partition(beforeInterceptCheck.getLocalIntercepts());

        // Capture local AROUND intercepts before TL_PARTITION could be reused
        if (!beforePartition.around().isEmpty()) {
          localAroundIntercepts = new ArrayList<>(beforePartition.around());
        }

        if (!beforePartition.before().isEmpty()) {
          ConsolidatedCallbackResponse localResponse =
              localInterceptCallbackDispatcher.sendLocalBeforeCallbacks(
                  beforePartition.before(),
                  finalArgs,
                  className,
                  methodName,
                  paramTypesList,
                  peerUuid.toString());

          // Check if local callback wants to throw an exception
          if (localResponse.shouldThrowException()) {
            throw localResponse.getExceptionToThrow();
          }

          // Apply local argument mutations (local runs first, remote can override)
          if (localResponse.hasArgMutations()) {
            Object[] mutatedArgs = finalArgs.clone();
            for (Map.Entry<Integer, Object> entry : localResponse.getMutatedArgs().entrySet()) {
              int index = entry.getKey();
              Object newValue = entry.getValue();
              if (index >= 0 && index < mutatedArgs.length) {
                mutatedArgs[index] = newValue;
              }
            }
            finalArgs = mutatedArgs;
          }
        }

        // Handle LOCAL BEFORE_ASYNC intercepts (fire-and-forget, no blocking)
        if (!beforePartition.beforeAsync().isEmpty()) {
          localInterceptCallbackDispatcher.sendLocalBeforeAsyncCallbacks(
              beforePartition.beforeAsync(),
              finalArgs,
              className,
              methodName,
              paramTypesList,
              peerUuid.toString());
        }
      }

      // Send REMOTE BEFORE intercept callbacks and apply argument mutations
      if (beforeInterceptCheck != null
          && beforeInterceptCheck.hasRemoteIntercepts()
          && beforeExecMsg != null) {
        InterceptCallbackDispatcher.ConsolidatedCallbackResponse callbackResponse =
            interceptCallbackDispatcher.sendBeforeCallbacks(
                beforeInterceptCheck, beforeExecMsg, args);

        // Check if callback wants to throw an exception
        if (callbackResponse.shouldThrowException()) {
          throw callbackResponse.getExceptionToThrow();
        }

        // Apply argument mutations
        if (callbackResponse.hasArgMutations()) {
          Object[] mutatedArgs = args.clone();
          for (Map.Entry<Integer, Object> entry : callbackResponse.getMutatedArgs().entrySet()) {
            int index = entry.getKey();
            Object newValue = entry.getValue();
            if (index >= 0 && index < mutatedArgs.length) {
              mutatedArgs[index] = newValue;
            }
          }
          finalArgs = mutatedArgs;
        }
      }

      // Build AROUND chain (unified local + remote, onion model)
      // Each proceed() invokes the next layer, not the method directly
      AroundInterceptChain aroundChain = null;
      if (beforeInterceptCheck != null && beforeInterceptCheck.hasAnyIntercepts()) {
        if (className == null) {
          className = getClassNameFromPjp(pjp);
          methodName = getMethodNameFromPjp(pjp);
        }

        // Extract remote AROUND intercepts via partition (reuse TL_PARTITION, local is done)
        List<InterceptMessage> remoteAroundIntercepts = List.of();
        if (beforeInterceptCheck.hasRemoteIntercepts()) {
          InterceptPartition remotePartition = TL_PARTITION.get();
          remotePartition.partition(beforeInterceptCheck.getRemoteIntercepts());
          if (!remotePartition.around().isEmpty()) {
            remoteAroundIntercepts = new ArrayList<>(remotePartition.around());
          }
        }

        // Method invoker for the innermost layer
        final Object[] argsForChain = finalArgs;
        AroundInterceptChain.MethodInvoker methodInvoker =
            (invokeArgs) -> {
              try {
                Object result = invoke(pjp, invokeArgs != null ? invokeArgs : argsForChain);
                return new AfterPhaseData(result, null, returnsVoid(pjp));
              } catch (Throwable th) {
                return new AfterPhaseData(null, th, returnsVoid(pjp));
              }
            };

        aroundChain =
            aroundChainBuilder.build(
                localAroundIntercepts,
                remoteAroundIntercepts,
                className,
                methodName,
                paramTypesList,
                methodInvoker);
      }

      // Execute AROUND chain OR invoke method directly
      Object returnValue = null;
      InvocationThrowableWrapper throwableWrapper = null;

      if (aroundChain != null && !aroundChain.isEmpty()) {
        // Execute through the unified chain (handles local + remote AROUND in proper order)
        AroundInterceptChain.ChainResult chainResult = aroundChain.invoke(finalArgs, beforeExecMsg);

        returnValue = chainResult.returnValue();
        if (chainResult.thrownException() != null) {
          throwableWrapper = new InvocationThrowableWrapper(chainResult.thrownException());
        }
      } else {
        // No AROUND intercepts - invoke method directly
        try {
          returnValue = invoke(pjp, finalArgs);
        } catch (Throwable th) {
          logger.error(
              "Caught throwable while invoking field operation. Will wrap and return it.", th);
          throwableWrapper = new InvocationThrowableWrapper(th);
        }
      }

      // Check intercepts for AFTER phase
      InterceptCheckResult afterInterceptCheck = null;
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
        afterInterceptCheck =
            interceptChecker.checkIntercepts(pjp, getBeforeExecMessageType(), ExecPhase.AFTER);
      }

      boolean needsAfterMessages =
          withPubOrWal || (afterInterceptCheck != null && afterInterceptCheck.needsExecMessage());

      // Handle LOCAL AFTER intercepts first (before remote AFTER)
      // Local intercepts run regardless of whether we have ExecMessage
      if (afterInterceptCheck != null && afterInterceptCheck.hasLocalIntercepts()) {
        // Ensure className/methodName are extracted (may already be set from BEFORE phase)
        if (className == null) {
          className = getClassNameFromPjp(pjp);
          methodName = getMethodNameFromPjp(pjp);
        }

        // Single-pass partition of local intercepts by type
        InterceptPartition afterPartition = TL_PARTITION.get();
        afterPartition.partition(afterInterceptCheck.getLocalIntercepts());

        if (!afterPartition.after().isEmpty()) {
          boolean returnsVoidLocal = returnsVoid(pjp);

          ConsolidatedCallbackResponse localAfterResponse =
              localInterceptCallbackDispatcher.sendLocalAfterCallbacks(
                  afterPartition.after(),
                  finalArgs,
                  returnValue,
                  returnsVoidLocal,
                  throwableWrapper != null ? throwableWrapper.throwable() : null,
                  className,
                  methodName,
                  paramTypesList,
                  peerUuid.toString());

          // Check if local callback wants to throw an exception
          if (localAfterResponse.shouldThrowException()) {
            throwableWrapper =
                new InvocationThrowableWrapper(localAfterResponse.getExceptionToThrow());
          }

          // Apply local return value override (local runs first, remote can override)
          if (localAfterResponse.hasReturnValueOverride()) {
            returnValue = localAfterResponse.getOverriddenReturnValue();
          }
        }

        // Handle LOCAL AFTER_ASYNC intercepts (fire-and-forget, no blocking)
        if (!afterPartition.afterAsync().isEmpty()) {
          boolean returnsVoidLocal = returnsVoid(pjp);

          localInterceptCallbackDispatcher.sendLocalAfterAsyncCallbacks(
              afterPartition.afterAsync(),
              finalArgs,
              returnValue,
              returnsVoidLocal,
              throwableWrapper != null ? throwableWrapper.throwable() : null,
              className,
              methodName,
              paramTypesList,
              peerUuid.toString());
        }
      }

      // Note: LOCAL AROUND AFTER is handled by AroundInterceptChain

      if (needsAfterMessages) {
        // Reuse context if already created
        if (ctx == null) {
          ctx = ContextFactory.forJoinPoint(pjp.getStaticPart());
        }

        // 5. Store? object in object map
        ObjectRef objectRef = null;
        if (returnValue != null) {
          objectRef = generateObjectRef(returnValue);
        }

        boolean returnsVoid = returnsVoid(pjp);

        // 6. Wrap object or exception
        final ExecMessage afterExecMsg =
            createAfterExecMessage(
                ctx,
                throwableWrapper != null ? throwableWrapper : returnValue,
                objectRef,
                returnsVoid);

        // 7. Send object or exception (WAL/PUB only)
        @SuppressWarnings("unused")
        final ExecMessage afterExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(afterExecMsg), ExecPhase.AFTER);

        // 8. Send intercept callbacks (if any remote intercepts matched) and apply return value
        // override
        if (afterInterceptCheck != null && afterInterceptCheck.hasRemoteIntercepts()) {
          InterceptCallbackDispatcher.ConsolidatedCallbackResponse afterCallbackResponse =
              interceptCallbackDispatcher.sendAfterCallbacks(
                  afterInterceptCheck,
                  afterExecMsg,
                  returnValue,
                  returnsVoid,
                  throwableWrapper != null ? throwableWrapper.throwable() : null);

          // Check if callback wants to throw an exception
          if (afterCallbackResponse.shouldThrowException()) {
            throwableWrapper =
                new InvocationThrowableWrapper(afterCallbackResponse.getExceptionToThrow());
          }

          // Apply return value override
          if (afterCallbackResponse.hasReturnValueOverride()) {
            returnValue = afterCallbackResponse.getOverriddenReturnValue();
          }
        }

        // Note: REMOTE AROUND AFTER is handled by AroundInterceptChain
      }

      // 9. Return object or re-raise exception
      if (throwableWrapper != null) {
        if (logger.isTraceEnabled()) {
          logger.trace("dispatch:out re-raising exception: {}", throwableWrapper);
        }
        Throwable invocationThr = throwableWrapper.throwable();
        // we want to throw the cause exception
        if (invocationThr instanceof InvocationTargetException) {
          throw invocationThr.getCause();
        } else {
          throw invocationThr;
        }
      }

      if (logger.isTraceEnabled()) {
        logger.trace("dispatch:out returning object: {}", returnValue);
      }
      return returnValue;
    } finally {
      // Always exit dispatch tracking, even if an exception occurred
      if (trackingEnabled) {
        inFlightDispatchTracker.exitDispatch(className, methodName, paramTypeNames);
      }
    }
  }

  /**
   * Replay fast-path: matches the live operation against the WAL oracle, executes it, verifies the
   * return value, and advances the cursor.
   *
   * <p>This method completely bypasses all intercept logic, WAL writing, PUB sending, and message
   * creation. The replay path is purely: match → execute → verify → advance.
   *
   * <p>Nested operations advance the cursor themselves (each nested {@code dispatch()} call enters
   * this same method), so the cursor correctly walks through the balanced-parentheses WAL
   * structure.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle from AspectJ weaving
   * @return the result of the executed operation
   * @throws Throwable if the invoked operation throws
   */
  private Object dispatchReplay(ProceedingJoinPoint pjp) throws Throwable {
    String threadName = Thread.currentThread().getName();
    ReplayCursor cursor = replayContext.getCursor(threadName);

    // Step 1: Match against WAL
    WalEntry expectedEntry = cursor.peekNext();
    OperationSignature liveSig = OperationSignature.fromJoinPoint(pjp, getBeforeExecMessageType());

    if (expectedEntry == null || expectedEntry.getKind() != WalEntryKind.OPERATION) {
      // Cursor exhausted or unexpected completion entry — extra live operation
      replayContext.getDivergenceDetector().reportExtraOperation(liveSig);
      return invoke(pjp, pjp.getArgs());
    }

    OperationSignature walSig = OperationSignature.fromWalEntry(expectedEntry);
    if (!liveSig.matches(walSig)) {
      // Operation signature mismatch — record divergence but still execute (best-effort)
      replayContext.getDivergenceDetector().reportOperationMismatch(expectedEntry, liveSig);
      return invoke(pjp, pjp.getArgs());
    }

    // Step 2: RE_EXECUTE — advance past operation entry, invoke, then verify completion
    cursor.advance();

    Object result = invoke(pjp, pjp.getArgs());

    // Step 3: Verify return value against WAL completion entry
    WalEntry completionEntry = cursor.peekNext();
    if (completionEntry != null && completionEntry.getKind() == WalEntryKind.COMPLETION) {
      replayContext.getDivergenceDetector().compareReturnValue(completionEntry, result);

      // Register object ref mapping if the return value has a ref in the WAL
      if (result != null) {
        int walRef = extractReturnRef(completionEntry);
        if (walRef != 0) {
          replayContext.getObjectStore().register(walRef, result);
        }
      }

      cursor.advance();
    }

    return result;
  }

  /**
   * Extracts the object reference from a WAL completion entry's return value.
   *
   * @param completionEntry the completion WAL entry (must be a COMPLETION kind)
   * @return the object reference from the return value, or {@code 0} if none
   */
  private static int extractReturnRef(WalEntry completionEntry) {
    ReturnValue returnValue = completionEntry.getRawMessage().getReturnValue();
    if (returnValue == null) {
      return 0;
    }
    Obj obj = returnValue.getObject();
    if (obj == null) {
      return 0;
    }
    return obj.getRef();
  }

  /**
   * Dispatches an incoming (via RPC or Log) execution message.
   *
   * <p>This method validates the message type, optionally sends a BEFORE-phase message to WAL/PUB,
   * and performs the loading and invocation phases including argument extraction, accessible object
   * loading, and target retrieval. It also handles intercept callbacks (BEFORE, AROUND, AFTER) when
   * intercepts are enabled. Finally, it sends an AFTER-phase execution message to complete the
   * processing.
   *
   * <p>The BEFORE WAL/PUB send is controlled by {@link RunOptions#WITH_WAL_INCOMING_RPC} and, for
   * {@link MessageChannelType#LOG_RPC} channels, additionally by {@link
   * RunOptions#WITH_WAL_ALL_INCOMING_RPC}. This mirrors the hot-path {@link
   * #dispatch(ProceedingJoinPoint)} behavior where both BEFORE and AFTER messages are sent,
   * ensuring incoming RPC operations produce the same BEFORE+AFTER message pair in the WAL for
   * time-travel debugging and event sourcing.
   *
   * @param incomingCall the execution message to process
   * @param messageChannel the transport channel through which the message was received
   * @return the execution message received in response after processing the incoming call
   * @throws IllegalArgumentException if the message type is not supported by this dispatcher
   */
  @Override
  public ExecMessage dispatchIncoming(ExecMessage incomingCall, MessageChannelType messageChannel) {

    // Track in-flight dispatch for intercept coordination (if enabled)
    // This allows intercepts to wait for in-flight calls to complete before activation
    final boolean trackingEnabled =
        runOptions != null && runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING);
    String className = null;
    String methodName = null;
    String[] trackingParamTypes = null;

    if (trackingEnabled) {
      className = getClassname(incomingCall);
      methodName = getExecutableName(incomingCall);
      List<String> paramTypeList = getParameterTypes(incomingCall);
      trackingParamTypes = paramTypeList != null ? paramTypeList.toArray(new String[0]) : null;
      try {
        inFlightDispatchTracker.enterDispatch(className, methodName, trackingParamTypes);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while entering dispatch", e);
      }
    }

    try {
      if (logger.isTraceEnabled()) {
        logger.trace(
            "dispatchIncoming:in w/ message id: {}, from peer w/id:{}, channel: {}",
            incomingCall.getMessageId(),
            incomingCall.getPeerUuid(),
            messageChannel);
      }

      // get type
      final MessageType messageType = getMessageTypeOf(incomingCall);

      // check if this dispatcher supports the message type
      if (!getSupportedMessageType().equals(messageType)) {
        throw new IllegalArgumentException(
            "Unsupported message type: " + messageType + " for dispatcher: " + this.getClass());
      }

      // Check intercepts BEFORE loading/invocation phases
      final boolean isMessageInterceptable = InterceptChecker.isInterceptableType(messageType);
      InterceptCheckResult beforeInterceptCheck = null;
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
        List<String> paramTypeList = getParameterTypes(incomingCall);
        String[] paramTypes = paramTypeList != null ? paramTypeList.toArray(new String[0]) : null;
        beforeInterceptCheck =
            interceptChecker.checkIntercepts(
                getClassname(incomingCall),
                getExecutableName(incomingCall),
                paramTypes,
                messageType,
                ExecPhase.BEFORE);
      }

      // Send BEFORE message to WAL/PUB (consistent with hot-path dispatch())
      if (shouldWriteIncomingToWal(messageChannel)) {
        @SuppressWarnings("unused")
        final ExecMessage beforeExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(incomingCall), ExecPhase.BEFORE);
      }

      Throwable exceptionWhileLoading = null;
      Throwable exceptionWhileInvoking = null;
      AccessibleObject accessibleObject = null;
      Object target = null;
      Object value = null;
      List<MessageArgument> args = null;

      // Loading phase
      try {
        // 1. Extract and load parameter types from message
        List<Class<?>> parameterTypes = getParameterTypesFromMessage(incomingCall, messageType);

        // 2. Unwrap and load arguments
        args = getArgsFromMessage(incomingCall, parameterTypes);

        // 3. Load constructor/method/field to call
        accessibleObject =
            loadAccessibleObject(
                incomingCall, parameterTypes, args.stream().map(MessageArgument::object).toList());

        // 4. Load target for instance methods/field ops
        target = getTargetFromMessage(incomingCall);

        // 5. Load value for assigning field ops
        value = getValueFromMessage(incomingCall, accessibleObject);

        // 6. (Optionally) Set field/method accessible, allowing to break Java access rules
        if (allowNonPublicAccess) { // extra-check, since already checked in loadAccessibleObject
          accessibleObject.setAccessible(true);
        }
      } catch (ReflectiveOperationException | AmbiguousCallException | RuntimeException ex) {
        logger.error("Error during loading phase", ex);
        exceptionWhileLoading = ex;
      }

      // Process BEFORE intercept callbacks and apply argument mutations
      List<MessageArgument> finalArgs = args;

      // Captured local AROUND intercepts from partition (for optimized chain building)
      List<InterceptMessage> localAroundIntercepts = List.of();

      // Handle LOCAL BEFORE intercepts first (before remote)
      if (exceptionWhileLoading == null
          && beforeInterceptCheck != null
          && beforeInterceptCheck.hasLocalIntercepts()) {
        // Single-pass partition of local intercepts by type
        InterceptPartition beforePartition = TL_PARTITION.get();
        beforePartition.partition(beforeInterceptCheck.getLocalIntercepts());

        // Capture local AROUND intercepts before TL_PARTITION could be reused
        if (!beforePartition.around().isEmpty()) {
          localAroundIntercepts = new ArrayList<>(beforePartition.around());
        }

        if (!beforePartition.before().isEmpty()) {
          className = getClassname(incomingCall);
          methodName = getExecutableName(incomingCall);
          List<String> paramTypesList = getParameterTypes(incomingCall);
          if (paramTypesList == null) {
            paramTypesList = List.of();
          }

          // Extract current arg values for callbacks
          Object[] argValues;
          if (isFieldPutOperation(messageType) && value != null) {
            argValues = new Object[] {value};
          } else {
            argValues = args.stream().map(MessageArgument::object).toArray(Object[]::new);
          }

          ConsolidatedCallbackResponse localBeforeResponse =
              localInterceptCallbackDispatcher.sendLocalBeforeCallbacks(
                  beforePartition.before(),
                  argValues,
                  className,
                  methodName,
                  paramTypesList,
                  peerUuid.toString());

          // Check if local callback wants to throw an exception
          if (localBeforeResponse.shouldThrowException()) {
            exceptionWhileInvoking = localBeforeResponse.getExceptionToThrow();
          }

          // Apply local argument mutations (local runs first, remote can override)
          if (exceptionWhileInvoking == null && localBeforeResponse.hasArgMutations()) {
            if (isFieldPutOperation(messageType)) {
              Object mutatedValue = localBeforeResponse.getMutatedArgs().get(0);
              if (mutatedValue != null) {
                value = mutatedValue;
              }
            } else {
              List<MessageArgument> mutatedArgs = new ArrayList<>(args);
              for (Map.Entry<Integer, Object> entry :
                  localBeforeResponse.getMutatedArgs().entrySet()) {
                int index = entry.getKey();
                Object newValue = entry.getValue();
                if (index >= 0 && index < mutatedArgs.size()) {
                  mutatedArgs.set(
                      index, new MessageArgument(newValue, args.get(index).byReference()));
                }
              }
              finalArgs = mutatedArgs;
            }
          }
        }

        // Handle LOCAL BEFORE_ASYNC intercepts (fire-and-forget, no blocking)
        if (!beforePartition.beforeAsync().isEmpty()) {
          IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
          Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, args);

          localInterceptCallbackDispatcher.sendLocalBeforeAsyncCallbacks(
              beforePartition.beforeAsync(),
              argValues,
              meta.className(),
              meta.methodName(),
              meta.paramTypes(),
              peerUuid.toString());
        }
      }

      // Build AROUND chain (unified local + remote, onion model)
      // Note: AROUND chain is built after BEFORE intercepts but before invocation
      AroundInterceptChain aroundChain = null;
      if (exceptionWhileLoading == null
          && exceptionWhileInvoking == null
          && beforeInterceptCheck != null
          && beforeInterceptCheck.hasAnyIntercepts()) {
        className = getClassname(incomingCall);
        methodName = getExecutableName(incomingCall);
        List<String> paramTypesList = getParameterTypes(incomingCall);
        if (paramTypesList == null) {
          paramTypesList = List.of();
        }

        // Extract remote AROUND intercepts via partition (reuse TL_PARTITION, local is done)
        List<InterceptMessage> remoteAroundIntercepts = List.of();
        if (beforeInterceptCheck.hasRemoteIntercepts()) {
          InterceptPartition remotePartition = TL_PARTITION.get();
          remotePartition.partition(beforeInterceptCheck.getRemoteIntercepts());
          if (!remotePartition.around().isEmpty()) {
            remoteAroundIntercepts = new ArrayList<>(remotePartition.around());
          }
        }

        // Capture for lambda
        final AccessibleObject accessibleForChain = accessibleObject;
        final Object targetForChain = target;
        final Object valueForChain = value;
        final List<MessageArgument> argsForChain = finalArgs;
        final boolean isFieldPut = isFieldPutOperation(messageType);
        final String threadAffinityForChain = incomingCall.getThreadAffinity();

        AroundInterceptChain.MethodInvoker methodInvoker =
            (invokeArgs) -> {
              try {
                // Convert args back to MessageArgument list if needed
                List<MessageArgument> invokeArgsList = argsForChain;
                if (invokeArgs != null) {
                  invokeArgsList = new ArrayList<>(argsForChain.size());
                  for (int i = 0; i < argsForChain.size() && i < invokeArgs.length; i++) {
                    invokeArgsList.add(
                        new MessageArgument(invokeArgs[i], argsForChain.get(i).byReference()));
                  }
                }
                // For field PUT operations, use the mutated value from invokeArgs[0]
                Object invokeValue = valueForChain;
                if (isFieldPut && invokeArgs != null && invokeArgs.length > 0) {
                  invokeValue = invokeArgs[0];
                }
                // Route through ThreadAffinityDispatcher for thread affinity support
                final List<MessageArgument> chainArgs = invokeArgsList;
                final Object chainValue = invokeValue;
                Object result =
                    threadAffinityDispatcher.execute(
                        threadAffinityForChain,
                        () ->
                            invokeIncoming(
                                accessibleForChain, targetForChain, chainArgs, chainValue));
                boolean isVoid = accessibleForChain != null && returnsVoid(accessibleForChain);
                return new AfterPhaseData(result, null, isVoid);
              } catch (Throwable th) {
                Throwable cause = th instanceof InvocationTargetException ? th.getCause() : th;
                boolean isVoid = accessibleForChain != null && returnsVoid(accessibleForChain);
                return new AfterPhaseData(null, cause, isVoid);
              }
            };

        aroundChain =
            aroundChainBuilder.build(
                localAroundIntercepts,
                remoteAroundIntercepts,
                className,
                methodName,
                paramTypesList,
                methodInvoker);
      }

      // Handle REMOTE BEFORE intercept callbacks
      if (exceptionWhileLoading == null
          && exceptionWhileInvoking == null
          && beforeInterceptCheck != null
          && beforeInterceptCheck.hasRemoteIntercepts()) {
        // Extract current arg values for callbacks
        // For field PUT operations, the value to set is passed as args[0] to callbacks
        Object[] argValues;
        if (isFieldPutOperation(messageType) && value != null) {
          argValues = new Object[] {value};
        } else {
          argValues = args.stream().map(MessageArgument::object).toArray(Object[]::new);
        }

        // Send BEFORE intercept callbacks
        InterceptCallbackDispatcher.ConsolidatedCallbackResponse beforeCallbackResponse =
            interceptCallbackDispatcher.sendBeforeCallbacks(
                beforeInterceptCheck, incomingCall, argValues);

        // Check if callback wants to throw an exception
        if (beforeCallbackResponse.shouldThrowException()) {
          exceptionWhileInvoking = beforeCallbackResponse.getExceptionToThrow();
        }

        // Apply argument mutations from BEFORE callbacks
        if (exceptionWhileInvoking == null && beforeCallbackResponse.hasArgMutations()) {
          // For field PUT operations, args is null; mutation applies to 'value' (index 0)
          if (isFieldPutOperation(messageType)) {
            Object mutatedValue = beforeCallbackResponse.getMutatedArgs().get(0);
            if (mutatedValue != null) {
              value = mutatedValue;
            }
          } else {
            finalArgs = applyArgMutations(args, args, beforeCallbackResponse.getMutatedArgs());
          }
        }
        // Note: REMOTE AROUND is handled by AroundInterceptChain
      }

      // Invocation phase - use AROUND chain if present
      Object returnValue = null;
      ObjectRef objectRef = null;
      if (exceptionWhileLoading == null && exceptionWhileInvoking == null) {
        // Extract arg values for chain invocation
        Object[] argValues;
        if (isFieldPutOperation(messageType) && value != null) {
          argValues = new Object[] {value};
        } else {
          argValues = finalArgs.stream().map(MessageArgument::object).toArray(Object[]::new);
        }

        if (aroundChain != null && !aroundChain.isEmpty()) {
          // Execute through the unified chain (handles local + remote AROUND in proper order)
          AroundInterceptChain.ChainResult chainResult =
              aroundChain.invoke(argValues, incomingCall);

          returnValue = chainResult.returnValue();
          if (chainResult.thrownException() != null) {
            exceptionWhileInvoking = chainResult.thrownException();
          }
        } else {
          // No AROUND intercepts - invoke directly
          try {
            // 7. Invoke constructor/method/field (using possibly mutated args and value)
            // Route through ThreadAffinityDispatcher for thread affinity support
            final AccessibleObject directAccessible = accessibleObject;
            final Object directTarget = target;
            final List<MessageArgument> directArgs = finalArgs;
            final Object directValue = value;
            final String threadAffinity = incomingCall.getThreadAffinity();
            returnValue =
                threadAffinityDispatcher.execute(
                    threadAffinity,
                    () -> invokeIncoming(directAccessible, directTarget, directArgs, directValue));
            if (logger.isTraceEnabled()) {
              String returnedClass =
                  returnValue == null ? "unavailable" : returnValue.getClass().toString();
              logger.trace(
                  "invokeIncoming returnValue: {} of class: {}", returnValue, returnedClass);
            }
          } catch (InvocationTargetException e) {
            logger.error("Error during invocation phase - invoke", e);
            exceptionWhileInvoking = e.getCause();
          } catch (ReflectiveOperationException | IllegalArgumentException e) {
            logger.error("Error during invocation phase - invoke", e);
            exceptionWhileInvoking = e;
          } catch (Exception e) {
            logger.error("Error during invocation phase - thread affinity dispatch", e);
            exceptionWhileInvoking = e;
          }
        }
      }

      // Check intercepts for AFTER phase
      InterceptCheckResult afterInterceptCheck = null;
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
        List<String> paramTypeList = getParameterTypes(incomingCall);
        String[] paramTypes = paramTypeList != null ? paramTypeList.toArray(new String[0]) : null;
        afterInterceptCheck =
            interceptChecker.checkIntercepts(
                getClassname(incomingCall),
                getExecutableName(incomingCall),
                paramTypes,
                messageType,
                ExecPhase.AFTER);
      }

      // 8. Map returnValue: add new entry in objectRef->object map
      if (exceptionWhileLoading == null && exceptionWhileInvoking == null) {
        try {
          if (!returnsVoid(accessibleObject) && returnValue != null) {
            objectRef = storeObject(returnValue);
          }
        } catch (RuntimeException e) {
          logger.error("Error after invocation phase - mapping objectref -> return value", e);
        }

        // 9. Save returnValue to peer's session
        if (objectRef != null && incomingCall.getPeerUuid() != null) {
          try {
            final UUID peerUuid = UUID.fromString(incomingCall.getPeerUuid());
            storeObjectInSession(peerUuid, objectRef);
          } catch (RuntimeException e) {
            logger.error("Error after invocation phase - saving return value to session", e);
          }
        }
      }

      // Handle LOCAL AFTER intercepts first (before remote)
      if (afterInterceptCheck != null && afterInterceptCheck.hasLocalIntercepts()) {
        // Single-pass partition of local intercepts by type
        InterceptPartition afterPartition = TL_PARTITION.get();
        afterPartition.partition(afterInterceptCheck.getLocalIntercepts());

        if (!afterPartition.after().isEmpty()) {
          IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
          boolean returnsVoidLocal = accessibleObject != null && returnsVoid(accessibleObject);
          Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, null);

          ConsolidatedCallbackResponse localAfterResponse =
              localInterceptCallbackDispatcher.sendLocalAfterCallbacks(
                  afterPartition.after(),
                  argValues,
                  returnValue,
                  returnsVoidLocal,
                  exceptionWhileInvoking,
                  meta.className(),
                  meta.methodName(),
                  meta.paramTypes(),
                  peerUuid.toString());

          // Check if local callback wants to throw an exception
          if (localAfterResponse.shouldThrowException()) {
            exceptionWhileInvoking = localAfterResponse.getExceptionToThrow();
          }

          // Apply local return value override (local runs first, remote can override)
          if (localAfterResponse.hasReturnValueOverride()) {
            returnValue = localAfterResponse.getOverriddenReturnValue();
          }
        }

        // Handle LOCAL AFTER_ASYNC intercepts (fire-and-forget, no blocking)
        if (!afterPartition.afterAsync().isEmpty()) {
          IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
          boolean returnsVoidLocal = accessibleObject != null && returnsVoid(accessibleObject);
          Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, null);

          localInterceptCallbackDispatcher.sendLocalAfterAsyncCallbacks(
              afterPartition.afterAsync(),
              argValues,
              returnValue,
              returnsVoidLocal,
              exceptionWhileInvoking,
              meta.className(),
              meta.methodName(),
              meta.paramTypes(),
              peerUuid.toString());
        }
      }

      // Note: LOCAL AROUND AFTER is handled by AroundInterceptChain

      // 10. Wrap object or exception
      final ExecMessage afterExecMsg =
          createAfterExecMessage(
              incomingCall,
              returnValue,
              objectRef,
              accessibleObject,
              exceptionWhileLoading,
              exceptionWhileInvoking);

      // Send REMOTE AFTER intercept callbacks and apply return value overrides
      if (afterInterceptCheck != null && afterInterceptCheck.hasRemoteIntercepts()) {
        boolean returnsVoidMethod = accessibleObject != null && returnsVoid(accessibleObject);
        InterceptCallbackDispatcher.ConsolidatedCallbackResponse afterCallbackResponse =
            interceptCallbackDispatcher.sendAfterCallbacks(
                afterInterceptCheck,
                afterExecMsg,
                returnValue,
                returnsVoidMethod,
                exceptionWhileInvoking);

        // Check if callback wants to throw an exception
        if (afterCallbackResponse.shouldThrowException()) {
          exceptionWhileInvoking = afterCallbackResponse.getExceptionToThrow();
        }

        // Apply return value override
        if (afterCallbackResponse.hasReturnValueOverride()) {
          returnValue = afterCallbackResponse.getOverriddenReturnValue();
        }
      }

      // Note: REMOTE AROUND AFTER is handled by AroundInterceptChain

      // Recreate afterExecMsg if returnValue was overridden by callbacks
      final ExecMessage finalAfterExecMsg;
      if (afterInterceptCheck != null) {
        // Regenerate the after exec message with potentially modified return value/exception
        finalAfterExecMsg =
            createAfterExecMessage(
                incomingCall,
                returnValue,
                objectRef,
                accessibleObject,
                exceptionWhileLoading,
                exceptionWhileInvoking);
      } else {
        finalAfterExecMsg = afterExecMsg;
      }

      // 11. Send object or exception to WAL/PUB (consistent with BEFORE message gating)
      final ExecMessage afterExecResponseMsg;
      if (shouldWriteIncomingToWal(messageChannel)) {
        afterExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(finalAfterExecMsg), ExecPhase.AFTER);
      } else {
        afterExecResponseMsg = finalAfterExecMsg;
      }

      // 12. Return received message
      if (logger.isTraceEnabled()) {
        logger.trace(
            "dispatchIncoming:out returning message: {}", ColferUtils.format(afterExecResponseMsg));
      }
      return afterExecResponseMsg;
    } finally {
      // Always exit dispatch tracking, even if an exception occurred
      if (trackingEnabled) {
        inFlightDispatchTracker.exitDispatch(className, methodName, trackingParamTypes);
      }
    }
  }

  /**
   * Generates a unique {@link ObjectRef} for the given object based on its identity hash code.
   *
   * @param object the object for which to generate a reference
   * @return a new {@link ObjectRef} instance encapsulating the object's identity
   */
  protected ObjectRef generateObjectRef(Object object) {
    return ObjectRef.from(System.identityHashCode(object));
  }

  /**
   * Stores the provided object in the lookup store and returns its reference.
   *
   * @param object the object to be stored; if null, no action is taken
   * @return an ObjectRef representing the stored object, or null if the object is null
   */
  final ObjectRef storeObject(Object object) {
    return object != null ? objectLookupStore.storeObject(object) : null;
  }

  /**
   * Extracts parameter types from the message, and loads the classes for each parameter.
   *
   * @param execMessage The message to extract parameter types from
   * @param messageType The type of the message
   * @return List of loaded classes for each parameter, or null if execMessage is not a call to
   *     constructor/method.
   * @throws ClassNotFoundException if a parameter class cannot be found
   */
  private List<Class<?>> getParameterTypesFromMessage(
      ExecMessage execMessage, MessageType messageType) throws ClassNotFoundException {

    final List<Class<?>> paramClasses = new ArrayList<>();
    List<Parameter> parameterList = getParameterList(execMessage);

    if (messageType.equals(MessageType.EXEC_CONSTRUCTOR)
        || messageType.equals(MessageType.EXEC_CLASS_METHOD)
        || messageType.equals(MessageType.EXEC_INSTANCE_METHOD)) {
      for (Parameter param : parameterList) {
        if (param.getValue().getClazz() == null
            || param.getValue().getClazz().getName().isEmpty()) {
          paramClasses.add(null);
        } else {
          Class<?> primitiveClass =
              Classes.getClassForPrimitive(param.getValue().getClazz().getName());
          if (primitiveClass != null) { // param is primitive
            paramClasses.add(primitiveClass);
          } else { // i.e. not a primitive
            paramClasses.add(
                Class.forName(
                    param.getValue().getClazz().getName(),
                    true,
                    Thread.currentThread().getContextClassLoader()));
          }
        }
      }
    } else {
      return null;
    }

    return paramClasses;
  }

  /**
   * Extracts and unwraps arguments from the execution message based on the provided parameter
   * types.
   *
   * <p>This method iterates over the parameters contained in the message, attempts to look up
   * objects by reference, and, if not found, unwraps the object to its expected type.
   *
   * @param execMessage the execution message containing parameters
   * @param parameterTypes the list of expected parameter classes for proper unwrapping
   * @return a list of MessageArgument instances encapsulating the unwrapped arguments along with a
   *     flag indicating whether the argument was retrieved by reference
   */
  private List<MessageArgument> getArgsFromMessage(
      ExecMessage execMessage, List<Class<?>> parameterTypes) {

    final List<MessageArgument> args = new ArrayList<>();
    final List<Parameter> parameterList = getParameterList(execMessage);

    int i = 0;
    if (parameterList != null) {
      for (Parameter parameter : parameterList) {
        if (logger.isTraceEnabled()) {
          logger.trace("getting arg from param #{}: {}", i, ColferUtils.format(parameter));
        }
        Obj obj = parameter.getValue();
        if (obj.getIsNull()) {
          args.add(new MessageArgument(null, true));
        } else {
          Object lookedUpObj = null;
          final int objRef = obj.getRef();
          if (objRef != 0) {
            // First try to fetch object by reference (works only with locally-instantiated/stored
            // objects)
            lookedUpObj = objectLookupStore.lookupObject(ObjectRef.from(objRef));
          }
          if (lookedUpObj != null) {
            args.add(new MessageArgument(lookedUpObj, true));
          } else {
            // If not found by reference, unwrap value
            args.add(
                new MessageArgument(Unwrapper.unwrapObject(obj, parameterTypes.get(i)), false));
          }
        }
        i++;
      }
    }

    return args;
  }

  /**
   * Retrieves the value to assign for field operations from the execution message.
   *
   * <p>Dispatchers overriding this method should extract and return the value object for assignment
   * operations.
   *
   * @param execMessage the execution message containing the value
   * @param accessibleObject the field related accessible object
   * @return the value to be assigned, or null if not applicable
   */
  Object getValueFromMessage(ExecMessage execMessage, AccessibleObject accessibleObject) {
    return null;
  }

  /**
   * Retrieves the target object for instance method or field operations from the execution message.
   *
   * <p>Overriding dispatchers should extract and return the appropriate target object upon which
   * the execution should be performed.
   *
   * @param execMessage the execution message containing target information
   * @return the target object on which a method or field operation is to be invoked
   * @throws NullPointerException if the target object cannot be determined
   */
  Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    return null;
  }

  /**
   * Wraps a Throwable message to be sent back to the client, after an exception occurred during
   * loading or invoking an accessible object.
   *
   * @param messageId The id of the message that this is a response to
   * @param accessibleObject The accessible object that failed to be loaded or invoked
   * @param exceptionWhileLoading Either this or exceptionWhileInvoking must be non-null
   * @param exceptionWhileInvoking Either this or exceptionWhileLoading must be non-null
   * @return The wrapped ExecMessage with the Throwable
   */
  final ExecMessage wrapAfterExecThrowableMessage(
      String messageId,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    Throwable throwable =
        exceptionWhileLoading != null ? exceptionWhileLoading : exceptionWhileInvoking;
    return messageBuilder.buildAccessibleObjectThrowable(
        peerUuid, accessibleObject, throwable, messageId);
  }

  /**
   * Stores the given object reference in the session associated with the specified peer UUID.
   *
   * <p>This method sends a session command message to the session service to persist the object
   * reference.
   *
   * @param peerUuid the UUID identifying the peer whose session is to be updated
   * @param objectRef the object reference to store in the session
   */
  private void storeObjectInSession(@Nonnull UUID peerUuid, @Nonnull ObjectRef objectRef) {
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, peerUuid, objectRef);
    messageGateway.sendMessageToSessionService(sessionCommandMsg);
  }

  /**
   * Creates an execution message to be sent before the actual invocation.
   *
   * <p>Subclasses should construct an appropriate ExecMessage that encapsulates context and
   * invocation details, to be sent during the pre-execution phase.
   *
   * @param ctxt the execution context holding metadata for the invocation
   * @param sender the originator of the call
   * @param target the target on which the accessible object will be invoked
   * @param args the arguments to be used in the invocation
   * @param includeDeclaredExceptions if {@code true}, extract and include declared exceptions from
   *     method signature; if {@code false}, declaredExceptions will be {@code null}
   * @return the constructed before-execution ExecMessage
   */
  protected abstract ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args, boolean includeDeclaredExceptions);

  /**
   * Creates an execution message to be sent after the invocation has been performed.
   *
   * <p>This message encapsulates the result of the invocation, including any object reference if
   * applicable, and indicates whether the invocation returned void.
   *
   * @param ctxt the execution context for the call
   * @param value the return value of the invocation (or exception if occurred)
   * @param objectRef the reference to the stored result object, if applicable
   * @param isVoid flag indicating whether the execution result is void
   * @return the constructed after-execution ExecMessage
   */
  protected abstract ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid);

  /**
   * Creates an execution response message after processing an incoming execution call.
   *
   * <p>This method packages the outcome of the invocation, including any return value, object
   * reference, or exception encountered during loading or invocation phases.
   *
   * @param execMessage the original execution message received
   * @param valueObject the result of the invocation if successful
   * @param valueObjRef the object reference corresponding to the result, if available
   * @param accessibleObject the accessible object that was subject to invocation
   * @param exceptionWhileLoading the exception encountered during the loading phase, if any
   * @param exceptionWhileInvoking the exception encountered during the invocation phase, if any
   * @return the constructed ExecMessage representing the after-execution response
   */
  protected abstract ExecMessage createAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking);

  /**
   * Invokes the target accessible object (e.g., constructor, method, or field) using the provided
   * parameters.
   *
   * <p>This method may be overridden by specialized dispatchers based on the type of the operation.
   *
   * <p>If the args parameter differs from the original join point arguments (e.g., due to BEFORE
   * intercept callback mutations), this method uses {@link ProceedingJoinPoint#proceed(Object[])}
   * to pass the modified arguments to the intercepted operation.
   *
   * @param pjp the proceeding join point
   * @param args the arguments for the invocation (which may be mutated by intercept callbacks)
   * @return the result of the accessible object invocation, or null for void operations
   */
  protected Object invoke(ProceedingJoinPoint pjp, Object[] args) throws Throwable {
    // Use pjp.proceed(args) to pass the arguments (which may have been mutated by intercept
    // callbacks) to the intercepted operation. AspectJ's proceed(Object[]) allows us to replace
    // the original arguments with modified ones.
    return pjp.proceed(args);
  }

  /**
   * Invokes the loaded accessible object (constructor, method, or field) using the unwrapped
   * arguments.
   *
   * <p>Subclasses must implement the logic to perform the invocation, handling any value assignment
   * for field operations.
   *
   * @param accessibleObject the accessible object to invoke
   * @param target the target object for instance operations; may be null for static members
   * @param args the list of arguments encapsulated in MessageArgument instances
   * @param value the value to assign for field operations, if applicable
   * @return the result of the invocation, which may include a wrapped exception
   * @throws ReflectiveOperationException if a reflection error occurs during invocation
   */
  protected abstract Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws ReflectiveOperationException;

  /**
   * Determines whether invoking the specified accessible object results in a void return type.
   *
   * @param accessibleObject the method, constructor, or field to evaluate
   * @return true if the invocation yields no meaningful return value; false otherwise
   */
  protected abstract boolean returnsVoid(AccessibleObject accessibleObject);

  /**
   * Determines whether the operation represented by the proceeding join point returns void.
   *
   * <p>This method is used during dispatch to determine whether the operation produces a return
   * value. For method calls, the return type is extracted from the method signature. For field
   * operations and constructors, subclasses return a fixed value (set fields return void,
   * constructors and get fields do not).
   *
   * @param pjp the proceeding join point representing the intercepted operation
   * @return true if the operation returns void; false otherwise
   */
  protected abstract boolean returnsVoid(ProceedingJoinPoint pjp);

  /**
   * Retrieves the MessageType corresponding to the before-execution phase.
   *
   * <p>This type is used to identify the nature of the operation (constructor, instance method, or
   * class method) during pre-execution processing.
   *
   * @return the MessageType for the before-execution message
   */
  protected abstract MessageType getBeforeExecMessageType();

  /**
   * Extracts the list of parameters from the given execution message.
   *
   * <p>This method should return the parameters that will be used to determine argument types and
   * values during the loading phase.
   *
   * @param execMessage the execution message containing the parameters
   * @return a list of Parameter objects extracted from the execution message
   */
  protected abstract List<Parameter> getParameterList(ExecMessage execMessage);

  /**
   * Loads the accessible object (Constructor, Method, Field) from the message.
   *
   * @param execMessage The message to extract the accessible object from
   * @param parameterTypes Used only by constructor and method dispatchers
   * @param args Arguments. Only by constructor and method dispatchers
   * @return The loaded accessible object
   * @throws ReflectiveOperationException if the accessible object cannot be loaded
   * @throws AmbiguousCallException if the call is ambiguous
   */
  protected abstract AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException, AmbiguousCallException;

  /**
   * Checks if the given message type represents a field PUT operation.
   *
   * <p>Field PUT operations store the value to set in args[0], so when intercept callbacks mutate
   * args, we need to pass the mutated value to invokeIncoming.
   *
   * @param messageType the message type to check
   * @return true if this is a field PUT operation (instance or static)
   */
  private boolean isFieldPutOperation(MessageType messageType) {
    return messageType == MessageType.EXEC_PUT_FIELD || messageType == MessageType.EXEC_PUT_STATIC;
  }

  /**
   * Applies argument mutations from intercept callbacks to a list of message arguments.
   *
   * <p>This method creates a new list with the mutated values applied. The {@code byReference} flag
   * for each argument is always taken from {@code originalArgs}, not {@code currentArgs}. This is
   * important because:
   *
   * <ul>
   *   <li>The {@code byReference} flag is part of the method's contract (how the parameter was
   *       declared)
   *   <li>When multiple callbacks mutate args sequentially, each should preserve the original
   *       by-reference semantics
   *   <li>A callback mutating a value should not change whether that parameter is passed by
   *       reference
   * </ul>
   *
   * <p><b>Usage patterns:</b>
   *
   * <ul>
   *   <li>First mutation: {@code applyArgMutations(args, args, mutations)} - both parameters are
   *       the original args
   *   <li>Subsequent mutations: {@code applyArgMutations(finalArgs, args, mutations)} - currentArgs
   *       may already be mutated, but byReference comes from original
   * </ul>
   *
   * @param currentArgs the current argument list (may already have mutations applied)
   * @param originalArgs the original argument list (used for {@code byReference} flag lookup)
   * @param mutations map of argument index to new value
   * @return a new list with mutations applied, preserving original byReference flags
   */
  private List<MessageArgument> applyArgMutations(
      List<MessageArgument> currentArgs,
      List<MessageArgument> originalArgs,
      Map<Integer, Object> mutations) {
    List<MessageArgument> result = new ArrayList<>(currentArgs);
    for (Map.Entry<Integer, Object> entry : mutations.entrySet()) {
      int index = entry.getKey();
      Object newValue = entry.getValue();
      if (index >= 0 && index < result.size()) {
        result.set(index, new MessageArgument(newValue, originalArgs.get(index).byReference()));
      }
    }
    return result;
  }

  // ---- Helper methods for local intercepts ----

  /**
   * Extracts the class name from a ProceedingJoinPoint.
   *
   * @param pjp the proceeding join point
   * @return the declaring class name
   */
  private String getClassNameFromPjp(ProceedingJoinPoint pjp) {
    return pjp.getSignature().getDeclaringTypeName();
  }

  /**
   * Extracts the method/field name from a ProceedingJoinPoint.
   *
   * <p>Returns "new" for constructors (matching the convention used in message construction and
   * intercept registration), or the signature name for methods and fields.
   *
   * @param pjp the proceeding join point
   * @return the method, constructor ("new"), or field name
   */
  private String getMethodNameFromPjp(ProceedingJoinPoint pjp) {
    Signature sig = pjp.getSignature();
    if (sig instanceof ConstructorSignature) {
      return "new";
    }
    return sig.getName();
  }

  /**
   * Extracts argument values for intercept callbacks from an incoming message.
   *
   * <p>This helper consolidates the common pattern of extracting argument values from various
   * sources (field PUT operation value, method arguments, or empty array).
   *
   * @param messageType the type of incoming message
   * @param value the field value for PUT operations (may be null)
   * @param finalArgs the final method arguments (may be null)
   * @param args the original method arguments (may be null)
   * @return array of argument values for callbacks
   */
  @Nonnull
  private Object[] extractArgValuesFromIncoming(
      MessageType messageType,
      Object value,
      List<MessageArgument> finalArgs,
      List<MessageArgument> args) {
    if (isFieldPutOperation(messageType) && value != null) {
      return new Object[] {value};
    } else if (finalArgs != null) {
      return finalArgs.stream().map(MessageArgument::object).toArray(Object[]::new);
    } else if (args != null) {
      return args.stream().map(MessageArgument::object).toArray(Object[]::new);
    } else {
      return new Object[0];
    }
  }

  /**
   * Metadata extracted from an incoming call for intercept callbacks.
   *
   * @param className the class name
   * @param methodName the method/field name
   * @param paramTypes the parameter types
   */
  private record IncomingInterceptMetadata(
      String className, String methodName, List<String> paramTypes) {}

  /**
   * Determines whether an incoming message should be written to WAL/PUB.
   *
   * <p>The decision uses a three-channel matrix:
   *
   * <ul>
   *   <li>{@code CLI_RPC} &mdash; requires {@code WITH_WAL_INCOMING_CLI}
   *   <li>{@code LOG_RPC} &mdash; requires {@code WITH_WAL_INCOMING_RPC} + {@code
   *       WITH_WAL_ALL_INCOMING_RPC} + circularity guard (source and WAL must differ)
   *   <li>Others ({@code ZMQ_SOCKET_RPC}, {@code WEBSOCKET_RPC}) &mdash; requires {@code
   *       WITH_WAL_INCOMING_RPC}
   * </ul>
   *
   * <p>All channels require at least {@code WITH_WAL} or {@code WITH_TCP_PUB} to be enabled.
   *
   * @param messageChannel the transport channel through which the message was received
   * @return true if the incoming message should be written to WAL/PUB
   */
  private boolean shouldWriteIncomingToWal(MessageChannelType messageChannel) {
    boolean withPubOrWal =
        runOptions.contains(RunOptions.WITH_WAL) || runOptions.contains(RunOptions.WITH_TCP_PUB);
    if (!withPubOrWal) {
      return false;
    }
    if (messageChannel == MessageChannelType.CLI_RPC) {
      return runOptions.contains(RunOptions.WITH_WAL_INCOMING_CLI);
    }
    if (!runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC)) {
      return false;
    }
    if (messageChannel == MessageChannelType.LOG_RPC) {
      if (!runOptions.contains(RunOptions.WITH_WAL_ALL_INCOMING_RPC)) {
        return false;
      }
      if (sourceAndWalAreSameLog) {
        logger.warn(
            "WITH_WAL_ALL_INCOMING_RPC is enabled but source and WAL are the same log;"
                + " ignoring to prevent circular writes");
        return false;
      }
    }
    return true;
  }

  /**
   * Extracts intercept metadata from an incoming message.
   *
   * @param incomingCall the incoming execution message
   * @return metadata for intercept callbacks
   */
  @Nonnull
  private IncomingInterceptMetadata extractInterceptMetadata(ExecMessage incomingCall) {
    String className = getClassname(incomingCall);
    String methodName = getExecutableName(incomingCall);
    List<String> paramTypesList = getParameterTypes(incomingCall);
    if (paramTypesList == null) {
      paramTypesList = List.of();
    }
    return new IncomingInterceptMetadata(className, methodName, paramTypesList);
  }
}
