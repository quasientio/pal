/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import com.quasient.pal.common.lang.intercept.InterceptCallback;
import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import com.quasient.pal.messages.colfer.InterceptMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches local intercept callbacks within the same JVM.
 *
 * <p>Local intercepts are those where the callback peer UUID matches the intercepted peer UUID,
 * meaning the callback handler runs in the same JVM as the intercepted code. This enables:
 *
 * <ul>
 *   <li><b>No serialization:</b> Arguments and return values are live Java objects
 *   <li><b>No network latency:</b> Direct method invocation (~1μs vs ~1ms for remote)
 *   <li><b>Same heap:</b> No ObjectRef translation needed
 *   <li><b>Simpler error handling:</b> No timeouts or network errors
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Callback handlers must also be thread-safe,
 * as they may be invoked concurrently for different intercept requests.
 *
 * @see InterceptCallbackDispatcher for remote intercept handling
 * @see CallbackResolver for callback resolution
 */
@Singleton
public class LocalInterceptCallbackDispatcher {

  /** Logger instance for debugging local callback dispatch operations. */
  private static final Logger logger =
      LoggerFactory.getLogger(LocalInterceptCallbackDispatcher.class);

  /** Shared callback resolver for looking up registered callbacks and resolving static methods. */
  private final CallbackResolver callbackResolver;

  /** Executor service for running async intercept callbacks in the background. */
  private final ExecutorService asyncExecutor;

  /**
   * Constructs a new LocalInterceptCallbackDispatcher.
   *
   * @param callbackResolver the shared callback resolver
   * @param asyncExecutor the executor service for async callbacks
   */
  @Inject
  public LocalInterceptCallbackDispatcher(
      CallbackResolver callbackResolver,
      @Named("intercept.async.executor") ExecutorService asyncExecutor) {
    this.callbackResolver = callbackResolver;
    this.asyncExecutor = asyncExecutor;
  }

  // ---- BEFORE callbacks (synchronous) ----

  /**
   * Sends local BEFORE callbacks and returns the aggregated response.
   *
   * <p>Invokes each matching BEFORE callback in order. Callbacks can:
   *
   * <ul>
   *   <li>Mutate arguments via {@link InterceptContext#setArg(int, Object)}
   *   <li>Throw an exception via {@link InterceptContext#setExceptionToThrow(Throwable)}
   * </ul>
   *
   * <p>Processing stops early if any callback sets an exception to throw.
   *
   * @param localIntercepts the list of local BEFORE intercepts
   * @param args the method arguments (live Java objects)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer (same as callback peer)
   * @return the consolidated response with mutations and exceptions
   */
  public InterceptCallbackDispatcher.ConsolidatedCallbackResponse sendLocalBeforeCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid) {

    if (localIntercepts.isEmpty()) {
      return InterceptCallbackDispatcher.ConsolidatedCallbackResponse.proceed();
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending {} local BEFORE callbacks for {}.{}",
          localIntercepts.size(),
          className,
          methodName);
    }

    Map<Integer, Object> aggregatedMutations = new HashMap<>();
    Throwable exceptionToThrow = null;

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-BEFORE intercepts (they shouldn't be here, but defensive)
      if (interceptType != InterceptType.BEFORE) {
        continue;
      }

      try {
        // Resolve the callback (InterceptMessage only has callbackClass/callbackMethod, no
        // registered ID)
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create context for BEFORE phase
        InterceptContext context =
            InterceptContext.forLocalBeforePhase(
                className, methodName, paramTypes, interceptType, interceptedPeerUuid, args);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);

        // Aggregate argument mutations
        if (context.isArgsModified()) {
          Object[] modifiedArgs = context.getArgsInternal();
          if (modifiedArgs != null) {
            for (int i = 0; i < modifiedArgs.length; i++) {
              aggregatedMutations.put(i, modifiedArgs[i]);
            }
          }
        }

        // Check for exception (from context or response)
        exceptionToThrow = context.getExceptionToThrow();
        if (exceptionToThrow == null) {
          exceptionToThrow = response.getExceptionToThrow();
        }

        // Stop processing on exception
        if (exceptionToThrow != null) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Local BEFORE callback requested exception: {}",
                exceptionToThrow.getClass().getName());
          }
          break;
        }

      } catch (Exception e) {
        logger.error(
            "Error invoking local BEFORE callback: class={}, method={}",
            intercept.getCallbackClass(),
            intercept.getCallbackMethod(),
            e);
        // Treat callback errors as exceptions to throw
        exceptionToThrow = e;
        break;
      }
    }

    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        exceptionToThrow == null, aggregatedMutations, exceptionToThrow);
  }

  // ---- BEFORE_ASYNC callbacks (fire-and-forget) ----

  /**
   * Sends local BEFORE_ASYNC callbacks asynchronously (fire-and-forget).
   *
   * <p>Invokes each matching BEFORE_ASYNC callback in a background thread. Unlike synchronous
   * BEFORE callbacks, async callbacks:
   *
   * <ul>
   *   <li>Cannot mutate arguments (changes would not be visible to execution)
   *   <li>Cannot throw exceptions to block execution
   *   <li>Do not block the calling thread
   * </ul>
   *
   * <p>This is useful for non-blocking side effects such as:
   *
   * <ul>
   *   <li>Logging/auditing
   *   <li>Metrics collection
   *   <li>Event notification
   * </ul>
   *
   * @param localIntercepts the list of local BEFORE_ASYNC intercepts
   * @param args the method arguments (live Java objects, immutable view)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer
   */
  public void sendLocalBeforeAsyncCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid) {

    if (localIntercepts.isEmpty()) {
      return;
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending {} local BEFORE_ASYNC callbacks for {}.{}",
          localIntercepts.size(),
          className,
          methodName);
    }

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-BEFORE_ASYNC intercepts
      if (interceptType != InterceptType.BEFORE_ASYNC) {
        continue;
      }

      // Submit callback to executor (fire-and-forget, Future result intentionally ignored)
      submitAsyncBeforeCallback(
          intercept, args, className, methodName, paramTypes, interceptType, interceptedPeerUuid);
    }
  }

  /**
   * Submits an async BEFORE callback to the executor.
   *
   * <p>This method is extracted to satisfy spotbugs dead local store warnings when ignoring the
   * Future return value intentionally for fire-and-forget semantics.
   *
   * @param intercept the intercept message
   * @param args the method arguments
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes the parameter types
   * @param interceptType the intercept type
   * @param interceptedPeerUuid the UUID of the intercepted peer
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
      justification = "Fire-and-forget async callback - Future intentionally ignored")
  private void submitAsyncBeforeCallback(
      InterceptMessage intercept,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      InterceptType interceptType,
      String interceptedPeerUuid) {
    asyncExecutor.submit(
        () -> {
          try {
            // Resolve the callback
            InterceptCallback callback =
                callbackResolver.resolve(
                    null, intercept.getCallbackClass(), intercept.getCallbackMethod());

            // Create context for BEFORE phase (no mutations allowed for async)
            InterceptContext context =
                InterceptContext.forLocalBeforePhase(
                    className, methodName, paramTypes, interceptType, interceptedPeerUuid, args);

            // Invoke the callback (response is ignored for fire-and-forget)
            callback.handle(context);

            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Local BEFORE_ASYNC callback completed: class={}, method={}",
                  intercept.getCallbackClass(),
                  intercept.getCallbackMethod());
            }

          } catch (Exception e) {
            logger.error(
                "Error invoking local BEFORE_ASYNC callback: class={}, method={}",
                intercept.getCallbackClass(),
                intercept.getCallbackMethod(),
                e);
            // Swallow exception - async callbacks are fire-and-forget
          }
        });
  }

  // ---- AFTER callbacks (synchronous) ----

  /**
   * Sends local AFTER callbacks and returns the aggregated response.
   *
   * <p>Invokes each matching AFTER callback in order. Callbacks can:
   *
   * <ul>
   *   <li>Override the return value via {@link InterceptContext#setReturnValue(Object)}
   *   <li>Throw an exception via {@link InterceptContext#setExceptionToThrow(Throwable)}
   * </ul>
   *
   * @param localIntercepts the list of local AFTER intercepts
   * @param args the method arguments (from BEFORE phase, possibly modified)
   * @param returnValue the original return value (may be null)
   * @param isVoid whether the method is void
   * @param thrownException the exception thrown by the method (may be null)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer
   * @return the consolidated response with return value override and exceptions
   */
  public InterceptCallbackDispatcher.ConsolidatedCallbackResponse sendLocalAfterCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid) {

    if (localIntercepts.isEmpty()) {
      return InterceptCallbackDispatcher.ConsolidatedCallbackResponse.proceed();
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending {} local AFTER callbacks for {}.{}",
          localIntercepts.size(),
          className,
          methodName);
    }

    Object currentReturnValue = returnValue;
    boolean returnValueOverridden = false;
    Throwable exceptionToThrow = null;

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-AFTER intercepts
      if (interceptType != InterceptType.AFTER) {
        continue;
      }

      try {
        // Resolve the callback (InterceptMessage only has callbackClass/callbackMethod, no
        // registered ID)
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create context for AFTER phase
        InterceptContext context =
            InterceptContext.forLocalAfterPhase(
                className,
                methodName,
                paramTypes,
                interceptType,
                interceptedPeerUuid,
                args,
                currentReturnValue,
                isVoid,
                thrownException);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);

        // Check for return value override
        if (context.isReturnValueModified()) {
          currentReturnValue = context.getReturnValue();
          returnValueOverridden = true;
        }

        // Check for exception (from context or response)
        exceptionToThrow = context.getExceptionToThrow();
        if (exceptionToThrow == null) {
          exceptionToThrow = response.getExceptionToThrow();
        }

        // Stop processing on exception
        if (exceptionToThrow != null) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Local AFTER callback requested exception: {}",
                exceptionToThrow.getClass().getName());
          }
          break;
        }

      } catch (Exception e) {
        logger.error(
            "Error invoking local AFTER callback: class={}, method={}",
            intercept.getCallbackClass(),
            intercept.getCallbackMethod(),
            e);
        exceptionToThrow = e;
        break;
      }
    }

    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        true, new HashMap<>(), exceptionToThrow, currentReturnValue, returnValueOverridden);
  }

  // ---- AFTER_ASYNC callbacks (fire-and-forget) ----

  /**
   * Sends local AFTER_ASYNC callbacks asynchronously (fire-and-forget).
   *
   * <p>Invokes each matching AFTER_ASYNC callback in a background thread. Unlike synchronous AFTER
   * callbacks, async callbacks:
   *
   * <ul>
   *   <li>Cannot override the return value (changes would not be visible to caller)
   *   <li>Cannot throw exceptions to replace the result
   *   <li>Do not block the calling thread
   * </ul>
   *
   * <p>This is useful for non-blocking side effects such as:
   *
   * <ul>
   *   <li>Logging/auditing (including return value and execution time)
   *   <li>Metrics collection (e.g., success/failure counts)
   *   <li>Event notification (e.g., publishing completion events)
   *   <li>Async cache population
   * </ul>
   *
   * @param localIntercepts the list of local AFTER_ASYNC intercepts
   * @param args the method arguments (from BEFORE phase, possibly modified)
   * @param returnValue the original return value (may be null)
   * @param isVoid whether the method is void
   * @param thrownException the exception thrown by the method (may be null)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer
   */
  public void sendLocalAfterAsyncCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid) {

    if (localIntercepts.isEmpty()) {
      return;
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending {} local AFTER_ASYNC callbacks for {}.{}",
          localIntercepts.size(),
          className,
          methodName);
    }

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-AFTER_ASYNC intercepts
      if (interceptType != InterceptType.AFTER_ASYNC) {
        continue;
      }

      // Submit callback to executor (fire-and-forget, Future result intentionally ignored)
      submitAsyncAfterCallback(
          intercept,
          args,
          returnValue,
          isVoid,
          thrownException,
          className,
          methodName,
          paramTypes,
          interceptType,
          interceptedPeerUuid);
    }
  }

  /**
   * Submits an async AFTER callback to the executor.
   *
   * <p>This method is extracted to satisfy spotbugs dead local store warnings when ignoring the
   * Future return value intentionally for fire-and-forget semantics.
   *
   * @param intercept the intercept message
   * @param args the method arguments
   * @param returnValue the return value (may be null)
   * @param isVoid whether the method is void
   * @param thrownException exception thrown (may be null)
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes the parameter types
   * @param interceptType the intercept type
   * @param interceptedPeerUuid the UUID of the intercepted peer
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
      justification = "Fire-and-forget async callback - Future intentionally ignored")
  private void submitAsyncAfterCallback(
      InterceptMessage intercept,
      Object[] args,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException,
      String className,
      String methodName,
      List<String> paramTypes,
      InterceptType interceptType,
      String interceptedPeerUuid) {
    asyncExecutor.submit(
        () -> {
          try {
            // Resolve the callback
            InterceptCallback callback =
                callbackResolver.resolve(
                    null, intercept.getCallbackClass(), intercept.getCallbackMethod());

            // Create context for AFTER phase (no overrides applied for async)
            InterceptContext context =
                InterceptContext.forLocalAfterPhase(
                    className,
                    methodName,
                    paramTypes,
                    interceptType,
                    interceptedPeerUuid,
                    args,
                    returnValue,
                    isVoid,
                    thrownException);

            // Invoke the callback (response is ignored for fire-and-forget)
            callback.handle(context);

            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Local AFTER_ASYNC callback completed: class={}, method={}",
                  intercept.getCallbackClass(),
                  intercept.getCallbackMethod());
            }

          } catch (Exception e) {
            logger.error(
                "Error invoking local AFTER_ASYNC callback: class={}, method={}",
                intercept.getCallbackClass(),
                intercept.getCallbackMethod(),
                e);
            // Swallow exception - async callbacks are fire-and-forget
          }
        });
  }

  // ---- AROUND callbacks (synchronous) ----

  /**
   * State tracking for a pending local AROUND callback that needs AFTER phase.
   *
   * @param intercept the intercept message
   * @param callback the resolved callback instance
   * @param context the intercept context (preserved for AFTER phase)
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "Record is internal state holder; mutable objects are intentionally shared")
  public record LocalAroundCallbackState(
      InterceptMessage intercept, InterceptCallback callback, InterceptContext context) {}

  /**
   * Sends local AROUND BEFORE-phase callbacks and returns state for AFTER phase.
   *
   * <p>This method handles local AROUND intercepts with the {@code ctx.proceed()} API. For each
   * AROUND intercept:
   *
   * <ol>
   *   <li>Creates context with {@link LocalAroundAccessor}
   *   <li>Invokes callback
   *   <li>If {@code proceed()} not called: returns skip response (method not executed)
   *   <li>If {@code proceed()} called: collects mutations and tracks callback for AFTER phase
   * </ol>
   *
   * <p><b>Key difference from remote AROUND:</b> Local AROUND intercepts invoke the method directly
   * via {@link LocalAroundAccessor} when {@code proceed()} is called. The accessor is passed to the
   * dispatcher, which calls it at the appropriate time.
   *
   * @param localIntercepts the list of local AROUND intercepts
   * @param args the method arguments (live Java objects)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer
   * @param localAroundAccessor the accessor for invoking the method
   * @return consolidated response with pending callbacks for AFTER phase
   */
  public LocalAroundConsolidatedResponse sendLocalAroundCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid,
      LocalAroundAccessor localAroundAccessor) {

    if (localIntercepts.isEmpty()) {
      return LocalAroundConsolidatedResponse.proceed();
    }

    // Filter for AROUND intercepts
    List<InterceptMessage> aroundIntercepts = new ArrayList<>();
    for (InterceptMessage intercept : localIntercepts) {
      InterceptType type = InterceptType.fromByte(intercept.getInterceptType());
      if (type == InterceptType.AROUND) {
        aroundIntercepts.add(intercept);
      }
    }

    if (aroundIntercepts.isEmpty()) {
      return LocalAroundConsolidatedResponse.proceed();
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending {} local AROUND callbacks for {}.{}",
          aroundIntercepts.size(),
          className,
          methodName);
    }

    // Track state across all callbacks
    Map<Integer, Object> mutatedArgs = new HashMap<>();
    List<LocalAroundCallbackState> pendingCallbacks = new ArrayList<>();
    Throwable exceptionToThrow = null;
    Object[] currentArgs = args;

    // Process AROUND intercepts
    for (InterceptMessage intercept : aroundIntercepts) {
      try {
        // Resolve the callback
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create context for AROUND (starts in BEFORE phase)
        InterceptContext context =
            InterceptContext.forLocalAroundPhase(
                className, methodName, paramTypes, interceptedPeerUuid, currentArgs);

        // Set the local around accessor
        context.setLocalAroundAccessor(localAroundAccessor);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);

        // Check for exception
        exceptionToThrow = context.getExceptionToThrow();
        if (exceptionToThrow == null) {
          exceptionToThrow = response.getExceptionToThrow();
        }
        if (exceptionToThrow != null) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Local AROUND callback requested exception: {}",
                exceptionToThrow.getClass().getName());
          }
          break;
        }

        // Check if proceed() was called
        if (!context.isProceedCalled()) {
          // Validate: skipProceed() on non-void methods requires setReturnValue() or
          // setExceptionToThrow()
          Throwable exToThrow = context.getExceptionToThrow();
          if (!context.isVoid() && !context.isReturnValueModified() && exToThrow == null) {
            throw new IllegalStateException(
                "skipProceed() was called but no return value was set. "
                    + "You must call ctx.setReturnValue(value) or ctx.setExceptionToThrow(exception) "
                    + "before skipping execution. Use ctx.setReturnValue(null) for explicit null.");
          }

          // Skip execution - extract return value from context
          Object skipReturnValue = context.getReturnValueInternal();
          if (logger.isDebugEnabled()) {
            logger.debug("Local AROUND callback skipped proceed(), returning cached value");
          }
          return LocalAroundConsolidatedResponse.skipWithReturn(skipReturnValue, exToThrow);
        }

        // proceed() was called - collect any arg mutations made before proceed()
        if (context.isArgsModified()) {
          Object[] modifiedArgs = context.getArgsInternal();
          if (modifiedArgs != null) {
            for (int i = 0; i < modifiedArgs.length; i++) {
              mutatedArgs.put(i, modifiedArgs[i]);
            }
            // Update currentArgs for next callback
            currentArgs = modifiedArgs;
          }
        }

        // Track this callback for AFTER phase (context is now in AFTER phase after proceed())
        pendingCallbacks.add(new LocalAroundCallbackState(intercept, callback, context));

      } catch (Exception e) {
        logger.error(
            "Error invoking local AROUND callback: class={}, method={}",
            intercept.getCallbackClass(),
            intercept.getCallbackMethod(),
            e);
        exceptionToThrow = e;
        break;
      }
    }

    return new LocalAroundConsolidatedResponse(
        true, mutatedArgs, exceptionToThrow, pendingCallbacks);
  }

  /**
   * Sends local AROUND AFTER-phase callbacks for pending callbacks that called proceed().
   *
   * <p>This method is called after the intercepted method has executed. It processes any return
   * value modifications from callbacks that called {@code proceed()}.
   *
   * <p><b>Note:</b> Unlike remote AROUND, local AROUND doesn't need {@code isVoid} or {@code
   * thrownException} parameters because the callback's {@link InterceptContext} is updated directly
   * during {@link InterceptContext#proceed()}. The context already has the return value, void
   * status, and any thrown exception from the actual method invocation.
   *
   * @param pendingCallbacks list of callbacks that called proceed()
   * @param returnValue the return value from method execution (used as initial value for override
   *     tracking)
   * @return consolidated response with any return value override
   */
  public InterceptCallbackDispatcher.ConsolidatedCallbackResponse sendLocalAroundAfterCallbacks(
      List<LocalAroundCallbackState> pendingCallbacks, Object returnValue) {

    if (pendingCallbacks == null || pendingCallbacks.isEmpty()) {
      return InterceptCallbackDispatcher.ConsolidatedCallbackResponse.proceed();
    }

    // Track return value override
    Object currentReturnValue = returnValue;
    boolean hasOverride = false;
    Throwable exceptionToThrow = null;

    // Process pending callbacks - check if they modified return value after proceed()
    for (LocalAroundCallbackState state : pendingCallbacks) {
      InterceptContext context = state.context();

      // Check for return value override (set after proceed())
      if (context.isReturnValueModified()) {
        currentReturnValue = context.getReturnValueInternal();
        hasOverride = true;
        if (logger.isDebugEnabled()) {
          logger.debug("Local AROUND callback overrode return value after proceed()");
        }
      }

      // Check for exception
      if (context.getExceptionToThrow() != null) {
        exceptionToThrow = context.getExceptionToThrow();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Local AROUND callback requested exception: {}",
              exceptionToThrow.getClass().getName());
        }
        break;
      }
    }

    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        true, new HashMap<>(), exceptionToThrow, currentReturnValue, hasOverride);
  }

  // ---- Response classes ----

  /**
   * Consolidated response from local AROUND BEFORE-phase callbacks.
   *
   * <p>Contains:
   *
   * <ul>
   *   <li>Whether to proceed with method execution
   *   <li>Aggregated argument mutations
   *   <li>Exception to throw (if any)
   *   <li>Pending callbacks for AFTER phase
   *   <li>Skip return value (if proceed was skipped)
   * </ul>
   */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification =
          "Internal response object; mutable state is intentionally shared for efficiency")
  public static class LocalAroundConsolidatedResponse {

    /** Whether to proceed with method execution. */
    private final boolean shouldProceed;

    /** Aggregated argument mutations (index -> new value). */
    private final Map<Integer, Object> mutatedArgs;

    /** Exception to throw instead of execution. */
    private final Throwable exceptionToThrow;

    /** Callbacks that called proceed() and need AFTER processing. */
    private final List<LocalAroundCallbackState> pendingCallbacks;

    /** Return value if proceeding was skipped. */
    private final Object skipReturnValue;

    /**
     * Constructs a response for proceeding with execution.
     *
     * @param shouldProceed whether to proceed
     * @param mutatedArgs argument mutations
     * @param exceptionToThrow exception to throw
     * @param pendingCallbacks callbacks for AFTER phase
     */
    public LocalAroundConsolidatedResponse(
        boolean shouldProceed,
        Map<Integer, Object> mutatedArgs,
        Throwable exceptionToThrow,
        List<LocalAroundCallbackState> pendingCallbacks) {
      this.shouldProceed = shouldProceed;
      this.mutatedArgs = mutatedArgs;
      this.exceptionToThrow = exceptionToThrow;
      this.pendingCallbacks = pendingCallbacks;
      this.skipReturnValue = null;
    }

    /**
     * Constructs a response for skipping execution.
     *
     * @param skipReturnValue the return value to use
     * @param exceptionToThrow exception to throw
     */
    private LocalAroundConsolidatedResponse(Object skipReturnValue, Throwable exceptionToThrow) {
      this.shouldProceed = false;
      this.mutatedArgs = new HashMap<>();
      this.exceptionToThrow = exceptionToThrow;
      this.pendingCallbacks = new ArrayList<>();
      this.skipReturnValue = skipReturnValue;
    }

    /**
     * Creates a response indicating proceed with no callbacks.
     *
     * @return proceed response
     */
    public static LocalAroundConsolidatedResponse proceed() {
      return new LocalAroundConsolidatedResponse(true, new HashMap<>(), null, new ArrayList<>());
    }

    /**
     * Creates a response indicating skip execution with return value.
     *
     * @param returnValue the return value
     * @param exceptionToThrow exception to throw
     * @return skip response
     */
    public static LocalAroundConsolidatedResponse skipWithReturn(
        Object returnValue, Throwable exceptionToThrow) {
      return new LocalAroundConsolidatedResponse(returnValue, exceptionToThrow);
    }

    /**
     * Returns whether to proceed with method execution.
     *
     * @return true if should proceed
     */
    public boolean shouldProceed() {
      return shouldProceed;
    }

    /**
     * Returns the aggregated argument mutations.
     *
     * @return map of index to new value
     */
    public Map<Integer, Object> getMutatedArgs() {
      return mutatedArgs;
    }

    /**
     * Returns whether there's an exception to throw.
     *
     * @return true if exception set
     */
    public boolean shouldThrowException() {
      return exceptionToThrow != null;
    }

    /**
     * Returns the exception to throw.
     *
     * @return the exception
     */
    public Throwable getExceptionToThrow() {
      return exceptionToThrow;
    }

    /**
     * Returns the pending callbacks for AFTER phase.
     *
     * @return list of callbacks
     */
    public List<LocalAroundCallbackState> getPendingCallbacks() {
      return pendingCallbacks;
    }

    /**
     * Returns the skip return value.
     *
     * @return the return value when skipping
     */
    public Object getSkipReturnValue() {
      return skipReturnValue;
    }
  }
}
