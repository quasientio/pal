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
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptApiMisuseException;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InvalidCallbackExceptionException;
import io.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import io.quasient.pal.messages.colfer.InterceptMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.annotation.Nullable;
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
 * <p><b>Async Callback Executor:</b> BEFORE_ASYNC and AFTER_ASYNC callbacks are dispatched via an
 * injected {@link ExecutorService}. The executor implementation is selected by {@link
 * VirtualThreadCallbackExecutor}:
 *
 * <ul>
 *   <li><b>Java 21+:</b> Virtual thread executor — each async callback runs on a lightweight
 *       virtual thread (~1KB), eliminating thread pool sizing concerns. Ideal for the callback
 *       workload (short-lived, potentially blocking on I/O).
 *   <li><b>Java 17-20:</b> Cached thread pool — auto-scales to demand, reuses idle threads.
 *   <li><b>Override:</b> Set system property {@code
 *       pal.intercept.async.executor=VIRTUAL|CACHED|FIXED}
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Callback handlers must also be thread-safe,
 * as they may be invoked concurrently for different intercept requests.
 *
 * @see InterceptCallbackDispatcher for remote intercept handling
 * @see CallbackResolver for callback resolution
 * @see VirtualThreadCallbackExecutor for executor selection strategy
 */
@Singleton
public class LocalInterceptCallbackDispatcher {

  /** Logger instance for debugging local callback dispatch operations. */
  private static final Logger logger =
      LoggerFactory.getLogger(LocalInterceptCallbackDispatcher.class);

  /**
   * Thread-local reusable HashMap for collecting argument mutations during callback dispatch.
   *
   * <p>This eliminates per-dispatch {@code new HashMap<>()} allocations. The map is cleared before
   * each use and its contents are copied into the response via {@link #snapshotMutations(Map)} when
   * non-empty. Mutation maps are typically small (0-3 entries) and short-lived, making them ideal
   * for thread-local reuse.
   */
  private static final ThreadLocal<HashMap<Integer, Object>> TL_MUTATIONS =
      ThreadLocal.withInitial(HashMap::new);

  /** Shared callback resolver for looking up registered callbacks and resolving static methods. */
  private final CallbackResolver callbackResolver;

  /** Executor service for running async intercept callbacks in the background. */
  private final ExecutorService asyncExecutor;

  /** Resolver for determining exception propagation and checked exception policies. */
  private final ExceptionPolicyResolver exceptionPolicyResolver;

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
    this(callbackResolver, asyncExecutor, createDefaultPolicyResolver());
  }

  /**
   * Constructs a new LocalInterceptCallbackDispatcher with custom exception policy resolver.
   *
   * @param callbackResolver the shared callback resolver
   * @param asyncExecutor the executor service for async callbacks
   * @param exceptionPolicyResolver the resolver for exception policies
   */
  public LocalInterceptCallbackDispatcher(
      CallbackResolver callbackResolver,
      ExecutorService asyncExecutor,
      ExceptionPolicyResolver exceptionPolicyResolver) {
    this.callbackResolver = callbackResolver;
    this.asyncExecutor = asyncExecutor;
    this.exceptionPolicyResolver =
        exceptionPolicyResolver != null ? exceptionPolicyResolver : createDefaultPolicyResolver();
  }

  /**
   * Creates a default exception policy resolver with sensible defaults.
   *
   * @return the default policy resolver
   */
  private static ExceptionPolicyResolver createDefaultPolicyResolver() {
    return new ExceptionPolicyResolver(new ExceptionPolicyConfig.Builder().build());
  }

  /**
   * Creates a snapshot of the given mutations map for safe inclusion in a response.
   *
   * <p>If the map is empty, returns {@link Collections#emptyMap()} to avoid allocation. If
   * non-empty, returns a new {@link HashMap} copy so the response is independent of the reusable
   * thread-local map.
   *
   * @param mutations the mutations map to snapshot (may be the thread-local reusable map)
   * @return an independent map suitable for inclusion in a response object
   */
  private static Map<Integer, Object> snapshotMutations(Map<Integer, Object> mutations) {
    return mutations.isEmpty() ? Collections.emptyMap() : new HashMap<>(mutations);
  }

  // ---- Exception handling helpers ----

  /**
   * Result of processing a callback exception based on exception policies.
   *
   * @param shouldPropagate whether the exception should be propagated to the caller
   * @param exceptionToPropagate the exception to propagate (may be validated/wrapped), or null
   */
  private record ExceptionHandlingResult(boolean shouldPropagate, Throwable exceptionToPropagate) {}

  /**
   * Unwraps an exception to get the root cause.
   *
   * <p>When callbacks are invoked via reflection, exceptions are wrapped in {@link
   * java.lang.reflect.InvocationTargetException}. This method extracts the actual exception that
   * was thrown by the callback.
   *
   * @param exception the exception to unwrap
   * @return the unwrapped exception (or original if not wrapped)
   */
  private static Throwable unwrapException(Throwable exception) {
    Throwable current = exception;
    while (current instanceof java.lang.reflect.InvocationTargetException
        && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  /**
   * Processes an exception thrown during callback execution based on exception policies.
   *
   * <p>This method implements the following logic:
   *
   * <ol>
   *   <li>API misuse exceptions (InterceptApiMisuseException) always propagate regardless of
   *       policy. These indicate programming errors in callback handlers (e.g., calling
   *       getReturnValue() in BEFORE phase) and must be visible to developers.
   *   <li>For other exceptions, the propagation policy determines handling:
   *       <ul>
   *         <li>PROPAGATE_ALL: All exceptions propagate
   *         <li>PROPAGATE_EXPLICIT_ONLY: Only explicit exceptions (via setExceptionToThrow)
   *             propagate
   *         <li>SWALLOW_ALL: All exceptions are swallowed
   *         <li>PROPAGATE_CONTROLLED_ONLY: Only explicit exceptions from successful callbacks
   *             propagate
   *       </ul>
   *   <li>For propagated checked exceptions, validation is applied based on CheckedExceptionPolicy
   * </ol>
   *
   * @param directThrowException exception thrown directly by callback (not via setExceptionToThrow)
   * @param explicitException exception set via setExceptionToThrow (from context or response)
   * @param callbackCompletedSuccessfully true if callback completed without throwing
   * @param intercept the intercept message for policy lookup
   * @param interceptType the type of intercept being processed
   * @param declaredExceptions the declared exceptions of the intercepted method
   * @param callbackClass the callback class name (for logging)
   * @param callbackMethod the callback method name (for logging)
   * @return the exception handling result indicating whether to propagate and what exception
   */
  private ExceptionHandlingResult processException(
      @Nullable Throwable directThrowException,
      @Nullable Throwable explicitException,
      boolean callbackCompletedSuccessfully,
      InterceptMessage intercept,
      InterceptType interceptType,
      @Nullable String[] declaredExceptions,
      String callbackClass,
      String callbackMethod) {

    // Determine which exception to consider
    Throwable exceptionToConsider = directThrowException != null ? directThrowException : null;
    boolean isExplicit = false;

    // If callback completed successfully, use explicit exception if set
    if (callbackCompletedSuccessfully && explicitException != null) {
      exceptionToConsider = explicitException;
      isExplicit = true;
    } else if (!callbackCompletedSuccessfully && directThrowException != null) {
      // Callback threw directly - unwrap InvocationTargetException if needed
      exceptionToConsider = unwrapException(directThrowException);
      isExplicit = false;
    }

    // No exception to process
    if (exceptionToConsider == null) {
      return new ExceptionHandlingResult(false, null);
    }

    // Step 1: API misuse exceptions always propagate (bypass policy)
    if (exceptionToConsider instanceof InterceptApiMisuseException) {
      logger.error(
          "API misuse exception from callback: class={}, method={}, exception={}",
          callbackClass,
          callbackMethod,
          exceptionToConsider.getMessage());
      return new ExceptionHandlingResult(true, exceptionToConsider);
    }

    // Step 2: Resolve propagation policy
    ExceptionPropagationPolicy propagationPolicy =
        exceptionPolicyResolver.resolvePropagationPolicy(intercept, interceptType);

    // Step 3: Apply propagation policy
    boolean shouldPropagate =
        switch (propagationPolicy) {
          case PROPAGATE_ALL -> true;
          case PROPAGATE_EXPLICIT_ONLY -> isExplicit;
          case SWALLOW_ALL -> false;
          case PROPAGATE_CONTROLLED_ONLY -> isExplicit && callbackCompletedSuccessfully;
        };

    if (!shouldPropagate) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Swallowing exception per policy {}: class={}, method={}, exception={}",
            propagationPolicy,
            callbackClass,
            callbackMethod,
            exceptionToConsider.getClass().getName());
      }
      return new ExceptionHandlingResult(false, null);
    }

    // Step 4: Validate checked exceptions if propagating
    CheckedExceptionPolicy checkedPolicy =
        exceptionPolicyResolver.resolveCheckedExceptionPolicy(intercept, interceptType);

    Throwable validatedException;
    try {
      validatedException =
          ExceptionValidator.validateThrowable(
              exceptionToConsider, declaredExceptions, checkedPolicy);
    } catch (InvalidCallbackExceptionException ice) {
      // REJECT policy throws InvalidCallbackExceptionException for incompatible checked exceptions
      validatedException = ice;
    }

    return new ExceptionHandlingResult(true, validatedException);
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
    return sendLocalBeforeCallbacks(
        localIntercepts, args, className, methodName, paramTypes, interceptedPeerUuid, null);
  }

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
   * <p>Exception handling is controlled by exception policies:
   *
   * <ul>
   *   <li>API misuse exceptions always propagate regardless of policy
   *   <li>Propagation policy determines which exceptions propagate to the caller
   *   <li>Checked exception policy validates exceptions against declared exceptions
   * </ul>
   *
   * @param localIntercepts the list of local BEFORE intercepts
   * @param args the method arguments (live Java objects)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer (same as callback peer)
   * @param declaredExceptions the declared exceptions of the intercepted method (may be null)
   * @return the consolidated response with mutations and exceptions
   */
  public InterceptCallbackDispatcher.ConsolidatedCallbackResponse sendLocalBeforeCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid,
      @Nullable String[] declaredExceptions) {

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

    // Reuse thread-local HashMap for collecting mutations (cleared before use)
    HashMap<Integer, Object> aggregatedMutations = TL_MUTATIONS.get();
    aggregatedMutations.clear();
    Throwable exceptionToThrow = null;

    // Create metadata once and share across all callbacks for this invocation
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(className, methodName, paramTypes);

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-BEFORE intercepts (they shouldn't be here, but defensive)
      if (interceptType != InterceptType.BEFORE) {
        continue;
      }

      Throwable directThrowException = null;
      Throwable explicitException = null;
      boolean callbackCompletedSuccessfully = false;

      try {
        // Resolve the callback (InterceptMessage only has callbackClass/callbackMethod, no
        // registered ID)
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create pooled context for BEFORE phase (sync callback — safe to reuse)
        InterceptContext context =
            InterceptContext.forLocalBeforePhasePooled(
                metadata, interceptType, interceptedPeerUuid, args);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);
        callbackCompletedSuccessfully = true;

        // Aggregate argument mutations (only indices that actually changed)
        if (context.isArgsModified()) {
          Object[] modifiedArgs = context.getArgsInternal();
          if (modifiedArgs != null) {
            for (int i = 0; i < modifiedArgs.length; i++) {
              if (modifiedArgs[i] != args[i]) {
                aggregatedMutations.put(i, modifiedArgs[i]);
              }
            }
          }
        }

        // Check for explicit exception (from context or response)
        explicitException = context.getExceptionToThrow();
        if (explicitException == null) {
          explicitException = response.getExceptionToThrow();
        }

      } catch (Exception e) {
        logger.error(
            "Error invoking local BEFORE callback: class={}, method={}",
            intercept.getCallbackClass(),
            intercept.getCallbackMethod(),
            e);
        directThrowException = e;
      }

      // Process exception using policy-based handling
      ExceptionHandlingResult result =
          processException(
              directThrowException,
              explicitException,
              callbackCompletedSuccessfully,
              intercept,
              interceptType,
              declaredExceptions,
              intercept.getCallbackClass(),
              intercept.getCallbackMethod());

      if (result.shouldPropagate()) {
        exceptionToThrow = result.exceptionToPropagate();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Local BEFORE callback requested exception: {}",
              exceptionToThrow.getClass().getName());
        }
        break;
      }
    }

    // Snapshot the thread-local map: empty → emptyMap() singleton, non-empty → defensive copy
    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        exceptionToThrow == null, snapshotMutations(aggregatedMutations), exceptionToThrow);
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

    // Create metadata once and share across async callbacks (immutable, safe to share)
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(className, methodName, paramTypes);

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-BEFORE_ASYNC intercepts
      if (interceptType != InterceptType.BEFORE_ASYNC) {
        continue;
      }

      // Submit callback to executor (fire-and-forget, Future result intentionally ignored)
      submitAsyncBeforeCallback(intercept, args, metadata, interceptType, interceptedPeerUuid);
    }
  }

  /**
   * Submits an async BEFORE callback to the executor.
   *
   * <p>This method is extracted to satisfy spotbugs dead local store warnings when ignoring the
   * Future return value intentionally for fire-and-forget semantics.
   *
   * <p><b>IMPORTANT:</b> Async callbacks MUST use fresh InterceptContext allocations (not pooled),
   * because the callback executes on a different thread after this method returns. The pooled
   * context would be recycled on the calling thread before the async callback reads it.
   *
   * @param intercept the intercept message
   * @param args the method arguments
   * @param metadata the pre-built metadata (immutable, safe to share across threads)
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
      InterceptContext.LocalInterceptMetadata metadata,
      InterceptType interceptType,
      String interceptedPeerUuid) {
    asyncExecutor.submit(
        () -> {
          try {
            // Resolve the callback
            InterceptCallback callback =
                callbackResolver.resolve(
                    null, intercept.getCallbackClass(), intercept.getCallbackMethod());

            // Create FRESH context for BEFORE phase (NOT pooled — async runs on different thread)
            InterceptContext context =
                InterceptContext.forLocalBeforePhase(
                    metadata, interceptType, interceptedPeerUuid, args);

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
    return sendLocalAfterCallbacks(
        localIntercepts,
        args,
        returnValue,
        isVoid,
        thrownException,
        className,
        methodName,
        paramTypes,
        interceptedPeerUuid,
        null);
  }

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
   * <p>Exception handling is controlled by exception policies:
   *
   * <ul>
   *   <li>API misuse exceptions always propagate regardless of policy
   *   <li>Propagation policy determines which exceptions propagate to the caller
   *   <li>Checked exception policy validates exceptions against declared exceptions
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
   * @param declaredExceptions the declared exceptions of the intercepted method (may be null)
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
      String interceptedPeerUuid,
      @Nullable String[] declaredExceptions) {

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

    // Create metadata once and share across all callbacks for this invocation
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(className, methodName, paramTypes);

    for (InterceptMessage intercept : localIntercepts) {
      InterceptType interceptType = InterceptType.fromByte(intercept.getInterceptType());

      // Skip non-AFTER intercepts
      if (interceptType != InterceptType.AFTER) {
        continue;
      }

      Throwable directThrowException = null;
      Throwable explicitException = null;
      boolean callbackCompletedSuccessfully = false;

      try {
        // Resolve the callback (InterceptMessage only has callbackClass/callbackMethod, no
        // registered ID)
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create context for AFTER phase (reuse metadata)
        InterceptContext context =
            InterceptContext.forLocalAfterPhase(
                metadata,
                interceptType,
                interceptedPeerUuid,
                args,
                currentReturnValue,
                isVoid,
                thrownException);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);
        callbackCompletedSuccessfully = true;

        // Check for return value override
        if (context.isReturnValueModified()) {
          currentReturnValue = context.getReturnValue();
          returnValueOverridden = true;
        }

        // Check for explicit exception (from context or response)
        explicitException = context.getExceptionToThrow();
        if (explicitException == null) {
          explicitException = response.getExceptionToThrow();
        }

      } catch (Exception e) {
        logger.error(
            "Error invoking local AFTER callback: class={}, method={}",
            intercept.getCallbackClass(),
            intercept.getCallbackMethod(),
            e);
        directThrowException = e;
      }

      // Process exception using policy-based handling
      ExceptionHandlingResult result =
          processException(
              directThrowException,
              explicitException,
              callbackCompletedSuccessfully,
              intercept,
              interceptType,
              declaredExceptions,
              intercept.getCallbackClass(),
              intercept.getCallbackMethod());

      if (result.shouldPropagate()) {
        exceptionToThrow = result.exceptionToPropagate();
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Local AFTER callback requested exception: {}",
              exceptionToThrow.getClass().getName());
        }
        break;
      }
    }

    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        true, Collections.emptyMap(), exceptionToThrow, currentReturnValue, returnValueOverridden);
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

    // Create metadata once and share across async callbacks (immutable, safe to share)
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(className, methodName, paramTypes);

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
          metadata,
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
   * <p><b>IMPORTANT:</b> Async callbacks MUST use fresh InterceptContext allocations (not pooled),
   * because the callback executes on a different thread after this method returns.
   *
   * @param intercept the intercept message
   * @param args the method arguments
   * @param returnValue the return value (may be null)
   * @param isVoid whether the method is void
   * @param thrownException exception thrown (may be null)
   * @param metadata the pre-built metadata (immutable, safe to share across threads)
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
      InterceptContext.LocalInterceptMetadata metadata,
      InterceptType interceptType,
      String interceptedPeerUuid) {
    asyncExecutor.submit(
        () -> {
          try {
            // Resolve the callback
            InterceptCallback callback =
                callbackResolver.resolve(
                    null, intercept.getCallbackClass(), intercept.getCallbackMethod());

            // Create FRESH context for AFTER phase (NOT pooled — async runs on different thread)
            InterceptContext context =
                InterceptContext.forLocalAfterPhase(
                    metadata,
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
    return sendLocalAroundCallbacks(
        localIntercepts,
        args,
        className,
        methodName,
        paramTypes,
        interceptedPeerUuid,
        localAroundAccessor,
        null);
  }

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
   * <p>Exception handling is controlled by exception policies:
   *
   * <ul>
   *   <li>API misuse exceptions always propagate regardless of policy
   *   <li>Propagation policy determines which exceptions propagate to the caller
   *   <li>Checked exception policy validates exceptions against declared exceptions
   * </ul>
   *
   * @param localIntercepts the list of local AROUND intercepts
   * @param args the method arguments (live Java objects)
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter type names
   * @param interceptedPeerUuid the UUID of the intercepted peer
   * @param localAroundAccessor the accessor for invoking the method
   * @param declaredExceptions the declared exceptions of the intercepted method (may be null)
   * @return consolidated response with pending callbacks for AFTER phase
   */
  public LocalAroundConsolidatedResponse sendLocalAroundCallbacks(
      List<InterceptMessage> localIntercepts,
      Object[] args,
      String className,
      String methodName,
      List<String> paramTypes,
      String interceptedPeerUuid,
      LocalAroundAccessor localAroundAccessor,
      @Nullable String[] declaredExceptions) {

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

    // Reuse thread-local HashMap for collecting mutations (cleared before use)
    HashMap<Integer, Object> mutatedArgs = TL_MUTATIONS.get();
    mutatedArgs.clear();
    List<LocalAroundCallbackState> pendingCallbacks = new ArrayList<>();
    Throwable exceptionToThrow = null;
    Object[] currentArgs = args;

    // Create metadata once and share across all AROUND callbacks for this invocation
    InterceptContext.LocalInterceptMetadata metadata =
        new InterceptContext.LocalInterceptMetadata(className, methodName, paramTypes);

    // Process AROUND intercepts
    for (InterceptMessage intercept : aroundIntercepts) {
      InterceptType interceptType = InterceptType.AROUND;
      Throwable directThrowException = null;
      Throwable explicitException = null;
      boolean callbackCompletedSuccessfully = false;

      try {
        // Resolve the callback
        InterceptCallback callback =
            callbackResolver.resolve(
                null, intercept.getCallbackClass(), intercept.getCallbackMethod());

        // Create context for AROUND (starts in BEFORE phase, reuse metadata)
        // AROUND contexts are NOT pooled because they may be stored in pendingCallbacks
        InterceptContext context =
            InterceptContext.forLocalAroundPhase(metadata, interceptedPeerUuid, currentArgs);

        // Set the local around accessor
        context.setLocalAroundAccessor(localAroundAccessor);

        // Invoke the callback
        InterceptCallbackResponse response = callback.handle(context);
        callbackCompletedSuccessfully = true;

        // Check for explicit exception (from context or response)
        explicitException = context.getExceptionToThrow();
        if (explicitException == null) {
          explicitException = response.getExceptionToThrow();
        }

        // Process exception using policy-based handling
        ExceptionHandlingResult result =
            processException(
                null,
                explicitException,
                callbackCompletedSuccessfully,
                intercept,
                interceptType,
                declaredExceptions,
                intercept.getCallbackClass(),
                intercept.getCallbackMethod());

        if (result.shouldPropagate()) {
          exceptionToThrow = result.exceptionToPropagate();
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
              if (modifiedArgs[i] != currentArgs[i]) {
                mutatedArgs.put(i, modifiedArgs[i]);
              }
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
        directThrowException = e;

        // Process exception using policy-based handling
        ExceptionHandlingResult result =
            processException(
                directThrowException,
                null,
                false,
                intercept,
                interceptType,
                declaredExceptions,
                intercept.getCallbackClass(),
                intercept.getCallbackMethod());

        if (result.shouldPropagate()) {
          exceptionToThrow = result.exceptionToPropagate();
          break;
        }
      }
    }

    // Snapshot the thread-local map: empty → emptyMap() singleton, non-empty → defensive copy
    return new LocalAroundConsolidatedResponse(
        true, snapshotMutations(mutatedArgs), exceptionToThrow, pendingCallbacks);
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
    return sendLocalAroundAfterCallbacks(pendingCallbacks, returnValue, null);
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
   * <p>Exception handling is controlled by exception policies:
   *
   * <ul>
   *   <li>API misuse exceptions always propagate regardless of policy
   *   <li>Propagation policy determines which exceptions propagate to the caller
   *   <li>Checked exception policy validates exceptions against declared exceptions
   * </ul>
   *
   * @param pendingCallbacks list of callbacks that called proceed()
   * @param returnValue the return value from method execution (used as initial value for override
   *     tracking)
   * @param declaredExceptions the declared exceptions of the intercepted method (may be null)
   * @return consolidated response with any return value override
   */
  public InterceptCallbackDispatcher.ConsolidatedCallbackResponse sendLocalAroundAfterCallbacks(
      List<LocalAroundCallbackState> pendingCallbacks,
      Object returnValue,
      @Nullable String[] declaredExceptions) {

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
      InterceptMessage intercept = state.intercept();
      InterceptType interceptType = InterceptType.AROUND;

      // Check for return value override (set after proceed())
      if (context.isReturnValueModified()) {
        currentReturnValue = context.getReturnValueInternal();
        hasOverride = true;
        if (logger.isDebugEnabled()) {
          logger.debug("Local AROUND callback overrode return value after proceed()");
        }
      }

      // Check for exception
      Throwable explicitException = context.getExceptionToThrow();
      if (explicitException != null) {
        // Process exception using policy-based handling
        ExceptionHandlingResult result =
            processException(
                null,
                explicitException,
                true, // Callback completed successfully if we got to AFTER phase
                intercept,
                interceptType,
                declaredExceptions,
                intercept.getCallbackClass(),
                intercept.getCallbackMethod());

        if (result.shouldPropagate()) {
          exceptionToThrow = result.exceptionToPropagate();
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Local AROUND callback requested exception: {}",
                exceptionToThrow.getClass().getName());
          }
          break;
        }
      }
    }

    return new InterceptCallbackDispatcher.ConsolidatedCallbackResponse(
        true, Collections.emptyMap(), exceptionToThrow, currentReturnValue, hasOverride);
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
      this.mutatedArgs = Collections.emptyMap();
      this.exceptionToThrow = exceptionToThrow;
      this.pendingCallbacks = Collections.emptyList();
      this.skipReturnValue = skipReturnValue;
    }

    /**
     * Creates a response indicating proceed with no callbacks.
     *
     * @return proceed response
     */
    public static LocalAroundConsolidatedResponse proceed() {
      return new LocalAroundConsolidatedResponse(
          true, Collections.emptyMap(), null, Collections.emptyList());
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
