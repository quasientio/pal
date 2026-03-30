/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java;

import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getExecutableName;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.Span;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalEntryKind;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.runtime.ContextFactory;
import io.quasient.pal.common.runtime.Dispatcher;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.Classes;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.intercept.AroundInterceptChain;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
import io.quasient.pal.core.intercept.InterceptCheckResult;
import io.quasient.pal.core.intercept.InterceptChecker;
import io.quasient.pal.core.intercept.InterceptPartition;
import io.quasient.pal.core.intercept.ParamTypeExtractor;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.replay.OperationSignature;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayCursor;
import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.core.replay.SpanFieldMutationReplayer;
import io.quasient.pal.core.rpc.policy.MemberCategory;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.SessionCommandType;
import io.quasient.pal.serdes.Unwrapper;
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
   * Thread-local dispatch depth counter for detecting entry points.
   *
   * <p>When a dispatch starts at depth 0, it means the call originated from unweaved code (e.g.,
   * JavaFX event dispatcher, external framework callback). Such calls are "entry points" that
   * represent external stimuli entering the PAL-tracked execution graph.
   *
   * <p>During replay, entry points are injected by {@link
   * io.quasient.pal.core.replay.ReplayInputInjector} to re-trigger the same execution paths.
   */
  private static final ThreadLocal<Integer> TL_DISPATCH_DEPTH = ThreadLocal.withInitial(() -> 0);

  /** The name of the JavaFX Application Thread as created by the JavaFX runtime. */
  private static final String JAVAFX_APPLICATION_THREAD = "JavaFX Application Thread";

  /** Thread affinity key for routing to the JavaFX Application Thread during replay. */
  private static final String FX_THREAD_AFFINITY = "fx-thread";

  /**
   * Replayer for field mutations within stubbed spans. Used by the {@link
   * ReplayAction#STUB_WITH_SIDE_EFFECTS} dispatch path to apply PUT_FIELD and PUT_STATIC mutations
   * from the WAL without executing the original method.
   */
  private final SpanFieldMutationReplayer spanFieldMutationReplayer =
      new SpanFieldMutationReplayer();

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

    // Track dispatch depth to detect entry points (calls from unweaved code like JavaFX events).
    // When depth == 0, this call originated outside weaved code and is an entry point.
    final int depthAtEntry = TL_DISPATCH_DEPTH.get();
    final boolean isEntryPoint = (depthAtEntry == 0);
    TL_DISPATCH_DEPTH.set(depthAtEntry + 1);

    try {
      return dispatchInternal(pjp, isEntryPoint);
    } finally {
      TL_DISPATCH_DEPTH.set(depthAtEntry);
    }
  }

  /**
   * Internal dispatch implementation, extracted to support depth tracking try-finally wrapper.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param isEntryPoint true if this dispatch is an entry point (call from unweaved code)
   * @return the result of the executed operation
   * @throws Throwable if an error occurs during invocation
   */
  private Object dispatchInternal(ProceedingJoinPoint pjp, boolean isEntryPoint) throws Throwable {
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

      // Check intercepts BEFORE creating Context/ExecMessage
      final MessageType beforeExecMsgType = getBeforeExecMessageType();
      final boolean isMessageInterceptable =
          InterceptChecker.isInterceptableType(beforeExecMsgType);

      InterceptCheckResult beforeInterceptCheck = null;
      if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
        beforeInterceptCheck =
            interceptChecker.checkIntercepts(pjp, beforeExecMsgType, ExecPhase.BEFORE);
      }

      // Recording scope: determine if this operation should be recorded to WAL/PUB.
      // Permit-all scopes skip signature extraction entirely. Otherwise the scope check is
      // cached per className.memberName#category, so steady-state cost is a single hash lookup.
      final boolean inRecordingScope;
      if (recordingScope.isPermitAll()) {
        inRecordingScope = true;
      } else {
        final Signature sig = pjp.getStaticPart().getSignature();
        final String scopeClassName = sig.getDeclaringTypeName();
        final String scopeMethodName =
            (sig instanceof ConstructorSignature) ? "new" : sig.getName();
        final MemberCategory memberCategory = MemberCategory.fromMessageType(beforeExecMsgType);
        inRecordingScope =
            recordingScope.isInScope(scopeClassName, scopeMethodName, memberCategory);
      }

      // Decide if we need to create messages
      boolean withPubOrWal =
          inRecordingScope
              && (runOptions.contains(RunOptions.WITH_WAL)
                  || runOptions.contains(RunOptions.WITH_TCP_PUB));
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

        // Mark as entry point if this call originated from unweaved code (e.g., JavaFX callback)
        if (isEntryPoint) {
          beforeExecMsg.setEntryPoint(true);
          // Set thread affinity for JavaFX thread so replay can route to the real FX thread
          if (JAVAFX_APPLICATION_THREAD.equals(Thread.currentThread().getName())) {
            beforeExecMsg.setThreadAffinity(FX_THREAD_AFFINITY);
          }
        }

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

        // Mark as entry point if this call originated from unweaved code
        if (isEntryPoint) {
          afterExecMsg.setEntryPoint(true);
          // Set thread affinity for JavaFX thread so replay can route to the real FX thread
          if (JAVAFX_APPLICATION_THREAD.equals(Thread.currentThread().getName())) {
            afterExecMsg.setThreadAffinity(FX_THREAD_AFFINITY);
          }
        }

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
        Throwable invocationThr = throwableWrapper.throwable();
        // we want to throw the cause exception
        if (invocationThr instanceof InvocationTargetException) {
          throw invocationThr.getCause();
        } else {
          throw invocationThr;
        }
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
    // Recording scope: out-of-scope operations have no WAL entries — invoke directly.
    // Since these operations were not recorded during the recording phase, they should not
    // consume WAL cursor entries or trigger false EXTRA_OPERATION divergences during replay.
    if (!recordingScope.isPermitAll()) {
      final Signature replaySig = pjp.getStaticPart().getSignature();
      final String replayClassName = replaySig.getDeclaringTypeName();
      final String replayMemberName =
          (replaySig instanceof ConstructorSignature) ? "new" : replaySig.getName();
      final MemberCategory replayCategory =
          MemberCategory.fromMessageType(getBeforeExecMessageType());
      if (!recordingScope.isInScope(replayClassName, replayMemberName, replayCategory)) {
        return invoke(pjp, pjp.getArgs());
      }
    }

    String threadName = Thread.currentThread().getName();
    ReplayCursor cursor = replayContext.getCursor(threadName);

    // Tracks whether an operation-only entry-point skip was used in this call. When true,
    // gate advancement before invoke is deferred to after invoke. This prevents the gate
    // from unblocking injection threads before the invoking thread has had a chance to
    // execute the operation (critical for blocking calls like Application.launch that spawn
    // threads whose entry points would otherwise race with the injector).
    boolean deferGateBeforeInvoke = false;

    // Step 1: Match against WAL
    WalEntry expectedEntry = cursor.peekNext();
    OperationSignature liveSig = OperationSignature.fromJoinPoint(pjp, getBeforeExecMessageType());

    // Skip COMPLETION entries for entry points that were skipped (called by unweaved code).
    // When an entry point's OPERATION is skipped, its COMPLETION is still in the WAL and
    // must also be skipped. Entry point completions are identifiable by the entryPoint flag.
    while (expectedEntry != null
        && expectedEntry.getKind() == WalEntryKind.COMPLETION
        && expectedEntry.isEntryPoint()) {
      long completionOffset = expectedEntry.getOffset();
      cursor.advance();
      replayContext.getReplayGate().advanceTo(completionOffset);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Skipped entry point COMPLETION at offset {} (entry point was called by unweaved code)",
            completionOffset);
      }
      expectedEntry = cursor.peekNext();
    }

    if (expectedEntry == null || expectedEntry.getKind() != WalEntryKind.OPERATION) {
      // Cursor exhausted or unexpected completion entry — extra live operation.
      // Advance the gate to Long.MAX_VALUE to signal that this thread's WAL entries
      // are exhausted. This unblocks any injectors waiting for entry points on this
      // thread - they can proceed since there are no more WAL entries to wait for.
      // Without this, the injector would wait forever for a gate offset that never
      // comes (because extra operations don't have WAL offsets to advance the gate).
      replayContext.getReplayGate().advanceTo(Long.MAX_VALUE);
      replayContext.getDivergenceDetector().reportExtraOperation(liveSig, threadName);
      return invoke(pjp, pjp.getArgs());
    }

    OperationSignature walSig = OperationSignature.fromWalEntry(expectedEntry);
    if (!liveSig.matches(walSig)) {
      // If the expected entry is an entry point and there's a mismatch, it might be because
      // the entry point was called by unweaved code (e.g., JavaFX calling constructor) and
      // we're now seeing a nested operation inside that entry point. In this case, we should:
      // 1. Mark the entry point as handled (so injector doesn't try to inject it)
      // 2. Advance past the entry point
      // 3. Re-check with the next WAL entry
      // This handles the JavaFX case where the constructor entry point is never directly
      // visible to dispatchReplay() because JavaFX (unweaved) calls it.
      if (expectedEntry.isEntryPoint()) {
        long skippedOffset = expectedEntry.getOffset();

        // Check if this entry point is pending injection. If so, don't skip it - this would
        // race with the injector. Report the live operation as extra and let the injection
        // happen normally.
        if (replayContext.isPendingInjection(threadName, skippedOffset)) {
          logger.info(
              "[{}] Entry point at offset {} is pending injection, not skipping. "
                  + "Live operation {} will be reported as extra.",
              threadName,
              skippedOffset,
              liveSig);
          replayContext.getDivergenceDetector().reportExtraOperation(liveSig, threadName);
          return invoke(pjp, pjp.getArgs());
        }

        replayContext.markEntryPointHandled(skippedOffset);

        // Determine skip strategy based on whether the entry point's thread has an injector.
        // If an injector exists, the entry point will be injected on its target thread and
        // nested operations will be handled there — skip the entire span.
        // If no injector exists (e.g., self-caller main()), nested operations will run on
        // this thread through dispatchReplay — skip only the OPERATION entry.
        boolean hasInjector = replayContext.hasInjectorForThread(expectedEntry.getThreadName());
        if (hasInjector) {
          Long completionOffset = replayContext.getWalIndex().getCompletionOffset(skippedOffset);
          if (completionOffset != null) {
            cursor.advancePast(completionOffset);
          } else {
            cursor.advance();
          }
        } else {
          cursor.advance();
          deferGateBeforeInvoke = true;
        }

        // Only advance gate to operation offset, not completion. Advancing to completion
        // would signal all operations in the span are "done", but other threads may have
        // operations within this offset range that haven't been processed yet.
        replayContext.getReplayGate().advanceTo(skippedOffset);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "[{}] Skipped entry point at offset {} (hasInjector={}, strategy={}), "
                  + "gate advanced to {}, re-matching",
              threadName,
              skippedOffset,
              hasInjector,
              hasInjector ? "span-skip" : "operation-only",
              skippedOffset);
        }

        // Re-check with the next entry. Keep skipping entry points until we find a match
        // or a non-entry-point mismatch. This handles cases with multiple consecutive
        // entry points called by unweaved code.
        while (true) {
          expectedEntry = cursor.peekNext();

          // When operation-only skip was used (no injector), the COMPLETION entry for the
          // skipped entry point remains in the cursor and must be skipped here.
          if (!hasInjector) {
            while (expectedEntry != null
                && expectedEntry.getKind() == WalEntryKind.COMPLETION
                && expectedEntry.isEntryPoint()) {
              long completionOff = expectedEntry.getOffset();
              cursor.advance();
              replayContext.getReplayGate().advanceTo(completionOff);
              expectedEntry = cursor.peekNext();
            }
          }

          if (expectedEntry == null || expectedEntry.getKind() != WalEntryKind.OPERATION) {
            // Cursor exhausted or unexpected entry after skipping
            if (logger.isInfoEnabled()) {
              logger.info(
                  "[{}] Cursor exhausted after skipping entry points. Live op {} is extra.",
                  threadName,
                  liveSig);
            }
            replayContext.getDivergenceDetector().reportExtraOperation(liveSig, threadName);
            return invoke(pjp, pjp.getArgs());
          }

          walSig = OperationSignature.fromWalEntry(expectedEntry);
          if (liveSig.matches(walSig)) {
            // Matched after skipping entry point(s) - continue with normal flow below
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "[{}] Matched {} after skipping entry point(s), cursor at offset {}",
                  threadName,
                  liveSig,
                  expectedEntry.getOffset());
            }
            break;
          }

          // Still mismatched - if it's another entry point, skip it too
          if (expectedEntry.isEntryPoint()) {
            long anotherSkippedOffset = expectedEntry.getOffset();

            // Check if pending injection
            if (replayContext.isPendingInjection(threadName, anotherSkippedOffset)) {
              if (logger.isInfoEnabled()) {
                logger.info(
                    "[{}] Entry point at offset {} is pending injection, stopping skip loop. "
                        + "Live operation {} will be reported as extra.",
                    threadName,
                    anotherSkippedOffset,
                    liveSig);
              }
              replayContext.getDivergenceDetector().reportExtraOperation(liveSig, threadName);
              return invoke(pjp, pjp.getArgs());
            }

            replayContext.markEntryPointHandled(anotherSkippedOffset);

            boolean anotherHasInjector =
                replayContext.hasInjectorForThread(expectedEntry.getThreadName());
            if (anotherHasInjector) {
              Long anotherCompletionOffset =
                  replayContext.getWalIndex().getCompletionOffset(anotherSkippedOffset);
              if (anotherCompletionOffset != null) {
                cursor.advancePast(anotherCompletionOffset);
              } else {
                cursor.advance();
              }
            } else {
              cursor.advance();
              // Track that we used operation-only skip for the COMPLETION skip loop
              hasInjector = false;
              deferGateBeforeInvoke = true;
            }
            replayContext.getReplayGate().advanceTo(anotherSkippedOffset);
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "[{}] Skipped another entry point at offset {} (hasInjector={})",
                  threadName,
                  anotherSkippedOffset,
                  anotherHasInjector);
            }
            // Continue loop to check next entry
          } else {
            // Non-entry-point mismatch - advance cursor/gate and invoke
            // This ensures we make progress even with mismatches
            long mismatchOffset = expectedEntry.getOffset();
            cursor.advance();
            replayContext.getReplayGate().advanceTo(mismatchOffset);
            replayContext
                .getDivergenceDetector()
                .reportOperationMismatch(expectedEntry, liveSig, threadName);
            return invoke(pjp, pjp.getArgs());
          }
        }
      } else {
        // Regular mismatch (not an entry point) — advance cursor/gate and execute
        // This ensures we make progress even with mismatches
        long mismatchOffset = expectedEntry.getOffset();
        cursor.advance();
        replayContext.getReplayGate().advanceTo(mismatchOffset);
        replayContext
            .getDivergenceDetector()
            .reportOperationMismatch(expectedEntry, liveSig, threadName);
        return invoke(pjp, pjp.getArgs());
      }
    }

    // Consult the replay policy to determine whether to re-execute or stub this operation.
    // Phantom cascading takes priority: if the target object is a phantom (its constructor was
    // stubbed), all operations on it are automatically stubbed regardless of the policy.
    long operationOffset = expectedEntry.getOffset();
    ReplayAction action = resolveReplayAction(expectedEntry);

    switch (action) {
      case STUB_FROM_WAL:
      case STUB_FROM_WAL_VERIFIED:
        return handleStub(cursor, expectedEntry, operationOffset, pjp, action);
      case STUB_WITH_SIDE_EFFECTS:
        return handleStubWithSideEffects(cursor, expectedEntry, operationOffset, pjp);
      case RE_EXECUTE:
      case RE_EXECUTE_UNCHECKED:
      default:
        break;
    }

    // Step 2: RE_EXECUTE — advance past operation entry, invoke, then verify completion
    cursor.advance();

    // Register target object if this is an instance method/field call and the target isn't
    // already registered. This handles objects created by unweaved code (e.g., JavaFX
    // controllers created by FXMLLoader) that later become targets of entry point injection.
    Object target = pjp.getTarget();
    if (target != null) {
      int walTargetRef = extractTargetRef(expectedEntry);
      if (walTargetRef != 0 && replayContext.getObjectStore().resolveOrNull(walTargetRef) == null) {
        replayContext.getObjectStore().register(walTargetRef, target);
      }
    }

    // Also register argument objects that aren't already registered. This catches objects
    // created by unweaved code that are passed as arguments before becoming entry point targets.
    registerArgumentObjects(expectedEntry, pjp.getArgs());

    // If this operation is an entry point being executed via the real runtime (e.g., JavaFX
    // calling start() which calls constructor/buildUI), mark it as handled so the
    // ReplayInputInjector doesn't try to inject it again. This prevents duplicate execution
    // when both the real runtime and the injector try to execute the same entry point.
    //
    // IMPORTANT: This MUST happen BEFORE advancing the gate. The gate advancement unblocks
    // the injector, which will check isEntryPointHandled(). If we mark after advancing,
    // there's a race where the injector sees the gate has reached this offset but the
    // entry point isn't marked as handled yet, causing duplicate execution.
    if (expectedEntry.isEntryPoint()) {
      replayContext.markEntryPointHandled(operationOffset);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Entry point at offset {} handled via dispatchReplay (real runtime path)",
            operationOffset);
      }
    }

    // Advance the gate after consuming the OPERATION entry so that injection threads
    // waiting on subsequent offsets can proceed even if invoke() blocks (e.g.,
    // CountDownLatch.await blocks until a shutdown entry-point is injected).
    //
    // EXCEPTION: When the operation was matched after an operation-only entry-point skip
    // (deferGateBeforeInvoke == true), gate advancement is deferred to AFTER invoke returns.
    // This prevents the gate from unblocking injection threads prematurely. The key scenario
    // is Application.launch: if the gate advances to its offset before invoke, the injector
    // for the FX thread unblocks immediately and races to inject entry points before the FX
    // thread has had a chance to process them naturally via dispatchReplay. By deferring,
    // the FX thread's own dispatchReplay calls advance the gate independently (e.g., when
    // processing FX entry points), and the injector sees them as already handled.
    if (!deferGateBeforeInvoke) {
      replayContext.getReplayGate().advanceTo(operationOffset);
    }

    Object result = invoke(pjp, pjp.getArgs());

    // Deferred gate advancement: now that invoke has returned, advance the gate.
    // By this point, other threads (e.g., FX thread) have independently advanced the gate
    // past operationOffset via their own dispatchReplay calls, so this is typically a no-op
    // (the gate uses max-monotonic advancement).
    if (deferGateBeforeInvoke) {
      replayContext.getReplayGate().advanceTo(operationOffset);
    }

    // Step 3: Verify return value against WAL completion entry
    WalEntry completionEntry = cursor.peekNext();
    if (completionEntry != null && completionEntry.getKind() == WalEntryKind.COMPLETION) {
      replayContext.getDivergenceDetector().compareReturnValue(completionEntry, result, threadName);

      // Register object ref mapping if the return value has a ref in the WAL
      if (result != null) {
        int walRef = extractReturnRef(completionEntry);
        if (walRef != 0) {
          replayContext.getObjectStore().register(walRef, result);
        }
      }

      long completionOffset = completionEntry.getOffset();
      cursor.advance();

      // Advance the ReplayGate so injection threads waiting on WAL ordering can proceed
      replayContext.getReplayGate().advanceTo(completionOffset);
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
   * Registers the return value of a <b>constructor</b> entry-point invocation in the {@link
   * io.quasient.pal.core.replay.ReplayObjectStore}.
   *
   * <p>This is called from the {@code dispatchIncoming} chain and direct paths after an entry-point
   * constructor completes. Registration is critical for constructors: without it, subsequent method
   * calls on the constructed object (dispatched as later entry points) would fail to resolve the
   * target by WAL ref, triggering the phantom stub path and corrupting replay state.
   *
   * <p><b>Only constructor return values should be registered.</b> Method return values (e.g.,
   * {@code TextField.getText()}) may diverge between recording and replay. Registering a divergent
   * live value would corrupt argument resolution for downstream entry points that reference the
   * same WAL ref — they would receive the (wrong) live value instead of deserializing the correct
   * value from the WAL.
   *
   * <p>Uses {@link io.quasient.pal.common.replay.WalIndex#getEntryAtOffset} for O(1) lookup of the
   * completion entry by offset, since the per-thread cursor may have already been advanced through
   * the span by nested {@code dispatchReplay} calls during the invocation.
   *
   * @param ctx the replay context providing access to the WAL index and object store
   * @param completionOffset the WAL offset of the COMPLETION entry
   * @param returnValue the live object returned by the invocation (may be {@code null})
   * @param entryPointMessageType the message type of the entry-point operation; only {@link
   *     MessageType#EXEC_CONSTRUCTOR} triggers registration
   */
  private void registerEntryPointReturnValue(
      ReplayContext ctx,
      long completionOffset,
      Object returnValue,
      MessageType entryPointMessageType) {
    if (entryPointMessageType != MessageType.EXEC_CONSTRUCTOR) {
      return;
    }
    if (returnValue == null) {
      return;
    }
    WalEntry completionEntry = ctx.getWalIndex().getEntryAtOffset(completionOffset);
    if (completionEntry == null) {
      return;
    }
    int walRef = extractReturnRef(completionEntry);
    if (walRef != 0) {
      ctx.getObjectStore().register(walRef, returnValue);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Registered entry-point return value in ReplayObjectStore: walRef={} -> {}",
            walRef,
            returnValue.getClass().getName());
      }
    }
  }

  /**
   * Registers argument objects from the WAL entry with their corresponding live objects.
   *
   * <p>During replay, objects created by unweaved code (e.g., JavaFX controllers created by
   * FXMLLoader) may be passed as arguments to weaved methods before they become targets. This
   * method ensures such objects are registered in the {@link
   * io.quasient.pal.core.replay.ReplayObjectStore} so they can be resolved later when entry point
   * injection needs to find them by WAL ref.
   *
   * @param operationEntry the operation WAL entry containing parameter refs
   * @param liveArgs the live argument objects from {@code pjp.getArgs()}
   */
  private void registerArgumentObjects(WalEntry operationEntry, Object[] liveArgs) {
    if (liveArgs == null || liveArgs.length == 0) {
      return;
    }
    ExecMessage msg = operationEntry.getRawMessage();
    if (msg == null) {
      return;
    }

    Obj[] args = extractArgs(msg);
    if (args.length == 0) {
      return;
    }

    // Match WAL argument refs with live arguments by index
    int minLen = Math.min(args.length, liveArgs.length);
    for (int i = 0; i < minLen; i++) {
      Object liveArg = liveArgs[i];
      if (liveArg == null) {
        continue;
      }
      Obj arg = args[i];
      if (arg == null) {
        continue;
      }
      int walRef = arg.getRef();
      if (walRef != 0 && replayContext.getObjectStore().resolveOrNull(walRef) == null) {
        replayContext.getObjectStore().register(walRef, liveArg);
      }
    }
  }

  /**
   * Extracts the args array from an ExecMessage based on its type.
   *
   * @param msg the execution message
   * @return the args array, or an empty array if the message type has no args
   */
  private static Obj[] extractArgs(ExecMessage msg) {
    if (msg.getInstanceMethodCall() != null) {
      return msg.getInstanceMethodCall().getArgs();
    }
    if (msg.getClassMethodCall() != null) {
      return msg.getClassMethodCall().getArgs();
    }
    if (msg.getConstructorCall() != null) {
      return msg.getConstructorCall().getArgs();
    }
    // Field operations have no args (value is stored separately)
    return new Obj[0];
  }

  /**
   * Extracts the target object reference from a WAL operation entry.
   *
   * <p>This is used during replay to register objects created by unweaved code (e.g., JavaFX
   * controllers) when they're first seen as targets of weaved method calls. Only instance method
   * calls and instance field operations have target objects.
   *
   * @param operationEntry the operation WAL entry (must be an OPERATION kind)
   * @return the target object reference, or {@code 0} if the operation has no target
   */
  private static int extractTargetRef(WalEntry operationEntry) {
    ExecMessage msg = operationEntry.getRawMessage();
    if (msg == null) {
      return 0;
    }
    // Instance method call
    if (msg.getInstanceMethodCall() != null) {
      return msg.getInstanceMethodCall().getObjectRef();
    }
    // Instance field get
    if (msg.getInstanceFieldGet() != null) {
      return msg.getInstanceFieldGet().getObjectRef();
    }
    // Instance field put
    if (msg.getInstanceFieldPut() != null) {
      return msg.getInstanceFieldPut().getObjectRef();
    }
    // Constructor, static method, static field — no target object
    return 0;
  }

  /**
   * Determines the replay action for the given WAL entry by checking phantom cascading first, then
   * consulting the replay policy.
   *
   * <p>Phantom cascading takes priority: if the operation's target object is a phantom (its
   * constructor was stubbed), the operation is automatically stubbed from WAL regardless of the
   * policy. This ensures that all operations on objects that were never created during replay are
   * consistently stubbed, cascading through the entire dependency tree.
   *
   * @param entry the WAL operation entry to evaluate
   * @return the resolved replay action
   */
  private ReplayAction resolveReplayAction(WalEntry entry) {
    if (isPhantomTarget(entry)) {
      return ReplayAction.STUB_FROM_WAL;
    }
    return replayContext
        .getPolicy()
        .getAction(entry.getClassName(), entry.getExecutableName(), entry.getMessageType());
  }

  /**
   * Returns whether the target object of the given WAL entry is a phantom in the replay object
   * store.
   *
   * <p>A phantom target indicates that the object's constructor was stubbed during replay, so the
   * object was never actually created. Any subsequent operations on phantom targets must also be
   * stubbed.
   *
   * @param entry the WAL operation entry to check
   * @return {@code true} if the target object reference is non-zero and registered as a phantom
   */
  private boolean isPhantomTarget(WalEntry entry) {
    int targetRef = extractTargetRef(entry);
    return targetRef != 0 && replayContext.getObjectStore().isPhantom(targetRef);
  }

  /**
   * Handles the {@link ReplayAction#STUB_FROM_WAL} and {@link ReplayAction#STUB_FROM_WAL_VERIFIED}
   * dispatch paths by returning the WAL-recorded value without executing the operation.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Finds the span's completion entry via the WAL index
   *   <li>Checks for exception replay (re-throws WAL-recorded exceptions)
   *   <li>Reconstructs the return value from the completion entry
   *   <li>Registers phantom or live objects in the object store
   *   <li>Marks entry points as handled
   *   <li>Advances the cursor past the entire span (skipping nested operations)
   *   <li>Advances the replay gate to the completion offset
   *   <li>For STUB_FROM_WAL_VERIFIED: also invokes the operation and compares (best-effort)
   * </ol>
   *
   * @param cursor the per-thread replay cursor
   * @param expectedEntry the matched WAL operation entry
   * @param operationOffset the WAL offset of the operation
   * @param pjp the AspectJ join point handle
   * @param action the specific stub action (STUB_FROM_WAL or STUB_FROM_WAL_VERIFIED)
   * @return the reconstructed return value from the WAL
   * @throws Throwable if the WAL completion entry contains a raised throwable
   */
  private Object handleStub(
      ReplayCursor cursor,
      WalEntry expectedEntry,
      long operationOffset,
      ProceedingJoinPoint pjp,
      ReplayAction action)
      throws Throwable {

    // 1. Find the span's completion entry
    Span span = replayContext.getWalIndex().getSpans().get(operationOffset);
    if (span == null) {
      // No span found — fall back to re-execution (defensive; should not happen for valid WALs)
      logger.warn(
          "No span found for STUB_FROM_WAL at offset {}, falling back to RE_EXECUTE",
          operationOffset);
      cursor.advance();
      return invoke(pjp, pjp.getArgs());
    }
    long completionOffset = span.completionOffset();
    WalEntry completionEntry = replayContext.getWalIndex().getEntryAtOffset(completionOffset);
    if (completionEntry == null) {
      logger.warn(
          "No completion entry at offset {} for STUB_FROM_WAL at offset {}, falling back to RE_EXECUTE",
          completionOffset,
          operationOffset);
      cursor.advance();
      return invoke(pjp, pjp.getArgs());
    }

    // 2. Check for exception replay: if the completion entry has a raisedThrowable, reconstruct
    // and re-throw it so the stub faithfully reproduces the original behavior including exceptions.
    ExecMessage completionMsg = completionEntry.getRawMessage();
    RaisedThrowable raised = completionMsg.getRaisedThrowable();
    if (raised != null && raised.getThrowable() != null) {
      advanceAfterStub(cursor, expectedEntry, operationOffset, completionOffset);
      throw reconstructThrowable(raised.getThrowable());
    }

    // 3. Reconstruct the return value from the WAL
    Object stubbedValue = reconstructReturnValue(completionEntry);

    // 4. Register phantom or live object in the object store
    int walRef = extractReturnRef(completionEntry);
    if (walRef != 0) {
      if (stubbedValue != null) {
        replayContext.getObjectStore().register(walRef, stubbedValue);
      } else {
        replayContext.getObjectStore().registerPhantom(walRef);
      }
    }

    // 5-7. Mark entry point, advance cursor, advance gate
    advanceAfterStub(cursor, expectedEntry, operationOffset, completionOffset);

    // 8. STUB_FROM_WAL_VERIFIED: also invoke the operation and compare (best-effort)
    if (action == ReplayAction.STUB_FROM_WAL_VERIFIED) {
      try {
        Object liveResult = invoke(pjp, pjp.getArgs());
        replayContext
            .getDivergenceDetector()
            .compareReturnValue(completionEntry, liveResult, Thread.currentThread().getName());
      } catch (Throwable t) {
        if (logger.isDebugEnabled()) {
          logger.debug("STUB_FROM_WAL_VERIFIED execution threw: {}", t.getMessage());
        }
      }
    }

    return stubbedValue;
  }

  /**
   * Handles the {@link ReplayAction#STUB_WITH_SIDE_EFFECTS} dispatch path by returning the
   * WAL-recorded value and replaying field mutations (PUT_FIELD / PUT_STATIC) from within the span.
   *
   * <p>This combines the stub return behavior of {@link #handleStub} with field mutation replay via
   * {@link SpanFieldMutationReplayer}. The method:
   *
   * <ol>
   *   <li>Finds the span's completion entry via the WAL index
   *   <li>Checks for exception replay (re-throws WAL-recorded exceptions)
   *   <li>Reconstructs the return value from the completion entry
   *   <li>Replays PUT_FIELD and PUT_STATIC mutations from within the span
   *   <li>Registers phantom or live objects in the object store
   *   <li>Marks entry points as handled, advances cursor and gate
   * </ol>
   *
   * @param cursor the per-thread replay cursor
   * @param expectedEntry the matched WAL operation entry
   * @param operationOffset the WAL offset of the operation
   * @param pjp the AspectJ join point handle
   * @return the reconstructed return value from the WAL
   * @throws Throwable if the WAL completion entry contains a raised throwable
   */
  private Object handleStubWithSideEffects(
      ReplayCursor cursor, WalEntry expectedEntry, long operationOffset, ProceedingJoinPoint pjp)
      throws Throwable {

    // 1. Find the span's completion entry
    Span span = replayContext.getWalIndex().getSpans().get(operationOffset);
    if (span == null) {
      logger.warn(
          "No span found for STUB_WITH_SIDE_EFFECTS at offset {}, falling back to RE_EXECUTE",
          operationOffset);
      cursor.advance();
      return invoke(pjp, pjp.getArgs());
    }
    long completionOffset = span.completionOffset();
    WalEntry completionEntry = replayContext.getWalIndex().getEntryAtOffset(completionOffset);
    if (completionEntry == null) {
      logger.warn(
          "No completion entry at offset {} for STUB_WITH_SIDE_EFFECTS at offset {}, falling back to RE_EXECUTE",
          completionOffset,
          operationOffset);
      cursor.advance();
      return invoke(pjp, pjp.getArgs());
    }

    // 2. Check for exception replay
    ExecMessage completionMsg = completionEntry.getRawMessage();
    RaisedThrowable raised = completionMsg.getRaisedThrowable();
    if (raised != null && raised.getThrowable() != null) {
      // Replay mutations even before re-throwing, so side effects are applied
      spanFieldMutationReplayer.replayMutations(
          replayContext.getWalIndex(), span, replayContext.getObjectStore());
      advanceAfterStub(cursor, expectedEntry, operationOffset, completionOffset);
      throw reconstructThrowable(raised.getThrowable());
    }

    // 3. Reconstruct the return value from the WAL
    Object stubbedValue = reconstructReturnValue(completionEntry);

    // 4. Replay PUT_FIELD and PUT_STATIC mutations from within the span
    spanFieldMutationReplayer.replayMutations(
        replayContext.getWalIndex(), span, replayContext.getObjectStore());

    // 5. Register phantom or live object in the object store
    int walRef = extractReturnRef(completionEntry);
    if (walRef != 0) {
      if (stubbedValue != null) {
        replayContext.getObjectStore().register(walRef, stubbedValue);
      } else {
        replayContext.getObjectStore().registerPhantom(walRef);
      }
    }

    // 6. Mark entry point, advance cursor, advance gate
    advanceAfterStub(cursor, expectedEntry, operationOffset, completionOffset);

    return stubbedValue;
  }

  /**
   * Common post-stub bookkeeping: marks entry points as handled, advances the cursor past the span,
   * and advances the replay gate.
   *
   * @param cursor the per-thread replay cursor
   * @param expectedEntry the matched WAL operation entry
   * @param operationOffset the WAL offset of the operation
   * @param completionOffset the WAL offset of the span's completion entry
   */
  private void advanceAfterStub(
      ReplayCursor cursor, WalEntry expectedEntry, long operationOffset, long completionOffset) {
    if (expectedEntry.isEntryPoint()) {
      replayContext.markEntryPointHandled(operationOffset);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Entry point at offset {} handled via STUB_FROM_WAL dispatch", operationOffset);
      }
    }

    cursor.advancePast(completionOffset);
    replayContext.getReplayGate().advanceTo(completionOffset);
  }

  /**
   * Handles phantom object stubbing for entry point injection when the target object doesn't exist.
   *
   * <p>This is called from {@code dispatchIncoming} when the loading phase fails because the target
   * object (created by unweaved code, e.g., JavaFX KeyEvent) isn't in the ReplayObjectStore.
   * Instead of failing, we check if the operation should be stubbed and return the WAL-recorded
   * value.
   *
   * <p>The method:
   *
   * <ol>
   *   <li>Finds the span's completion entry via the WAL index
   *   <li>Checks for exception replay (re-throws WAL-recorded exceptions)
   *   <li>Reconstructs the return value from the completion entry
   *   <li>Replays field mutations if action is {@code STUB_WITH_SIDE_EFFECTS}
   *   <li>Registers phantom or live objects in the object store
   *   <li>Marks the entry point as handled and advances the replay gate
   *   <li>Creates and returns the response ExecMessage
   * </ol>
   *
   * @param incomingCall the original incoming message
   * @param operationOffset the WAL offset of the operation (from pending injection queue)
   * @param action the stub action (STUB_FROM_WAL, STUB_FROM_WAL_VERIFIED, or
   *     STUB_WITH_SIDE_EFFECTS)
   * @return the response ExecMessage with the stubbed return value, or {@code null} if stubbing
   *     cannot be performed (caller should fall back to normal error handling)
   * @throws Throwable if the WAL completion entry contains a raised throwable
   */
  private ExecMessage handlePhantomStub(
      ExecMessage incomingCall, long operationOffset, ReplayAction action) throws Throwable {

    // 1. Find the span's completion entry
    Span span = replayContext.getWalIndex().getSpans().get(operationOffset);
    if (span == null) {
      logger.warn("No span found for phantom stub at offset {}, cannot stub", operationOffset);
      return null;
    }
    long completionOffset = span.completionOffset();
    WalEntry completionEntry = replayContext.getWalIndex().getEntryAtOffset(completionOffset);
    if (completionEntry == null) {
      logger.warn(
          "No completion entry at offset {} for phantom stub at offset {}, cannot stub",
          completionOffset,
          operationOffset);
      return null;
    }

    // 2. Check for exception replay
    ExecMessage completionMsg = completionEntry.getRawMessage();
    RaisedThrowable raised = completionMsg.getRaisedThrowable();
    if (raised != null && raised.getThrowable() != null) {
      String walThreadName = incomingCall.getThreadName();
      advancePhantomStub(walThreadName, operationOffset, completionOffset);
      throw reconstructThrowable(raised.getThrowable());
    }

    // 3. Reconstruct the return value from the WAL
    Object stubbedValue = reconstructReturnValue(completionEntry);

    // 4. Replay field mutations if action is STUB_WITH_SIDE_EFFECTS
    if (action == ReplayAction.STUB_WITH_SIDE_EFFECTS) {
      spanFieldMutationReplayer.replayMutations(
          replayContext.getWalIndex(), span, replayContext.getObjectStore());
    }

    // 5. Register phantom or live object in the object store
    int walRef = extractReturnRef(completionEntry);
    if (walRef != 0) {
      if (stubbedValue != null) {
        replayContext.getObjectStore().register(walRef, stubbedValue);
      } else {
        replayContext.getObjectStore().registerPhantom(walRef);
      }
    }

    // 6. Mark entry point as handled, advance cursor and gate
    String walThreadName = incomingCall.getThreadName();
    advancePhantomStub(walThreadName, operationOffset, completionOffset);

    // 7. Create and return the response ExecMessage by copying from WAL completion.
    // We cannot use createAfterExecMessage because that requires the AccessibleObject,
    // which we don't have since the loading phase failed before we could resolve it.
    // Instead, we create a response message that copies the return value from the WAL.
    ExecMessage response = new ExecMessage();
    response.setMessageId(incomingCall.getMessageId());
    response.setPeerUuid(incomingCall.getPeerUuid());
    response.setReturnValue(completionMsg.getReturnValue());
    return response;
  }

  /**
   * Advances replay state after handling a phantom stub in dispatchIncoming.
   *
   * <p>This method advances both the per-thread cursor AND the gate. Unlike {@link
   * #advanceAfterStub} which is called during dispatchReplay (where we already have the cursor),
   * this is called from dispatchIncoming where we need to look up the cursor by thread name.
   *
   * @param walThreadName the WAL thread name (from the incoming message)
   * @param operationOffset the WAL offset of the operation entry
   * @param completionOffset the WAL offset of the completion entry
   */
  private void advancePhantomStub(
      String walThreadName, long operationOffset, long completionOffset) {
    // Mark entry point as handled (phantom stubs in dispatchIncoming are always entry points)
    replayContext.markEntryPointHandled(operationOffset);
    if (logger.isDebugEnabled()) {
      logger.debug("Entry point at offset {} handled via phantom stub", operationOffset);
    }

    // Advance the per-thread cursor past the operation and completion entries.
    // This is critical: without this, subsequent dispatchIncoming calls on the same thread
    // will see a stale cursor position and fail to match.
    ReplayCursor cursor = replayContext.getCursor(walThreadName);
    if (cursor != null) {
      cursor.advancePast(completionOffset);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Phantom stub advanced cursor for thread '{}' past completion offset {}",
            walThreadName,
            completionOffset);
      }
    }

    // Advance the gate to the completion offset
    replayContext.getReplayGate().advanceTo(completionOffset);
  }

  /**
   * Reconstructs a return value from a WAL completion entry.
   *
   * <p>Handles the following cases:
   *
   * <ul>
   *   <li><b>Void method:</b> {@link ReturnValue#getIsVoid()} is true — returns {@code null}
   *   <li><b>Null return:</b> {@link Obj#getIsNull()} is true — returns {@code null}
   *   <li><b>Reference-only:</b> ref is non-zero but no serialized value — returns {@code null}
   *       (object will be registered as phantom)
   *   <li><b>Value object:</b> deserializable via {@link Unwrapper#unwrapObject(Obj)} — returns the
   *       reconstructed object
   *   <li><b>Unreconstructable:</b> {@link UnsupportedOperationException} from unwrapper — returns
   *       {@code null} (object will be registered as phantom)
   * </ul>
   *
   * @param completionEntry the WAL completion entry containing the return value
   * @return the reconstructed value, or {@code null} for void/null/unreconstructable returns
   */
  private Object reconstructReturnValue(WalEntry completionEntry) {
    ExecMessage msg = completionEntry.getRawMessage();
    ReturnValue rv = msg.getReturnValue();
    if (rv == null || rv.getIsVoid()) {
      return null;
    }
    Obj obj = rv.getObject();
    if (obj == null || obj.getIsNull()) {
      return null;
    }
    // Reference-only: has a ref but no serialized value
    if (obj.getRef() != 0 && (obj.getValue() == null || obj.getValue().isEmpty())) {
      return null;
    }
    try {
      return Unwrapper.unwrapObject(obj);
    } catch (UnsupportedOperationException | ClassNotFoundException e) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Cannot reconstruct return value at offset {}, registering as phantom: {}",
            completionEntry.getOffset(),
            e.getMessage());
      }
      return null;
    }
  }

  /**
   * Reconstructs a {@link Throwable java.lang.Throwable} from a WAL-recorded Colfer {@link
   * io.quasient.pal.messages.colfer.Throwable}.
   *
   * <p>Attempts to instantiate the original exception class via its {@code String} constructor. If
   * the class cannot be found or instantiated, falls back to a {@link RuntimeException} wrapping
   * the original message and type name.
   *
   * @param colferThrowable the serialized throwable from the WAL completion entry
   * @return a reconstructed Java throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // colfer.Throwable clashes with java.lang
  private static Throwable reconstructThrowable(
      io.quasient.pal.messages.colfer.Throwable colferThrowable) {
    String typeName = colferThrowable.getType();
    String message = colferThrowable.getMessage();
    try {
      Class<?> clazz =
          Class.forName(typeName, true, Thread.currentThread().getContextClassLoader());
      if (Throwable.class.isAssignableFrom(clazz)) {
        try {
          return (Throwable) clazz.getConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException e) {
          // No String constructor — try no-arg
          return (Throwable) clazz.getConstructor().newInstance();
        }
      }
    } catch (ReflectiveOperationException e) {
      // Cannot reconstruct the original type
    }
    return new RuntimeException(typeName + ": " + message);
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

    // Track whether this is a replay injection for phantom stub and logging decisions below.
    // Replay injections replay operations that originally ran inside the JVM with full access,
    // so they should be able to access private fields/methods.
    final boolean isReplayInjection = messageChannel == MessageChannelType.REPLAY_INJECTION;

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
      // get type
      final MessageType messageType = getMessageTypeOf(incomingCall);

      // check if this dispatcher supports the message type
      if (!getSupportedMessageType().equals(messageType)) {
        throw new IllegalArgumentException(
            "Unsupported message type: " + messageType + " for dispatcher: " + this.getClass());
      }

      // RPC policy check: deny operations that violate the configured policy.
      // The REPLAY_INJECTION exemption is handled inside RpcPolicyChecker.
      rpcPolicyChecker.checkAccess(incomingCall, messageType, messageChannel);

      // Extract operation identity for recording scope filtering.
      // Reuse className/methodName if already set from the tracking block above.
      final String incomingClassName = className != null ? className : getClassname(incomingCall);
      final String incomingMethodName =
          methodName != null ? methodName : getExecutableName(incomingCall);
      final MemberCategory incomingCategory = MemberCategory.fromMessageType(messageType);

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

      // Recording scope + channel-matrix gate for incoming WAL/PUB writes.
      final boolean incomingInRecordingScope =
          recordingScope.isInScope(incomingClassName, incomingMethodName, incomingCategory);

      // Send BEFORE message to WAL/PUB (consistent with hot-path dispatch())
      if (incomingInRecordingScope && shouldWriteIncomingToWal(messageChannel)) {
        // Record the executor thread name (not the sender's thread) so that multi-threaded
        // replay can create per-thread injectors matching the actual execution topology.
        incomingCall.setThreadName(Thread.currentThread().getName());
        incomingCall.setEntryPoint(true);
        @SuppressWarnings("unused")
        final ExecMessage beforeExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(incomingCall), ExecPhase.BEFORE);
      }

      // During replay of entry points, we need to advance the cursor past the entry-point
      // OPERATION entry so that nested operations see the correct cursor position. This
      // advancement MUST happen on the target thread (e.g., FX thread), not the injector thread.
      // Otherwise, the target thread's ongoing nested operations (via dispatchReplay) will see
      // a cursor that has been prematurely advanced, causing OPERATION_MISMATCH divergences.
      //
      // We use incomingCall.isEntryPoint() to determine if cursor advancement is needed, rather
      // than peeking the cursor. Peeking would be racy because the cursor is owned by the target
      // thread (which may be advancing it concurrently via dispatchReplay), not the injector
      // thread that calls dispatchIncoming().
      //
      // The ReplayGate advancement is done in ReplayInputInjector (which knows the WAL offset),
      // not here.
      ReplayCursor replayEntryPointCursor = null;
      if (replayContext != null
          && runOptions.contains(RunOptions.WITH_REPLAY)
          && incomingCall.getEntryPoint()) {
        String walThreadName = incomingCall.getThreadName();
        replayEntryPointCursor = replayContext.getCursor(walThreadName);
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

        // 6. Set field/method accessible — RPC access control is enforced earlier by
        // RpcPolicyChecker, so once permitted, all visibility levels are accessible.
        if (accessibleObject != null) {
          accessibleObject.setAccessible(true);
        }
      } catch (ReflectiveOperationException | AmbiguousCallException | RuntimeException ex) {
        // Log at DEBUG for replay injection (phantom stub may recover), ERROR otherwise
        if (isReplayInjection && replayContext != null) {
          if (logger.isDebugEnabled()) {
            logger.debug("Loading phase failed (phantom stub may handle): {}", ex.getMessage());
          }
        } else {
          logger.error("Error during loading phase", ex);
        }
        exceptionWhileLoading = ex;
      }

      // Handle phantom object stubbing: when loading phase fails during replay injection because
      // the target object doesn't exist (e.g., KeyEvent created by unweaved JavaFX code), check
      // if the operation should be stubbed. If yes, return the WAL-recorded value directly.
      if (isReplayInjection
          && exceptionWhileLoading != null
          && replayContext != null
          && runOptions.contains(RunOptions.WITH_REPLAY)) {

        // Use the WAL thread name from the message, not the current (injector) thread name.
        // The pending injection was pushed with the WAL thread name by ReplayInputInjector.
        String phantomThreadName = incomingCall.getThreadName();
        long phantomOffset = replayContext.popPendingInjection(phantomThreadName);

        // Debug: log phantom stub check details
        String phantomClassName = getClassname(incomingCall);
        String phantomMethodName = getExecutableName(incomingCall);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Phantom stub check: threadName={}, offset={}, class={}, method={}",
              phantomThreadName,
              phantomOffset,
              phantomClassName,
              phantomMethodName);
        }

        if (phantomOffset >= 0) {
          // Check if this operation should be stubbed according to the replay policy
          ReplayAction phantomAction =
              replayContext.getPolicy().getAction(phantomClassName, phantomMethodName, messageType);

          if (logger.isDebugEnabled()) {
            logger.debug(
                "Phantom stub action for {}.{}: {} (messageType={})",
                phantomClassName,
                phantomMethodName,
                phantomAction,
                messageType);
          }

          if (phantomAction == ReplayAction.STUB_FROM_WAL
              || phantomAction == ReplayAction.STUB_FROM_WAL_VERIFIED
              || phantomAction == ReplayAction.STUB_WITH_SIDE_EFFECTS) {

            logger.info(
                "Phantom object stubbing: operation {}.{} at offset {} has missing target, "
                    + "returning WAL-recorded value (action={})",
                phantomClassName,
                phantomMethodName,
                phantomOffset,
                phantomAction);

            try {
              ExecMessage phantomResponse =
                  handlePhantomStub(incomingCall, phantomOffset, phantomAction);
              if (phantomResponse != null) {
                // Clear tracking and replay injection mode before returning
                if (trackingEnabled) {
                  inFlightDispatchTracker.exitDispatch(className, methodName, trackingParamTypes);
                }
                return phantomResponse;
              }
              // If handlePhantomStub returns null, fall through to normal error handling
            } catch (Throwable t) {
              logger.warn(
                  "Phantom stub handling failed for {}.{} at offset {}, "
                      + "falling back to error response: {}",
                  phantomClassName,
                  phantomMethodName,
                  phantomOffset,
                  t.getMessage());
              // Fall through to normal error handling
            }
          } else {
            // Non-stub action (e.g., RE_EXECUTE) but target doesn't exist.
            // We can't execute, but we must still advance past the entry point
            // so the injector doesn't wait forever.
            logger.warn(
                "Phantom object skip: operation {}.{} at offset {} has missing target and "
                    + "action={} (not stubbable), skipping entry point",
                phantomClassName,
                phantomMethodName,
                phantomOffset,
                phantomAction);

            Long completionOffset = replayContext.getWalIndex().getCompletionOffset(phantomOffset);
            if (completionOffset != null) {
              advancePhantomStub(phantomThreadName, phantomOffset, completionOffset);
            }

            // Return null response (void-like) since we can't execute
            if (trackingEnabled) {
              inFlightDispatchTracker.exitDispatch(className, methodName, trackingParamTypes);
            }
            ExecMessage skipResponse = new ExecMessage();
            skipResponse.setMessageId(incomingCall.getMessageId());
            skipResponse.setPeerUuid(incomingCall.getPeerUuid());
            return skipResponse;
          }
        }
      }

      // After the loading phase, the target class is initialized (static initializers have run).
      // Count down the injector ready latch so replay input injector threads can proceed.
      // This ensures class static initialization runs on the self-caller thread (matching
      // the recording) before any injector thread can trigger class loading.
      if (replayContext != null && runOptions.contains(RunOptions.WITH_REPLAY)) {
        replayContext.countDownInjectorLatch();
      }

      // Handle replay injection stubbing: even when loading succeeds (target exists), check if
      // the operation matches a stub pattern. If yes, return the WAL-recorded value instead of
      // executing. This is critical for operations like TextField.getText() where the target
      // exists but we need the recorded value, not the live (empty) value.
      if (isReplayInjection
          && exceptionWhileLoading == null
          && replayContext != null
          && runOptions.contains(RunOptions.WITH_REPLAY)) {

        String stubClassName = getClassname(incomingCall);
        String stubMethodName = getExecutableName(incomingCall);
        ReplayAction stubAction =
            replayContext.getPolicy().getAction(stubClassName, stubMethodName, messageType);

        if (stubAction == ReplayAction.STUB_FROM_WAL
            || stubAction == ReplayAction.STUB_FROM_WAL_VERIFIED
            || stubAction == ReplayAction.STUB_WITH_SIDE_EFFECTS) {

          // Get the pending injection offset for this entry point
          String walThreadName = incomingCall.getThreadName();
          long stubOffset = replayContext.popPendingInjection(walThreadName);

          if (stubOffset >= 0) {
            logger.info(
                "Replay injection stub: {}.{} at offset {} - returning WAL value instead of "
                    + "executing (action={})",
                stubClassName,
                stubMethodName,
                stubOffset,
                stubAction);

            try {
              ExecMessage stubResponse = handlePhantomStub(incomingCall, stubOffset, stubAction);
              if (stubResponse != null) {
                if (trackingEnabled) {
                  inFlightDispatchTracker.exitDispatch(className, methodName, trackingParamTypes);
                }
                return stubResponse;
              }
            } catch (Throwable t) {
              logger.warn(
                  "Replay injection stub failed for {}.{} at offset {}, falling back to execution: {}",
                  stubClassName,
                  stubMethodName,
                  stubOffset,
                  t.getMessage());
            }
          }
        }
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
        final ReplayCursor chainReplayCursor = replayEntryPointCursor;
        final ReplayContext chainReplayContext = replayContext;

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
                        () -> {
                          // For entry points, check if dispatchReplay already handled this entry
                          // point before we invoke. This prevents duplicate execution when both
                          // the injector and the real runtime race to execute the same entry point.
                          //
                          // We get the entry point offset from the pending injection queue that the
                          // ReplayInputInjector pushed BEFORE calling incomingCall(). This is the
                          // correct offset - using the cursor would be racy since the cursor may
                          // have been advanced by dispatchReplay on this thread.
                          long injectedOffsetForCompletion = -1;
                          if (chainReplayCursor != null && chainReplayContext != null) {
                            long injectedOffset =
                                chainReplayContext.popPendingInjection(
                                    Thread.currentThread().getName());
                            if (injectedOffset >= 0) {
                              boolean alreadyHandled =
                                  chainReplayContext.isEntryPointHandled(injectedOffset);
                              WalEntry currentEntry = chainReplayCursor.peekNext();
                              long currentOffset =
                                  (currentEntry != null) ? currentEntry.getOffset() : -1;
                              logger.info(
                                  "[dispatchIncoming-chain] injectedOffset={} alreadyHandled={}"
                                      + " cursorAt={}",
                                  injectedOffset,
                                  alreadyHandled,
                                  currentOffset);
                              if (alreadyHandled) {
                                // Entry point was already handled (skipped by dispatchReplay).
                                // Advance the gate to completion offset so the injector can
                                // proceed.
                                Long completionOffset =
                                    chainReplayContext
                                        .getWalIndex()
                                        .getCompletionOffset(injectedOffset);
                                if (completionOffset != null) {
                                  chainReplayContext.getReplayGate().advanceTo(completionOffset);
                                  logger.info(
                                      "[dispatchIncoming-chain] SKIP injectedOffset={}, "
                                          + "advanced gate to {} for injector",
                                      injectedOffset,
                                      completionOffset);
                                } else {
                                  logger.info(
                                      "[dispatchIncoming-chain] SKIP injectedOffset={} - race detected",
                                      injectedOffset);
                                }
                                return null;
                              }
                              // Entry point not yet handled - advance cursor and invoke
                              chainReplayCursor.advance();
                              injectedOffsetForCompletion = injectedOffset;
                            }
                          }
                          // Increment dispatch depth so nested woven operations inside the
                          // invoked method see depth > 0 and are not marked as entry points.
                          TL_DISPATCH_DEPTH.set(TL_DISPATCH_DEPTH.get() + 1);
                          Object invokeResult;
                          try {
                            invokeResult =
                                invokeIncoming(
                                    accessibleForChain, targetForChain, chainArgs, chainValue);
                          } finally {
                            TL_DISPATCH_DEPTH.set(TL_DISPATCH_DEPTH.get() - 1);
                          }

                          // After invoke completes, register the return value in
                          // ReplayObjectStore (critical for constructors — subsequent method
                          // calls need to find the object by WAL ref), then advance cursor
                          // past COMPLETION and gate.
                          if (injectedOffsetForCompletion >= 0
                              && chainReplayCursor != null
                              && chainReplayContext != null) {
                            Long completionOffset =
                                chainReplayContext
                                    .getWalIndex()
                                    .getCompletionOffset(injectedOffsetForCompletion);
                            if (completionOffset != null) {
                              registerEntryPointReturnValue(
                                  chainReplayContext, completionOffset, invokeResult, messageType);
                              chainReplayCursor.advancePast(completionOffset);
                              chainReplayContext.getReplayGate().advanceTo(completionOffset);
                              logger.info(
                                  "[dispatchIncoming-chain] Entry point {} completed, "
                                      + "advanced cursor/gate past completion {}",
                                  injectedOffsetForCompletion,
                                  completionOffset);
                            }
                          }
                          return invokeResult;
                        });
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
            final ReplayCursor directReplayCursor = replayEntryPointCursor;
            final ReplayContext directReplayContext = replayContext;
            returnValue =
                threadAffinityDispatcher.execute(
                    threadAffinity,
                    () -> {
                      // For entry points, check if dispatchReplay already handled this entry
                      // point before we invoke. This prevents duplicate execution when both
                      // the injector and the real runtime race to execute the same entry point.
                      //
                      // We get the entry point offset from the pending injection queue that the
                      // ReplayInputInjector pushed BEFORE calling incomingCall(). This is the
                      // correct offset - using the cursor would be racy since the cursor may
                      // have been advanced by dispatchReplay on this thread.
                      long injectedOffsetForCompletion = -1;
                      if (directReplayCursor != null && directReplayContext != null) {
                        long injectedOffset =
                            directReplayContext.popPendingInjection(
                                Thread.currentThread().getName());
                        if (injectedOffset >= 0) {
                          boolean alreadyHandled =
                              directReplayContext.isEntryPointHandled(injectedOffset);
                          WalEntry currentEntry = directReplayCursor.peekNext();
                          long currentOffset =
                              (currentEntry != null) ? currentEntry.getOffset() : -1;
                          logger.info(
                              "[dispatchIncoming-direct] injectedOffset={} alreadyHandled={}"
                                  + " cursorAt={}",
                              injectedOffset,
                              alreadyHandled,
                              currentOffset);
                          if (alreadyHandled) {
                            // Entry point was already handled (skipped by dispatchReplay).
                            // Advance the gate to completion offset so the injector can proceed.
                            Long completionOffset =
                                directReplayContext
                                    .getWalIndex()
                                    .getCompletionOffset(injectedOffset);
                            if (completionOffset != null) {
                              directReplayContext.getReplayGate().advanceTo(completionOffset);
                              logger.info(
                                  "[dispatchIncoming-direct] SKIP injectedOffset={}, "
                                      + "advanced gate to {} for injector",
                                  injectedOffset,
                                  completionOffset);
                            } else {
                              logger.info(
                                  "[dispatchIncoming-direct] SKIP injectedOffset={} - race detected",
                                  injectedOffset);
                            }
                            return null;
                          }
                          // Entry point not yet handled - advance cursor and invoke
                          directReplayCursor.advance();
                          injectedOffsetForCompletion = injectedOffset;
                        }
                      }
                      // Increment dispatch depth so nested woven operations inside the
                      // invoked method see depth > 0 and are not marked as entry points.
                      TL_DISPATCH_DEPTH.set(TL_DISPATCH_DEPTH.get() + 1);
                      Object invokeResult;
                      try {
                        invokeResult =
                            invokeIncoming(directAccessible, directTarget, directArgs, directValue);
                      } finally {
                        TL_DISPATCH_DEPTH.set(TL_DISPATCH_DEPTH.get() - 1);
                      }

                      // After invoke completes, register the return value in
                      // ReplayObjectStore (critical for constructors — subsequent method
                      // calls need to find the object by WAL ref), then advance cursor
                      // past COMPLETION and gate.
                      if (injectedOffsetForCompletion >= 0
                          && directReplayCursor != null
                          && directReplayContext != null) {
                        Long completionOffset =
                            directReplayContext
                                .getWalIndex()
                                .getCompletionOffset(injectedOffsetForCompletion);
                        if (completionOffset != null) {
                          registerEntryPointReturnValue(
                              directReplayContext, completionOffset, invokeResult, messageType);
                          directReplayCursor.advancePast(completionOffset);
                          directReplayContext.getReplayGate().advanceTo(completionOffset);
                          logger.info(
                              "[dispatchIncoming-direct] Entry point {} completed, "
                                  + "advanced cursor/gate past completion {}",
                              injectedOffsetForCompletion,
                              completionOffset);
                        }
                      }
                      return invokeResult;
                    });
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

        // 9. Save returnValue to peer's session (skip during replay - no session service)
        final boolean isReplayMode =
            replayContext != null && runOptions.contains(RunOptions.WITH_REPLAY);
        if (!isReplayMode && objectRef != null && incomingCall.getPeerUuid() != null) {
          try {
            final UUID peerUuid = UuidUtils.fromBytes(incomingCall.getPeerUuid());
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
      if (incomingInRecordingScope && shouldWriteIncomingToWal(messageChannel)) {
        finalAfterExecMsg.setEntryPoint(true);
        afterExecResponseMsg =
            messageGateway.sendExecMessage(messageBuilder.wrap(finalAfterExecMsg), ExecPhase.AFTER);
      } else {
        afterExecResponseMsg = finalAfterExecMsg;
      }

      // 12. During replay, advance the cursor past the entry-point COMPLETION entry and advance
      // the ReplayGate to the completion offset so other threads can proceed.
      //
      // Use the message's recorded thread name (not Thread.currentThread().getName()) for the
      // same reason as in step 7: JavaFX entry points may be injected from a differently-named
      // thread but the cursor is keyed to the WAL's recorded thread name.
      if (replayContext != null && runOptions.contains(RunOptions.WITH_REPLAY)) {
        String walThreadName = incomingCall.getThreadName();
        ReplayCursor cursor = replayContext.getCursor(walThreadName);
        WalEntry peeked = cursor.peekNext();
        if (peeked != null && peeked.getKind() == WalEntryKind.COMPLETION) {
          // Register return value in ReplayObjectStore with its WAL ref. This is critical for
          // constructors: the constructed object needs to be registered so subsequent method
          // calls on it can find the target object by WAL ref.
          if (returnValue != null) {
            int walRef = extractReturnRef(peeked);
            if (walRef != 0) {
              replayContext.getObjectStore().register(walRef, returnValue);
              if (logger.isDebugEnabled()) {
                logger.debug(
                    "Registered entry-point return value in ReplayObjectStore: {} -> {}",
                    walRef,
                    returnValue.getClass().getName());
              }
            }
          }

          long completionOffset = peeked.getOffset();
          cursor.advance(); // skip past the entry-point COMPLETION entry
          replayContext.getReplayGate().advanceTo(completionOffset);
        }
      }

      // 13. Return received message
      return afterExecResponseMsg;
    } finally {
      // Always exit dispatch tracking, even if an exception occurred
      if (trackingEnabled) {
        inFlightDispatchTracker.exitDispatch(className, methodName, trackingParamTypes);
      }
    }
  }

  /**
   * Returns the {@link ObjectRef} for the given object, storing it if not already present. Refs are
   * monotonically generated by the {@link ObjectLookupStore}; the same object (by identity) always
   * returns the same ref.
   *
   * @param object the object for which to obtain a reference; may be {@code null}
   * @return the {@link ObjectRef} associated with the object, or {@code null} if object is null
   */
  protected ObjectRef generateObjectRef(Object object) {
    return object != null ? objectLookupStore.storeObject(object) : null;
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
    List<Obj> argsList = getArgsList(execMessage);

    if (messageType.equals(MessageType.EXEC_CONSTRUCTOR)
        || messageType.equals(MessageType.EXEC_CLASS_METHOD)
        || messageType.equals(MessageType.EXEC_INSTANCE_METHOD)) {
      for (Obj obj : argsList) {
        if (obj.getClazz() == null || obj.getClazz().getName().isEmpty()) {
          paramClasses.add(null);
        } else {
          Class<?> primitiveClass = Classes.getClassForPrimitive(obj.getClazz().getName());
          if (primitiveClass != null) { // param is primitive
            paramClasses.add(primitiveClass);
          } else { // i.e. not a primitive
            paramClasses.add(
                Class.forName(
                    obj.getClazz().getName(),
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
    final List<Obj> argsList = getArgsList(execMessage);

    // Check if we're in replay mode and should use ReplayObjectStore
    final boolean useReplayObjectStore =
        replayContext != null && runOptions.contains(RunOptions.WITH_REPLAY);

    int i = 0;
    if (argsList != null) {
      for (Obj obj : argsList) {
        if (obj.getIsNull()) {
          args.add(new MessageArgument(null, true));
        } else {
          Object lookedUpObj = null;
          final int objRef = obj.getRef();
          if (objRef != 0) {
            if (useReplayObjectStore) {
              // In replay mode, look up from ReplayObjectStore which maps WAL refs to live objects
              lookedUpObj = replayContext.getObjectStore().resolveOrNull(objRef);
            } else {
              // Normal RPC mode: fetch from objectLookupStore
              lookedUpObj = objectLookupStore.lookupObject(ObjectRef.from(objRef));
            }
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
   * Extracts the list of args from the given execution message.
   *
   * <p>This method should return the args that will be used to determine argument types and values
   * during the loading phase.
   *
   * @param execMessage the execution message containing the args
   * @return a list of Obj instances extracted from the execution message
   */
  protected abstract List<Obj> getArgsList(ExecMessage execMessage);

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
   * <p><b>Note:</b> Recording scope filtering is applied at the call sites in {@code
   * dispatchIncoming()}, not in this method. This keeps the channel-matrix logic testable in
   * isolation.
   *
   * @param messageChannel the transport channel through which the message was received
   * @return true if the incoming message should be written to WAL/PUB
   */
  private boolean shouldWriteIncomingToWal(MessageChannelType messageChannel) {
    // During replay, incoming messages are being replayed from the WAL — do not write them back.
    if (runOptions.contains(RunOptions.WITH_REPLAY)) {
      return false;
    }
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
