/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getExecutableName;
import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;

import com.quasient.pal.common.lang.intercept.AfterPhaseData;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.ContextFactory;
import com.quasient.pal.common.runtime.Dispatcher;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.common.util.Classes;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher.AroundCallbackState;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher.AroundConsolidatedResponse;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
import com.quasient.pal.core.intercept.InterceptCheckResult;
import com.quasient.pal.core.intercept.InterceptChecker;
import com.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundCallbackState;
import com.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundConsolidatedResponse;
import com.quasient.pal.core.internal.messages.SessionCommandMsg;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.messages.types.SessionCommandType;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.colfer.ColferUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;

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

    final Object sender = pjp.getThis();
    final Object[] args = pjp.getArgs();
    final Object target = pjp.getTarget();

    if (logger.isTraceEnabled()) {
      logger.trace("JoinPoint: {}", pjp.toLongString());
      logger.trace("dispatch:in w/sender: {}, target: {}, args: {}", sender, target, args);
    }

    // Check intercepts BEFORE creating Context/ExecMessage
    final MessageType beforeExecMsgType = getBeforeExecMessageType();
    final boolean isMessageInterceptable = InterceptChecker.isInterceptableType(beforeExecMsgType);

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

      // 1. Wrap message
      beforeExecMsg = createBeforeExecMessage(ctx, sender, target, args);

      // 2. Send message (WAL/PUB only - intercepts handled separately)
      @SuppressWarnings("unused")
      final ExecMessage beforeExecResponseMsg =
          messageGateway.sendExecMessage(messageBuilder.wrap(beforeExecMsg), ExecPhase.BEFORE);
    }

    // Handle LOCAL BEFORE intercepts (no ExecMessage needed for hot-path optimization)
    Object[] finalArgs = args;
    if (beforeInterceptCheck != null && beforeInterceptCheck.hasLocalIntercepts()) {
      List<InterceptMessage> localBeforeIntercepts =
          filterBeforeIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localBeforeIntercepts.isEmpty()) {
        String className = getClassNameFromPjp(pjp);
        String methodName = getMethodNameFromPjp(pjp);
        List<String> paramTypes = getParamTypesFromPjp(pjp);

        ConsolidatedCallbackResponse localResponse =
            localInterceptCallbackDispatcher.sendLocalBeforeCallbacks(
                localBeforeIntercepts,
                finalArgs,
                className,
                methodName,
                paramTypes,
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
      List<InterceptMessage> localBeforeAsyncIntercepts =
          filterBeforeAsyncIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localBeforeAsyncIntercepts.isEmpty()) {
        String className = getClassNameFromPjp(pjp);
        String methodName = getMethodNameFromPjp(pjp);
        List<String> paramTypes = getParamTypesFromPjp(pjp);

        localInterceptCallbackDispatcher.sendLocalBeforeAsyncCallbacks(
            localBeforeAsyncIntercepts,
            finalArgs,
            className,
            methodName,
            paramTypes,
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

    // Handle LOCAL AROUND intercepts (direct method invocation, no serialization)
    List<LocalAroundCallbackState> localAroundPendingCallbacks = null;
    boolean skipInvoke = false;
    Object skipReturnValue = null;
    InvocationThrowableWrapper throwableWrapper = null;
    Object returnValue = null;

    if (beforeInterceptCheck != null && beforeInterceptCheck.hasLocalIntercepts()) {
      List<InterceptMessage> localAroundIntercepts =
          filterAroundIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localAroundIntercepts.isEmpty()) {
        String className = getClassNameFromPjp(pjp);
        String methodName = getMethodNameFromPjp(pjp);
        List<String> paramTypes = getParamTypesFromPjp(pjp);

        // Create accessor that invokes the method directly
        final Object[] argsForAccessor = finalArgs;
        LocalAroundAccessor localAccessor =
            (argsToInvoke) -> {
              try {
                Object result = invoke(pjp, argsToInvoke != null ? argsToInvoke : argsForAccessor);
                return new AfterPhaseData(result, null, returnsVoid(pjp));
              } catch (Throwable th) {
                return new AfterPhaseData(null, th, returnsVoid(pjp));
              }
            };

        LocalAroundConsolidatedResponse localAroundResponse =
            localInterceptCallbackDispatcher.sendLocalAroundCallbacks(
                localAroundIntercepts,
                finalArgs,
                className,
                methodName,
                paramTypes,
                peerUuid.toString(),
                localAccessor);

        // Check if callback wants to throw an exception
        if (localAroundResponse.shouldThrowException()) {
          throw localAroundResponse.getExceptionToThrow();
        }

        // Check if callback says to skip execution (e.g., cache hit)
        if (!localAroundResponse.shouldProceed()) {
          // Skip method invocation - use cached/computed value from callback
          skipInvoke = true;
          skipReturnValue = localAroundResponse.getSkipReturnValue();
        } else {
          // Apply argument mutations from local AROUND
          if (!localAroundResponse.getMutatedArgs().isEmpty()) {
            Object[] mutatedArgs = finalArgs == args ? args.clone() : finalArgs;
            for (Map.Entry<Integer, Object> entry :
                localAroundResponse.getMutatedArgs().entrySet()) {
              int index = entry.getKey();
              Object newValue = entry.getValue();
              if (index >= 0 && index < mutatedArgs.length) {
                mutatedArgs[index] = newValue;
              }
            }
            finalArgs = mutatedArgs;
          }

          // Track pending local AROUND callbacks for AFTER phase
          localAroundPendingCallbacks = localAroundResponse.getPendingCallbacks();

          // If any local AROUND callback called proceed(), the method was already invoked
          // Extract the return value from the last pending callback's context
          if (localAroundPendingCallbacks != null && !localAroundPendingCallbacks.isEmpty()) {
            LocalAroundCallbackState lastCallback =
                localAroundPendingCallbacks.get(localAroundPendingCallbacks.size() - 1);
            returnValue = lastCallback.context().getReturnValueInternal();
            Throwable thrown = lastCallback.context().getThrownException();
            if (thrown != null) {
              throwableWrapper = new InvocationThrowableWrapper(thrown);
            }
            // Skip remote AROUND and direct invocation since local AROUND already invoked
            skipInvoke = true;
          }
        }
      }
    }

    // Send REMOTE AROUND BEFORE callbacks and handle skip semantics
    // (only if local AROUND didn't already invoke the method)
    List<AroundCallbackState> aroundPendingCallbacks = null;
    if (!skipInvoke
        && beforeInterceptCheck != null
        && beforeInterceptCheck.hasRemoteIntercepts()
        && beforeExecMsg != null) {
      AroundConsolidatedResponse aroundResponse =
          interceptCallbackDispatcher.sendAroundCallbacks(
              beforeInterceptCheck, beforeExecMsg, finalArgs);

      // Check if callback wants to throw an exception
      if (aroundResponse.shouldThrowException()) {
        throw aroundResponse.getExceptionToThrow();
      }

      // Check if callback says to skip execution (e.g., cache hit)
      if (!aroundResponse.shouldProceed()) {
        // Skip method invocation but continue with post-execution steps (WAL, AFTER intercepts)
        // Note: AROUND AFTER callbacks won't fire since proceed() wasn't called
        skipInvoke = true;
        skipReturnValue = aroundResponse.getSkipReturnValue();
      } else {
        // Apply additional argument mutations from AROUND callbacks
        if (aroundResponse.hasArgMutations()) {
          Object[] mutatedArgs = finalArgs == args ? args.clone() : finalArgs;
          for (Map.Entry<Integer, Object> entry : aroundResponse.getMutatedArgs().entrySet()) {
            int index = entry.getKey();
            Object newValue = entry.getValue();
            if (index >= 0 && index < mutatedArgs.length) {
              mutatedArgs[index] = newValue;
            }
          }
          finalArgs = mutatedArgs;
        }

        // Track pending AROUND callbacks for AFTER phase
        aroundPendingCallbacks = aroundResponse.getPendingCallbacks();
      }
    }

    // 4. Invoke (skip if AROUND callback said to skip or local AROUND already invoked)
    if (skipInvoke) {
      // Use the skip return value from AROUND callback (e.g., cached value)
      // If local AROUND invoked, returnValue is already set; otherwise use skipReturnValue
      if (returnValue == null && localAroundPendingCallbacks == null) {
        returnValue = skipReturnValue;
      }
    } else {
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
      List<InterceptMessage> localAfterIntercepts =
          filterAfterIntercepts(afterInterceptCheck.getLocalIntercepts());
      if (!localAfterIntercepts.isEmpty()) {
        String className = getClassNameFromPjp(pjp);
        String methodName = getMethodNameFromPjp(pjp);
        List<String> paramTypes = getParamTypesFromPjp(pjp);
        boolean returnsVoidLocal = returnsVoid(pjp);

        ConsolidatedCallbackResponse localAfterResponse =
            localInterceptCallbackDispatcher.sendLocalAfterCallbacks(
                localAfterIntercepts,
                finalArgs,
                returnValue,
                returnsVoidLocal,
                throwableWrapper != null ? throwableWrapper.throwable() : null,
                className,
                methodName,
                paramTypes,
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
      List<InterceptMessage> localAfterAsyncIntercepts =
          filterAfterAsyncIntercepts(afterInterceptCheck.getLocalIntercepts());
      if (!localAfterAsyncIntercepts.isEmpty()) {
        String className = getClassNameFromPjp(pjp);
        String methodName = getMethodNameFromPjp(pjp);
        List<String> paramTypes = getParamTypesFromPjp(pjp);
        boolean returnsVoidLocal = returnsVoid(pjp);

        localInterceptCallbackDispatcher.sendLocalAfterAsyncCallbacks(
            localAfterAsyncIntercepts,
            finalArgs,
            returnValue,
            returnsVoidLocal,
            throwableWrapper != null ? throwableWrapper.throwable() : null,
            className,
            methodName,
            paramTypes,
            peerUuid.toString());
      }
    }

    // Handle LOCAL AROUND AFTER phase (for callbacks that called proceed())
    if (localAroundPendingCallbacks != null && !localAroundPendingCallbacks.isEmpty()) {
      ConsolidatedCallbackResponse localAroundAfterResponse =
          localInterceptCallbackDispatcher.sendLocalAroundAfterCallbacks(
              localAroundPendingCallbacks,
              returnValue,
              returnsVoid(pjp),
              throwableWrapper != null ? throwableWrapper.throwable() : null);

      // Check if callback wants to throw an exception
      if (localAroundAfterResponse.shouldThrowException()) {
        throwableWrapper =
            new InvocationThrowableWrapper(localAroundAfterResponse.getExceptionToThrow());
      }

      // Apply return value override
      if (localAroundAfterResponse.hasReturnValueOverride()) {
        returnValue = localAroundAfterResponse.getOverriddenReturnValue();
      }
    }

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
          createAfterExecMessage(ctx, returnValue, objectRef, returnsVoid);

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

      // 8.5. Send AROUND AFTER callbacks (for callbacks that called proceed())
      if (aroundPendingCallbacks != null && !aroundPendingCallbacks.isEmpty()) {
        InterceptCallbackDispatcher.ConsolidatedCallbackResponse aroundAfterResponse =
            interceptCallbackDispatcher.sendAroundAfterCallbacks(
                aroundPendingCallbacks,
                afterExecMsg,
                returnValue,
                returnsVoid,
                throwableWrapper != null ? throwableWrapper.throwable() : null);

        // Check if callback wants to throw an exception
        if (aroundAfterResponse.shouldThrowException()) {
          throwableWrapper =
              new InvocationThrowableWrapper(aroundAfterResponse.getExceptionToThrow());
        }

        // Apply return value override
        if (aroundAfterResponse.hasReturnValueOverride()) {
          returnValue = aroundAfterResponse.getOverriddenReturnValue();
        }
      }
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
  }

  /**
   * Dispatches an incoming (via RPC or Log) execution message.
   *
   * <p>This method validates the message type, writes ahead if not coming from Log, and performs
   * the loading and invocation phases including argument extraction, accessible object loading, and
   * target retrieval. It also handles intercept callbacks (BEFORE, AROUND, AFTER) when intercepts
   * are enabled. Finally, it sends an after-execution message to complete the processing.
   *
   * @param incomingCall the execution message to process
   * @param messageChannel the transport channel through which the message was received
   * @return the execution message received in response after processing the incoming call
   * @throws IllegalArgumentException if the message type is not supported by this dispatcher
   */
  @Override
  public ExecMessage dispatchIncoming(ExecMessage incomingCall, MessageChannelType messageChannel) {
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
    List<AroundCallbackState> aroundPendingCallbacks = null;
    List<LocalAroundCallbackState> localAroundPendingCallbacks = null;
    boolean skipInvoke = false;
    Object skipReturnValue = null;
    Object localAroundReturnValue = null;
    Throwable localAroundThrownException = null;

    // Handle LOCAL BEFORE intercepts first (before remote)
    if (exceptionWhileLoading == null
        && beforeInterceptCheck != null
        && beforeInterceptCheck.hasLocalIntercepts()) {
      List<InterceptMessage> localBeforeIntercepts =
          filterBeforeIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localBeforeIntercepts.isEmpty()) {
        String className = getClassname(incomingCall);
        String methodName = getExecutableName(incomingCall);
        List<String> paramTypesList = getParameterTypes(incomingCall);
        if (paramTypesList == null) {
          paramTypesList = List.of();
        }

        // Extract current arg values for callbacks
        Object[] argValues;
        if (isFieldPutOperation(messageType) && value != null) {
          argValues = new Object[] {value};
        } else if (args != null) {
          argValues = args.stream().map(MessageArgument::object).toArray(Object[]::new);
        } else {
          argValues = new Object[0];
        }

        ConsolidatedCallbackResponse localBeforeResponse =
            localInterceptCallbackDispatcher.sendLocalBeforeCallbacks(
                localBeforeIntercepts,
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
          } else if (args != null) {
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
      List<InterceptMessage> localBeforeAsyncIntercepts =
          filterBeforeAsyncIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localBeforeAsyncIntercepts.isEmpty()) {
        IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
        Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, args);

        localInterceptCallbackDispatcher.sendLocalBeforeAsyncCallbacks(
            localBeforeAsyncIntercepts,
            argValues,
            meta.className(),
            meta.methodName(),
            meta.paramTypes(),
            peerUuid.toString());
      }
    }

    // Handle LOCAL AROUND intercepts (direct method invocation, no serialization)
    if (exceptionWhileLoading == null
        && exceptionWhileInvoking == null
        && beforeInterceptCheck != null
        && beforeInterceptCheck.hasLocalIntercepts()) {
      List<InterceptMessage> localAroundIntercepts =
          filterAroundIntercepts(beforeInterceptCheck.getLocalIntercepts());
      if (!localAroundIntercepts.isEmpty()) {
        String className = getClassname(incomingCall);
        String methodName = getExecutableName(incomingCall);
        List<String> paramTypesList = getParameterTypes(incomingCall);
        if (paramTypesList == null) {
          paramTypesList = List.of();
        }

        // Extract current arg values for accessor
        Object[] argValues;
        if (isFieldPutOperation(messageType) && value != null) {
          argValues = new Object[] {value};
        } else if (finalArgs != null) {
          argValues = finalArgs.stream().map(MessageArgument::object).toArray(Object[]::new);
        } else {
          argValues = new Object[0];
        }

        // Create accessor that invokes the method directly
        // Capture accessibleObject, target, value, and messageType for the lambda
        final AccessibleObject accessibleForLambda = accessibleObject;
        final Object targetForLambda = target;
        final Object valueForLambda = value;
        final List<MessageArgument> argsForLambda = finalArgs;
        final boolean isFieldPut = isFieldPutOperation(messageType);
        LocalAroundAccessor localAccessor =
            (argsToInvoke) -> {
              try {
                // Convert args back to MessageArgument list if needed
                List<MessageArgument> invokeArgs = argsForLambda;
                if (argsToInvoke != null && argsForLambda != null) {
                  invokeArgs = new ArrayList<>(argsForLambda.size());
                  for (int i = 0; i < argsForLambda.size() && i < argsToInvoke.length; i++) {
                    invokeArgs.add(
                        new MessageArgument(argsToInvoke[i], argsForLambda.get(i).byReference()));
                  }
                }
                // For field PUT operations, use the mutated value from argsToInvoke[0]
                Object invokeValue = valueForLambda;
                if (isFieldPut && argsToInvoke != null && argsToInvoke.length > 0) {
                  invokeValue = argsToInvoke[0];
                }
                Object result =
                    invokeIncoming(accessibleForLambda, targetForLambda, invokeArgs, invokeValue);
                boolean isVoid = accessibleForLambda != null && returnsVoid(accessibleForLambda);
                return new AfterPhaseData(result, null, isVoid);
              } catch (Throwable th) {
                Throwable cause = th;
                if (th instanceof InvocationTargetException) {
                  cause = th.getCause();
                }
                boolean isVoid = accessibleForLambda != null && returnsVoid(accessibleForLambda);
                return new AfterPhaseData(null, cause, isVoid);
              }
            };

        LocalAroundConsolidatedResponse localAroundResponse =
            localInterceptCallbackDispatcher.sendLocalAroundCallbacks(
                localAroundIntercepts,
                argValues,
                className,
                methodName,
                paramTypesList,
                peerUuid.toString(),
                localAccessor);

        // Check if callback wants to throw an exception
        if (localAroundResponse.shouldThrowException()) {
          exceptionWhileInvoking = localAroundResponse.getExceptionToThrow();
        }

        // Check if callback says to skip execution
        if (exceptionWhileInvoking == null && !localAroundResponse.shouldProceed()) {
          skipInvoke = true;
          skipReturnValue = localAroundResponse.getSkipReturnValue();
        } else if (exceptionWhileInvoking == null) {
          // Apply argument mutations from local AROUND
          if (!localAroundResponse.getMutatedArgs().isEmpty()) {
            if (isFieldPutOperation(messageType)) {
              Object mutatedValue = localAroundResponse.getMutatedArgs().get(0);
              if (mutatedValue != null) {
                value = mutatedValue;
              }
            } else if (finalArgs != null) {
              List<MessageArgument> mutatedArgs = new ArrayList<>(finalArgs);
              for (Map.Entry<Integer, Object> entry :
                  localAroundResponse.getMutatedArgs().entrySet()) {
                int index = entry.getKey();
                Object newValue = entry.getValue();
                if (index >= 0 && index < mutatedArgs.size()) {
                  mutatedArgs.set(
                      index, new MessageArgument(newValue, finalArgs.get(index).byReference()));
                }
              }
              finalArgs = mutatedArgs;
            }
          }

          // Track pending local AROUND callbacks for AFTER phase
          localAroundPendingCallbacks = localAroundResponse.getPendingCallbacks();

          // If any local AROUND callback called proceed(), the method was already invoked
          if (localAroundPendingCallbacks != null && !localAroundPendingCallbacks.isEmpty()) {
            LocalAroundCallbackState lastCallback =
                localAroundPendingCallbacks.get(localAroundPendingCallbacks.size() - 1);
            localAroundReturnValue = lastCallback.context().getReturnValueInternal();
            localAroundThrownException = lastCallback.context().getThrownException();
            // Skip remote AROUND and direct invocation since local AROUND already invoked
            skipInvoke = true;
          }
        }
      }
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
            argValues = new Object[] {value};
          }
        } else {
          List<MessageArgument> mutatedArgs = new ArrayList<>(args);
          for (Map.Entry<Integer, Object> entry :
              beforeCallbackResponse.getMutatedArgs().entrySet()) {
            int index = entry.getKey();
            Object newValue = entry.getValue();
            if (index >= 0 && index < mutatedArgs.size()) {
              // Preserve the byReference flag from original arg
              mutatedArgs.set(index, new MessageArgument(newValue, args.get(index).byReference()));
            }
          }
          finalArgs = mutatedArgs;
          argValues =
              finalArgs.stream().map(MessageArgument::object).toArray(Object[]::new); // update
        }
      }

      // Send AROUND BEFORE callbacks and handle skip semantics
      if (exceptionWhileInvoking == null) {
        AroundConsolidatedResponse aroundResponse =
            interceptCallbackDispatcher.sendAroundCallbacks(
                beforeInterceptCheck, incomingCall, argValues);

        // Check if callback wants to throw an exception
        if (aroundResponse.shouldThrowException()) {
          exceptionWhileInvoking = aroundResponse.getExceptionToThrow();
        }

        // Check if callback says to skip execution (e.g., cache hit)
        if (exceptionWhileInvoking == null && !aroundResponse.shouldProceed()) {
          skipInvoke = true;
          skipReturnValue = aroundResponse.getSkipReturnValue();
        } else if (exceptionWhileInvoking == null) {
          // Apply additional argument mutations from AROUND callbacks
          if (aroundResponse.hasArgMutations()) {
            // For field PUT operations, args is null; mutation applies to 'value' (index 0)
            if (isFieldPutOperation(messageType)) {
              Object mutatedValue = aroundResponse.getMutatedArgs().get(0);
              if (mutatedValue != null) {
                value = mutatedValue;
              }
            } else {
              List<MessageArgument> mutatedArgs =
                  finalArgs == args ? new ArrayList<>(args) : finalArgs;
              for (Map.Entry<Integer, Object> entry : aroundResponse.getMutatedArgs().entrySet()) {
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
          // Track pending AROUND callbacks for AFTER phase
          aroundPendingCallbacks = aroundResponse.getPendingCallbacks();
        }
      }
    }

    // Invocation phase
    Object returnValue = null;
    ObjectRef objectRef = null;
    if (exceptionWhileLoading == null && exceptionWhileInvoking == null) {
      if (skipInvoke) {
        // Use the skip return value from AROUND callback (e.g., cached value)
        // If local AROUND invoked, use its return value; otherwise use skipReturnValue
        if (localAroundPendingCallbacks != null && !localAroundPendingCallbacks.isEmpty()) {
          returnValue = localAroundReturnValue;
          if (localAroundThrownException != null) {
            exceptionWhileInvoking = localAroundThrownException;
          }
        } else {
          returnValue = skipReturnValue;
        }
      } else {
        try {
          // 7. Invoke constructor/method/field (using possibly mutated args and value)
          // Note: For field PUT operations, 'value' has already been updated from callback
          // mutations
          returnValue = invokeIncoming(accessibleObject, target, finalArgs, value);
          if (logger.isTraceEnabled()) {
            String returnedClass =
                returnValue == null ? "unavailable" : returnValue.getClass().toString();
            logger.trace("invokeIncoming returnValue: {} of class: {}", returnValue, returnedClass);
          }
        } catch (InvocationTargetException e) {
          logger.error("Error during invocation phase - invoke", e);
          exceptionWhileInvoking = e.getCause();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
          logger.error("Error during invocation phase - invoke", e);
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
      List<InterceptMessage> localAfterIntercepts =
          filterAfterIntercepts(afterInterceptCheck.getLocalIntercepts());
      if (!localAfterIntercepts.isEmpty()) {
        IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
        boolean returnsVoidLocal = accessibleObject != null && returnsVoid(accessibleObject);
        Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, null);

        ConsolidatedCallbackResponse localAfterResponse =
            localInterceptCallbackDispatcher.sendLocalAfterCallbacks(
                localAfterIntercepts,
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
      List<InterceptMessage> localAfterAsyncIntercepts =
          filterAfterAsyncIntercepts(afterInterceptCheck.getLocalIntercepts());
      if (!localAfterAsyncIntercepts.isEmpty()) {
        IncomingInterceptMetadata meta = extractInterceptMetadata(incomingCall);
        boolean returnsVoidLocal = accessibleObject != null && returnsVoid(accessibleObject);
        Object[] argValues = extractArgValuesFromIncoming(messageType, value, finalArgs, null);

        localInterceptCallbackDispatcher.sendLocalAfterAsyncCallbacks(
            localAfterAsyncIntercepts,
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

    // Handle LOCAL AROUND AFTER phase (for callbacks that called proceed())
    if (localAroundPendingCallbacks != null && !localAroundPendingCallbacks.isEmpty()) {
      boolean returnsVoidLocal = accessibleObject != null && returnsVoid(accessibleObject);
      ConsolidatedCallbackResponse localAroundAfterResponse =
          localInterceptCallbackDispatcher.sendLocalAroundAfterCallbacks(
              localAroundPendingCallbacks, returnValue, returnsVoidLocal, exceptionWhileInvoking);

      // Check if callback wants to throw an exception
      if (localAroundAfterResponse.shouldThrowException()) {
        exceptionWhileInvoking = localAroundAfterResponse.getExceptionToThrow();
      }

      // Apply return value override
      if (localAroundAfterResponse.hasReturnValueOverride()) {
        returnValue = localAroundAfterResponse.getOverriddenReturnValue();
      }
    }

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

    // Send AROUND AFTER callbacks (for callbacks that called proceed())
    if (aroundPendingCallbacks != null && !aroundPendingCallbacks.isEmpty()) {
      boolean returnsVoidMethod = accessibleObject != null && returnsVoid(accessibleObject);
      InterceptCallbackDispatcher.ConsolidatedCallbackResponse aroundAfterResponse =
          interceptCallbackDispatcher.sendAroundAfterCallbacks(
              aroundPendingCallbacks,
              afterExecMsg,
              returnValue,
              returnsVoidMethod,
              exceptionWhileInvoking);

      // Check if callback wants to throw an exception
      if (aroundAfterResponse.shouldThrowException()) {
        exceptionWhileInvoking = aroundAfterResponse.getExceptionToThrow();
      }

      // Apply return value override
      if (aroundAfterResponse.hasReturnValueOverride()) {
        returnValue = aroundAfterResponse.getOverriddenReturnValue();
      }
    }

    // Recreate afterExecMsg if returnValue was overridden by callbacks
    final ExecMessage finalAfterExecMsg;
    if (afterInterceptCheck != null
        || (aroundPendingCallbacks != null && !aroundPendingCallbacks.isEmpty())) {
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

    // 11. Send object or exception, and receive
    final ExecMessage afterExecResponseMsg =
        messageGateway.sendExecMessage(messageBuilder.wrap(finalAfterExecMsg), ExecPhase.AFTER);

    // 12. Return received message
    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatchIncoming:out returning message: {}", ColferUtils.format(afterExecResponseMsg));
    }
    return afterExecResponseMsg;
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
   * @return the constructed before-execution ExecMessage
   */
  protected abstract ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args);

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
   * @param pjp the proceeding join point
   * @return the method or field name
   */
  private String getMethodNameFromPjp(ProceedingJoinPoint pjp) {
    return pjp.getSignature().getName();
  }

  /**
   * Extracts parameter type names from a ProceedingJoinPoint.
   *
   * @param pjp the proceeding join point
   * @return list of parameter type names, or empty list for fields
   */
  private List<String> getParamTypesFromPjp(ProceedingJoinPoint pjp) {
    Signature sig = pjp.getSignature();
    if (sig instanceof CodeSignature codeSig) {
      Class<?>[] paramTypes = codeSig.getParameterTypes();
      if (paramTypes != null && paramTypes.length > 0) {
        return Arrays.stream(paramTypes).map(Class::getName).toList();
      }
    }
    return List.of();
  }

  /**
   * Filters intercept messages to return only BEFORE type intercepts.
   *
   * @param intercepts the list of intercepts to filter
   * @return list of BEFORE intercepts
   */
  private List<InterceptMessage> filterBeforeIntercepts(List<InterceptMessage> intercepts) {
    return intercepts.stream()
        .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.BEFORE)
        .toList();
  }

  /**
   * Filters intercept messages to return only AFTER type intercepts.
   *
   * @param intercepts the list of intercepts to filter
   * @return list of AFTER intercepts
   */
  private List<InterceptMessage> filterAfterIntercepts(List<InterceptMessage> intercepts) {
    return intercepts.stream()
        .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AFTER)
        .toList();
  }

  /**
   * Filters intercept messages to return only AROUND type intercepts.
   *
   * @param intercepts the list of intercepts to filter
   * @return list of AROUND intercepts
   */
  private List<InterceptMessage> filterAroundIntercepts(List<InterceptMessage> intercepts) {
    return intercepts.stream()
        .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
        .toList();
  }

  /**
   * Filters intercept messages to return only BEFORE_ASYNC type intercepts.
   *
   * @param intercepts the list of intercepts to filter
   * @return list of BEFORE_ASYNC intercepts
   */
  private List<InterceptMessage> filterBeforeAsyncIntercepts(List<InterceptMessage> intercepts) {
    return intercepts.stream()
        .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.BEFORE_ASYNC)
        .toList();
  }

  /**
   * Filters intercept messages to return only AFTER_ASYNC type intercepts.
   *
   * @param intercepts the list of intercepts to filter
   * @return list of AFTER_ASYNC intercepts
   */
  private List<InterceptMessage> filterAfterAsyncIntercepts(List<InterceptMessage> intercepts) {
    return intercepts.stream()
        .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AFTER_ASYNC)
        .toList();
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
