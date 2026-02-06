/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptApiMisuseException;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the chained execution of AROUND intercepts following the onion model.
 *
 * <p>The chain processes intercepts in order:
 *
 * <ol>
 *   <li>Local AROUND intercepts (in registration order) - outermost layers
 *   <li>Remote AROUND intercepts (in registration order) - inner layers
 *   <li>Method execution - innermost
 * </ol>
 *
 * <p>Each layer's {@code proceed()} invokes the next layer, not the method directly. Only when the
 * chain is exhausted does proceed() trigger actual method execution.
 *
 * <h2>Arg Mutation Propagation</h2>
 *
 * <p>Each layer can mutate args before calling proceed(). The mutated args are passed to the next
 * layer. This creates inward propagation:
 *
 * <pre>
 * Layer-1: args=[1,2] → mutate arg[0]=10 → proceed([10,2])
 *   Layer-2: args=[10,2] → mutate arg[1]=20 → proceed([10,20])
 *     Method executes with args=[10,20]
 * </pre>
 *
 * <h2>Return Value Propagation</h2>
 *
 * <p>Each layer can modify the return value after proceed() returns. This creates outward
 * propagation:
 *
 * <pre>
 *     Method returns 100
 *   Layer-2: sees 100, overrides to 200, returns 200
 * Layer-1: sees 200, keeps it, returns 200
 * </pre>
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification =
        "Chain is built and used within a single request, no external mutation expected")
public class AroundInterceptChain {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(AroundInterceptChain.class);

  /**
   * Sealed interface for AROUND intercept handles in the chain. Each handle represents either a
   * local or remote intercept.
   */
  public sealed interface AroundHandle permits LocalAroundHandle, RemoteAroundHandle {}

  /**
   * Handle for a local AROUND intercept (callback runs in same JVM).
   *
   * @param intercept the intercept message containing metadata
   * @param callback the resolved callback instance
   * @param className the intercepted class name
   * @param methodName the intercepted method/field name
   * @param paramTypes the parameter type names
   * @param peerUuid the intercepted peer's UUID (same as callback peer for local)
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Internal DTO, intentional exposure for chain processing")
  public record LocalAroundHandle(
      InterceptMessage intercept,
      InterceptCallback callback,
      String className,
      String methodName,
      List<String> paramTypes,
      String peerUuid)
      implements AroundHandle {}

  /**
   * Handle for a remote AROUND intercept (callback runs on different peer).
   *
   * @param intercept the intercept message containing metadata
   * @param callbackPeerUuid the UUID of the peer that will handle the callback
   * @param callbackId unique ID for correlating BEFORE/AFTER phases
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Internal DTO, intentional exposure for chain processing")
  public record RemoteAroundHandle(
      InterceptMessage intercept, UUID callbackPeerUuid, String callbackId)
      implements AroundHandle {}

  /** Functional interface for invoking the actual method at the end of the chain. */
  @FunctionalInterface
  public interface MethodInvoker {
    /**
     * Invokes the actual method with the given arguments.
     *
     * @param args the arguments (possibly mutated by the chain)
     * @return the result of method execution
     */
    AfterPhaseData invoke(Object[] args);
  }

  /**
   * Interface for dispatching remote AROUND intercept requests. Implementations handle the network
   * communication with callback peers.
   */
  public interface RemoteAroundDispatcher {
    /**
     * Sends the BEFORE phase request to the remote callback peer.
     *
     * @param handle the remote intercept handle
     * @param execMessage the execution message (for context)
     * @param args the current arguments
     * @return the result of the BEFORE phase
     */
    RemoteAroundBeforeResult sendBefore(
        RemoteAroundHandle handle, ExecMessage execMessage, Object[] args);

    /**
     * Sends the AFTER phase request to the remote callback peer.
     *
     * @param handle the remote intercept handle
     * @param execMessage the execution message
     * @param returnValue the return value from inner layers
     * @param isVoid whether the method is void
     * @param thrownException exception from inner layers (may be null)
     * @return the result of the AFTER phase
     */
    RemoteAroundAfterResult sendAfter(
        RemoteAroundHandle handle,
        ExecMessage execMessage,
        Object returnValue,
        boolean isVoid,
        Throwable thrownException);
  }

  /**
   * Result from remote AROUND BEFORE phase.
   *
   * @param shouldProceed whether to continue with execution
   * @param argMutations map of argument index to mutated value
   * @param skipReturnValue return value if skipping (shouldProceed=false)
   * @param exceptionToThrow exception to throw (if any)
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Internal DTO, intentional exposure for chain processing")
  public record RemoteAroundBeforeResult(
      boolean shouldProceed,
      Map<Integer, Object> argMutations,
      @Nullable Object skipReturnValue,
      @Nullable Throwable exceptionToThrow) {}

  /**
   * Result from remote AROUND AFTER phase.
   *
   * @param hasReturnOverride whether the callback overrode the return value
   * @param overriddenReturn the overridden return value (if hasReturnOverride)
   * @param exceptionToThrow exception to throw (if any)
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Internal DTO, intentional exposure for chain processing")
  public record RemoteAroundAfterResult(
      boolean hasReturnOverride,
      @Nullable Object overriddenReturn,
      @Nullable Throwable exceptionToThrow) {}

  /**
   * Result of the entire chain invocation.
   *
   * @param returnValue the final return value
   * @param thrownException exception if method/callback threw
   * @param isVoid whether the method is void
   * @param methodWasInvoked whether the actual method was invoked (false if skipped)
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Internal DTO, intentional exposure for chain processing")
  public record ChainResult(
      @Nullable Object returnValue,
      @Nullable Throwable thrownException,
      boolean isVoid,
      boolean methodWasInvoked) {

    /**
     * Creates a result for when the chain was skipped (method not invoked).
     *
     * @param returnValue the skip return value
     * @param exception the exception (if any)
     * @return the chain result
     */
    public static ChainResult skipped(Object returnValue, Throwable exception) {
      return new ChainResult(returnValue, exception, false, false);
    }

    /**
     * Creates a result for when the method was executed.
     *
     * @param returnValue the return value
     * @param exception the exception (if any)
     * @param isVoid whether the method is void
     * @return the chain result
     */
    public static ChainResult executed(Object returnValue, Throwable exception, boolean isVoid) {
      return new ChainResult(returnValue, exception, isVoid, true);
    }
  }

  /** The ordered list of AROUND handles (local first, then remote). */
  private final List<AroundHandle> chain;

  /** The method invoker for the innermost layer. */
  private final MethodInvoker methodInvoker;

  /** The dispatcher for remote AROUND callbacks. */
  private final RemoteAroundDispatcher remoteDispatcher;

  /**
   * Constructs a new AROUND intercept chain.
   *
   * @param chain ordered list of AROUND handles (local first, then remote)
   * @param methodInvoker the invoker for the actual method
   * @param remoteDispatcher dispatcher for remote intercept communication
   */
  public AroundInterceptChain(
      List<AroundHandle> chain,
      MethodInvoker methodInvoker,
      RemoteAroundDispatcher remoteDispatcher) {
    this.chain = chain;
    this.methodInvoker = methodInvoker;
    this.remoteDispatcher = remoteDispatcher;
  }

  /**
   * Returns true if the chain is empty (no AROUND intercepts).
   *
   * @return true if no intercepts in chain
   */
  public boolean isEmpty() {
    return chain.isEmpty();
  }

  /**
   * Invokes the AROUND chain starting from the first intercept.
   *
   * @param args the initial method arguments
   * @param execMessage the execution message (for remote callbacks)
   * @return the result of chain execution
   */
  public ChainResult invoke(Object[] args, @Nullable ExecMessage execMessage) {
    if (chain.isEmpty()) {
      // No AROUND intercepts - invoke method directly
      AfterPhaseData result = methodInvoker.invoke(args);
      return ChainResult.executed(result.returnValue(), result.thrownException(), result.isVoid());
    }

    try {
      AfterPhaseData result = invokeAt(0, args, execMessage);
      return ChainResult.executed(result.returnValue(), result.thrownException(), result.isVoid());
    } catch (SkipExecutionException e) {
      return ChainResult.skipped(e.returnValue, e.skipException);
    } catch (Throwable t) {
      return ChainResult.executed(null, t, false);
    }
  }

  /**
   * Invokes the chain starting at the specified index.
   *
   * @param index the current position in the chain
   * @param args the current arguments (may have been mutated)
   * @param execMessage the execution message
   * @return the result from this layer and all inner layers
   * @throws SkipExecutionException if a layer decides to skip
   * @throws Throwable if an error occurs
   */
  private AfterPhaseData invokeAt(int index, Object[] args, ExecMessage execMessage)
      throws Throwable {
    if (index >= chain.size()) {
      // End of chain - invoke the actual method
      if (logger.isDebugEnabled()) {
        logger.debug("Chain exhausted at index {}, invoking method", index);
      }
      return methodInvoker.invoke(args);
    }

    AroundHandle handle = chain.get(index);

    if (handle instanceof LocalAroundHandle local) {
      return invokeLocal(index, local, args, execMessage);
    } else if (handle instanceof RemoteAroundHandle remote) {
      return invokeRemote(index, remote, args, execMessage);
    } else {
      throw new IllegalStateException("Unknown handle type: " + handle.getClass());
    }
  }

  /**
   * Invokes a local AROUND intercept in the chain.
   *
   * <p>This method executes the callback handler and processes the result. If the callback throws
   * an exception, the following behavior applies:
   *
   * <ul>
   *   <li>{@link SkipExecutionException}: Re-thrown to propagate skip signal through chain
   *   <li>{@link InterceptApiMisuseException}: Always propagated (bypasses exception policy) since
   *       these indicate programming errors in the callback handler that should be immediately
   *       visible to developers
   *   <li>Other exceptions: Stored in {@link AfterPhaseData} for downstream handling
   * </ul>
   *
   * @param index the current position in the chain
   * @param handle the local AROUND handle containing callback info
   * @param args the current arguments (may have been mutated by outer layers)
   * @param execMessage the execution message for context
   * @return the result from this layer and all inner layers
   * @throws SkipExecutionException if a layer decides to skip execution
   * @throws InterceptApiMisuseException if the callback handler misuses the intercept API
   */
  private AfterPhaseData invokeLocal(
      int index, LocalAroundHandle handle, Object[] args, ExecMessage execMessage) {

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoking local AROUND at index {}: {}.{}",
          index,
          handle.className(),
          handle.methodName());
    }

    // Create accessor that continues the chain (not direct method invocation!)
    // IMPORTANT: This accessor implements proceed() for AROUND intercepts
    LocalAroundAccessor chainAccessor =
        (modifiedArgs) -> {
          Object[] nextArgs = modifiedArgs != null ? modifiedArgs : args;
          try {
            return invokeAt(index + 1, nextArgs, execMessage);
          } catch (SkipExecutionException e) {
            // FIX: When an inner layer skips, return the skip value as a normal result.
            // This allows the outer callback to complete its AFTER logic.
            // Previously, we re-threw the exception, which prevented outer callbacks from
            // executing their cleanup/after code. See AroundChainIT.testAroundSkipInMiddleOfChain.
            return new AfterPhaseData(e.returnValue, e.skipException, false);
          } catch (Throwable t) {
            return new AfterPhaseData(null, t, false);
          }
        };

    // Create context for this callback
    InterceptContext context =
        InterceptContext.forLocalAroundPhase(
            handle.className(), handle.methodName(), handle.paramTypes(), handle.peerUuid(), args);
    context.setLocalAroundAccessor(chainAccessor);

    // Invoke the callback
    InterceptCallbackResponse response;
    try {
      response = handle.callback().handle(context);
    } catch (InvocationTargetException e) {
      // FIX: Callbacks are invoked via reflection (CallbackResolver uses Method.invoke()),
      // which wraps all thrown exceptions in InvocationTargetException. We need to unwrap
      // the cause for proper exception handling downstream.
      // - SkipExecutionException: re-throw to propagate skip signal through chain
      // - InterceptApiMisuseException: always propagate (bypasses policy)
      // - Other exceptions: store unwrapped cause in AfterPhaseData
      // See AroundChainIT.testAroundSkipInMiddleOfChain.
      Throwable cause = e.getCause();
      if (cause instanceof SkipExecutionException skipExecutionException) {
        throw skipExecutionException;
      }
      // API misuse exceptions always propagate (bypass policy)
      if (cause instanceof InterceptApiMisuseException interceptApiMisuseException) {
        logger.error("API misuse exception from AROUND callback: {}", cause.getMessage());
        throw interceptApiMisuseException; // Propagate by re-throwing
      }
      // Store unwrapped exception for other cases
      return new AfterPhaseData(null, cause != null ? cause : e, false);
    } catch (SkipExecutionException e) {
      // Re-throw skip signal to propagate through chain (in case not invoked via reflection)
      throw e;
    } catch (Exception e) {
      return new AfterPhaseData(null, e, false);
    }

    // Check for exception from callback
    Throwable exToThrow = context.getExceptionToThrow();
    if (exToThrow == null) {
      exToThrow = response.getExceptionToThrow();
    }
    if (exToThrow != null) {
      return new AfterPhaseData(null, exToThrow, context.isVoid());
    }

    // Check if proceed() was called
    if (!context.isProceedCalled()) {
      // Callback decided to skip - validate that return value or exception was set
      Throwable skipException = context.getExceptionToThrow();
      if (!context.isReturnValueModified() && skipException == null && !context.isVoid()) {
        throw new IllegalStateException(
            "AROUND callback skipped proceed() without setting return value. "
                + "Call ctx.setReturnValue() or ctx.setExceptionToThrow() before skipping. "
                + "Use ctx.setReturnValue(null) for explicit null.");
      }
      // Signal skip to outer layers
      throw new SkipExecutionException(context.getReturnValueInternal(), skipException);
    }

    // proceed() was called - get result from context (set by accessor via chain)
    Object returnValue = context.getReturnValueInternal();
    Throwable thrownException = context.getThrownException();

    // Check if callback modified return value after proceed()
    if (context.isReturnValueModified()) {
      returnValue = context.getReturnValueInternal();
      // If callback set a return value after proceed() and there was an exception,
      // the callback has "handled" the exception - don't propagate it
      if (thrownException != null) {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Callback modified return value after exception, treating as suppressed: {}",
              thrownException.getClass().getSimpleName());
        }
        thrownException = null;
      }
    }

    return new AfterPhaseData(returnValue, thrownException, context.isVoid());
  }

  /** Invokes a remote AROUND intercept in the chain. */
  private AfterPhaseData invokeRemote(
      int index, RemoteAroundHandle handle, Object[] args, ExecMessage execMessage)
      throws Throwable {

    if (logger.isDebugEnabled()) {
      logger.debug("Invoking remote AROUND at index {}: callbackId={}", index, handle.callbackId());
    }

    // Send BEFORE phase request to remote peer
    RemoteAroundBeforeResult beforeResult = remoteDispatcher.sendBefore(handle, execMessage, args);

    // Check for exception
    if (beforeResult.exceptionToThrow() != null) {
      throw beforeResult.exceptionToThrow();
    }

    // Check if remote callback decided to skip
    if (!beforeResult.shouldProceed()) {
      if (logger.isDebugEnabled()) {
        logger.debug("Remote AROUND at index {} skipped proceed()", index);
      }
      throw new SkipExecutionException(beforeResult.skipReturnValue(), null);
    }

    // Apply arg mutations and continue chain
    Object[] nextArgs = applyMutations(args, beforeResult.argMutations());
    AfterPhaseData innerResult;
    try {
      innerResult = invokeAt(index + 1, nextArgs, execMessage);
    } catch (SkipExecutionException e) {
      // Inner layer skipped - still need to send AFTER to this remote
      innerResult = new AfterPhaseData(e.returnValue, e.skipException, false);
    }

    // Send AFTER phase request to remote peer
    RemoteAroundAfterResult afterResult =
        remoteDispatcher.sendAfter(
            handle,
            execMessage,
            innerResult.returnValue(),
            innerResult.isVoid(),
            innerResult.thrownException());

    // Check for exception from AFTER phase
    if (afterResult.exceptionToThrow() != null) {
      return new AfterPhaseData(null, afterResult.exceptionToThrow(), innerResult.isVoid());
    }

    // Apply return value override if any
    Object finalReturn =
        afterResult.hasReturnOverride()
            ? afterResult.overriddenReturn()
            : innerResult.returnValue();

    return new AfterPhaseData(finalReturn, innerResult.thrownException(), innerResult.isVoid());
  }

  /** Applies argument mutations to a copy of the args array. */
  private Object[] applyMutations(Object[] args, Map<Integer, Object> mutations) {
    if (mutations == null || mutations.isEmpty()) {
      return args;
    }
    Object[] result = args.clone();
    for (Map.Entry<Integer, Object> entry : mutations.entrySet()) {
      int idx = entry.getKey();
      if (idx >= 0 && idx < result.length) {
        result[idx] = entry.getValue();
      }
    }
    return result;
  }

  /** Internal runtime exception to signal skip through the chain. */
  private static class SkipExecutionException extends RuntimeException {
    /** The return value to use when skipping. */
    final Object returnValue;

    /** The exception to throw (if any). */
    final Throwable skipException;

    /**
     * Constructs a skip exception.
     *
     * @param returnValue the return value
     * @param skipException the exception
     */
    SkipExecutionException(Object returnValue, Throwable skipException) {
      super(null, null, false, false); // No stack trace needed
      this.returnValue = returnValue;
      this.skipException = skipException;
    }
  }

  // ========== Builder for constructing the chain ==========

  /** Builder for constructing an AroundInterceptChain. */
  public static class Builder {
    /** The list of handles being built. */
    private final List<AroundHandle> handles = new ArrayList<>();

    /** The method invoker. */
    private MethodInvoker methodInvoker;

    /** The remote dispatcher. */
    private RemoteAroundDispatcher remoteDispatcher;

    /**
     * Adds a local AROUND handle to the chain.
     *
     * @param intercept the intercept message
     * @param callback the callback instance
     * @param className the class name
     * @param methodName the method name
     * @param paramTypes the parameter types
     * @param peerUuid the peer UUID
     * @return this builder
     */
    public Builder addLocal(
        InterceptMessage intercept,
        InterceptCallback callback,
        String className,
        String methodName,
        List<String> paramTypes,
        String peerUuid) {
      handles.add(
          new LocalAroundHandle(intercept, callback, className, methodName, paramTypes, peerUuid));
      return this;
    }

    /**
     * Adds a remote AROUND handle to the chain.
     *
     * @param intercept the intercept message
     * @param callbackPeerUuid the callback peer UUID
     * @return this builder
     */
    public Builder addRemote(InterceptMessage intercept, UUID callbackPeerUuid) {
      String callbackId = UUID.randomUUID().toString();
      handles.add(new RemoteAroundHandle(intercept, callbackPeerUuid, callbackId));
      return this;
    }

    /**
     * Sets the method invoker.
     *
     * @param invoker the method invoker
     * @return this builder
     */
    public Builder methodInvoker(MethodInvoker invoker) {
      this.methodInvoker = invoker;
      return this;
    }

    /**
     * Sets the remote dispatcher.
     *
     * @param dispatcher the remote dispatcher
     * @return this builder
     */
    public Builder remoteDispatcher(RemoteAroundDispatcher dispatcher) {
      this.remoteDispatcher = dispatcher;
      return this;
    }

    /**
     * Builds the chain.
     *
     * @return the built chain
     */
    public AroundInterceptChain build() {
      if (methodInvoker == null) {
        throw new IllegalStateException("methodInvoker is required");
      }
      return new AroundInterceptChain(handles, methodInvoker, remoteDispatcher);
    }
  }

  /**
   * Creates a new builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }
}
