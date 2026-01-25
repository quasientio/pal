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
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.InterceptMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates intercept activation with in-flight dispatch tracking to ensure safe intercept
 * activation.
 *
 * <p>This class orchestrates the sequence of operations needed to safely activate an intercept when
 * in-flight tracking is enabled:
 *
 * <ol>
 *   <li><b>Check configuration:</b> Determine if in-flight tracking is enabled globally and if the
 *       intercept has a forceImmediate override
 *   <li><b>For immediate activation:</b> Register the intercept synchronously and return
 *   <li><b>For drain-based activation:</b> Submit async task that performs:
 *       <ul>
 *         <li>Fence new dispatches to block new matching dispatches
 *         <li>Wait for in-flight dispatches to complete within the configured timeout
 *         <li>Register the intercept with InterceptMatcher once quiescence is achieved
 *         <li>Unfence to allow new dispatches to proceed (now intercepted)
 *       </ul>
 * </ol>
 *
 * <p><b>Async activation:</b> When drain is required, the activation is performed asynchronously in
 * a dedicated single-threaded executor. This prevents blocking the InterceptMatcher thread that
 * processes intercept events from etcd. The caller receives an {@link ActivationResult} with status
 * {@link ActivationResult#ASYNC_PENDING} indicating the activation is in progress.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li><b>Global setting:</b> {@link RunOptions#WITH_IN_FLIGHT_TRACKING} controls whether
 *       in-flight tracking and drain is enabled for all intercepts by default
 *   <li><b>Per-intercept override:</b> {@link InterceptRequest#isForceImmediate()} can override the
 *       global setting to force immediate activation for a specific intercept
 *   <li><b>Drain timeout:</b> Configured via the {@code intercept.drain.timeout.ms} property
 *       (default: 5000ms)
 * </ul>
 *
 * <p><b>Activation result:</b> The {@link #activateIntercept(InterceptMessage)} method returns an
 * {@link ActivationResult} indicating:
 *
 * <ul>
 *   <li>{@link ActivationResult#isSuccess()} = true: Immediate activation succeeded
 *   <li>{@link ActivationResult#isAsyncPending()} = true: Async activation submitted (drain in
 *       progress)
 *   <li>{@link ActivationResult#isSuccess()} = false: Activation failed (e.g., duplicate intercept)
 * </ul>
 *
 * <p><b>Thread safety:</b> This class is thread-safe and may be called concurrently from multiple
 * threads. Async activations are serialized via a single-threaded executor.
 *
 * <p><b>Usage example:</b>
 *
 * <pre>{@code
 * InterceptActivationCoordinator coordinator = ...;
 * InterceptMessage message = ...;
 *
 * ActivationResult result = coordinator.activateIntercept(message);
 * if (result.isSuccess()) {
 *   // Immediate activation succeeded
 * } else if (result.isAsyncPending()) {
 *   // Drain-based activation in progress, will complete asynchronously
 * } else {
 *   logger.warn("Intercept activation failed: {}", result.getMessage());
 * }
 * }</pre>
 *
 * @see InFlightDispatchTracker
 * @see InterceptMatcher
 * @see RunOptions#WITH_IN_FLIGHT_TRACKING
 */
@Singleton
public class InterceptActivationCoordinator {

  /** Logger instance for this class. */
  private static final Logger logger =
      LoggerFactory.getLogger(InterceptActivationCoordinator.class);

  /** Tracker for in-flight dispatches, used to fence and wait for quiescence. */
  private final InFlightDispatchTracker inFlightTracker;

  /**
   * Provider for InterceptMatcher to break circular dependency.
   *
   * <p>This coordinator calls {@link
   * InterceptMatcher#registerInterceptRequest(io.quasient.pal.messages.colfer.InterceptMessage)}
   * after quiescence is achieved (when drain is required) or immediately (when drain is not
   * required).
   *
   * <p>We use a Provider to avoid a circular dependency: InterceptMatcher depends on
   * InterceptActivationCoordinator, and the coordinator needs to call back to the matcher.
   */
  private final Provider<InterceptMatcher> interceptMatcherProvider;

  /**
   * Runtime options controlling peer behavior.
   *
   * <p>This set is injected by Guice and is immutable at runtime, so storing a reference is safe.
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "RunOptions set is injected and immutable at runtime")
  private final Set<RunOptions> runOptions;

  /** Timeout in milliseconds for waiting for in-flight dispatches to drain. */
  private final long drainTimeoutMs;

  /**
   * Single-threaded executor for async drain-based activations.
   *
   * <p>Using a single thread ensures that only one drain/activation runs at a time, which is
   * important because {@link InterceptRequests} assumes single-writer semantics. This also prevents
   * blocking the InterceptMatcher thread that processes intercept events.
   */
  private final ExecutorService asyncActivationExecutor;

  /**
   * Constructs a new InterceptActivationCoordinator with the specified dependencies.
   *
   * @param inFlightTracker the tracker for in-flight dispatches
   * @param interceptMatcherProvider the provider for InterceptMatcher (to break circular
   *     dependency)
   * @param runOptions the runtime options controlling peer behavior
   * @param drainTimeoutMs the timeout in milliseconds for waiting for in-flight dispatches to drain
   *     (injected from properties as "intercept.drain.timeout.ms")
   */
  @Inject
  public InterceptActivationCoordinator(
      InFlightDispatchTracker inFlightTracker,
      Provider<InterceptMatcher> interceptMatcherProvider,
      Set<RunOptions> runOptions,
      @Named("intercept.drain.timeout.ms") long drainTimeoutMs) {
    this(
        inFlightTracker,
        interceptMatcherProvider,
        runOptions,
        drainTimeoutMs,
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "intercept-activation-async");
              t.setDaemon(true);
              return t;
            }));
  }

  /**
   * Package-private constructor for testing that allows injection of a custom executor.
   *
   * @param inFlightTracker the tracker for in-flight dispatches
   * @param interceptMatcherProvider the provider for InterceptMatcher
   * @param runOptions the runtime options controlling peer behavior
   * @param drainTimeoutMs the timeout in milliseconds for waiting for in-flight dispatches to drain
   * @param asyncActivationExecutor the executor for async activations (for testing)
   */
  InterceptActivationCoordinator(
      InFlightDispatchTracker inFlightTracker,
      Provider<InterceptMatcher> interceptMatcherProvider,
      Set<RunOptions> runOptions,
      long drainTimeoutMs,
      ExecutorService asyncActivationExecutor) {
    this.inFlightTracker = inFlightTracker;
    this.interceptMatcherProvider = interceptMatcherProvider;
    this.runOptions = runOptions;
    this.drainTimeoutMs = drainTimeoutMs;
    this.asyncActivationExecutor = asyncActivationExecutor;

    if (logger.isDebugEnabled()) {
      logger.debug(
          "InterceptActivationCoordinator initialized with drainTimeoutMs={}, WITH_IN_FLIGHT_TRACKING={}",
          drainTimeoutMs,
          runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING));
    }
  }

  /**
   * Shuts down the async activation executor, waiting for pending activations to complete.
   *
   * <p>This method should be called during peer shutdown to ensure clean termination.
   *
   * @param timeoutMs maximum time to wait for pending activations to complete
   */
  public void shutdown(long timeoutMs) {
    asyncActivationExecutor.shutdown();
    try {
      if (!asyncActivationExecutor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
        logger.warn(
            "Async activation executor did not terminate within {}ms, forcing shutdown", timeoutMs);
        asyncActivationExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      asyncActivationExecutor.shutdownNow();
    }
  }

  /**
   * Activates an intercept from an InterceptMessage received via ZMQ.
   *
   * <p>This method is called by {@link InterceptMatcher} when it receives an intercept registration
   * message. It coordinates the activation sequence based on configuration and the forceImmediate
   * flag in the message.
   *
   * <p>The activation flow:
   *
   * <ol>
   *   <li>Extract class and method patterns from the message
   *   <li>Check if drain is required (WITH_IN_FLIGHT_TRACKING enabled and forceImmediate=false)
   *   <li>If drain required: submit async task to executor, return ASYNC_PENDING immediately
   *   <li>If drain not required: register immediately and return success/failure
   * </ol>
   *
   * <p><b>Non-blocking:</b> This method never blocks. When drain is required, the drain/activation
   * is performed asynchronously in a background thread, allowing the caller (InterceptMatcher) to
   * continue processing other intercept events.
   *
   * @param interceptMessage the intercept message containing registration data
   * @return an {@link ActivationResult} indicating success, async pending, or failure
   */
  public ActivationResult activateIntercept(InterceptMessage interceptMessage) {
    String classPattern = interceptMessage.getClazz();
    String methodPattern = extractMethodPatternFromMessage(interceptMessage);

    boolean trackingEnabled = runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING);
    boolean forceImmediate = interceptMessage.getForceImmediate();

    // Determine if drain is required
    boolean shouldDrain = trackingEnabled && !forceImmediate;

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Activating intercept for {}.{}, trackingEnabled={}, forceImmediate={}, shouldDrain={}",
          classPattern,
          methodPattern,
          trackingEnabled,
          forceImmediate,
          shouldDrain);
    }

    if (shouldDrain) {
      // Start fencing synchronously to ensure new dispatches are blocked immediately.
      // This preserves the semantic that once register() returns, new calls will be blocked.
      inFlightTracker.startFencing(classPattern, methodPattern);

      // Submit the rest (wait for quiescence, register, stop fencing) to async executor.
      // This avoids blocking the InterceptMatcher thread during the potentially long drain wait.
      submitAsyncDrainAndRegister(classPattern, methodPattern, interceptMessage);
      return ActivationResult.asyncPending(
          "Intercept activation pending (drain in progress for "
              + classPattern
              + "."
              + methodPattern
              + ")");
    } else {
      return activateImmediateAndRegister(classPattern, methodPattern, interceptMessage);
    }
  }

  /**
   * Submits the drain and register task to the async executor.
   *
   * <p>This is called AFTER fencing has been started synchronously. The async task waits for
   * quiescence, registers the intercept, and stops fencing.
   *
   * <p>This helper method exists to allow proper suppression of the SpotBugs DLS_DEAD_LOCAL_STORE
   * warning that would occur if we used a local variable to suppress the error-prone
   * FutureReturnValueIgnored warning.
   *
   * @param classPattern the class pattern
   * @param methodPattern the method pattern
   * @param interceptMessage the intercept message to register
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @SuppressFBWarnings(
      value = "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE",
      justification = "Future intentionally ignored - activation completes asynchronously")
  private void submitAsyncDrainAndRegister(
      String classPattern, String methodPattern, InterceptMessage interceptMessage) {
    asyncActivationExecutor.submit(
        () -> waitForQuiescenceAndRegisterAsync(classPattern, methodPattern, interceptMessage));
  }

  /**
   * Activates an intercept immediately and registers it with the matcher.
   *
   * <p>This method is used when:
   *
   * <ul>
   *   <li>Global in-flight tracking is disabled, or
   *   <li>The intercept has forceImmediate=true (per-intercept override)
   * </ul>
   *
   * @param classPattern the class pattern from the message
   * @param methodPattern the method pattern from the message
   * @param interceptMessage the intercept message to register
   * @return an {@link ActivationResult} indicating success or failure
   */
  private ActivationResult activateImmediateAndRegister(
      String classPattern, String methodPattern, InterceptMessage interceptMessage) {
    if (logger.isDebugEnabled()) {
      logger.debug("Activating intercept immediately for {}.{}", classPattern, methodPattern);
    }

    try {
      interceptMatcherProvider.get().registerInterceptRequest(interceptMessage);
      return ActivationResult.success("Intercept activated immediately");
    } catch (DuplicateInterceptException e) {
      if (logger.isWarnEnabled()) {
        logger.warn(
            "Cannot register duplicate intercept for {}.{}", classPattern, methodPattern, e);
      }
      return ActivationResult.failure("Duplicate intercept: " + e.getMessage());
    } catch (Exception e) {
      if (logger.isErrorEnabled()) {
        logger.error("Error activating intercept for {}.{}", classPattern, methodPattern, e);
      }
      return ActivationResult.failure("Error during intercept activation: " + e.getMessage());
    }
  }

  /**
   * Waits for quiescence and registers the intercept asynchronously.
   *
   * <p>This method runs asynchronously in the {@link #asyncActivationExecutor} thread. It is called
   * AFTER fencing has been started synchronously by the caller. It:
   *
   * <ol>
   *   <li>Waits for in-flight dispatches to complete (quiescence) with configured timeout
   *   <li>If quiescence is achieved, registers the intercept with InterceptMatcher
   *   <li>Always stops fencing in a finally block to ensure cleanup
   * </ol>
   *
   * <p><b>Note:</b> Fencing is started synchronously by {@link #activateIntercept} before this
   * method is called. This ensures that new dispatches are blocked immediately when the
   * registration response is sent.
   *
   * <p><b>Note:</b> This method does not return a result to the original caller since it runs
   * asynchronously. Success or failure is logged, and the intercept is registered (or not) based on
   * the outcome.
   *
   * @param classPattern the class pattern from the message
   * @param methodPattern the method pattern from the message
   * @param interceptMessage the intercept message to register
   */
  private void waitForQuiescenceAndRegisterAsync(
      String classPattern, String methodPattern, InterceptMessage interceptMessage) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Async: Waiting for quiescence for {}.{}, timeout={}ms",
          classPattern,
          methodPattern,
          drainTimeoutMs);
    }

    try {
      // Step 1: Wait for quiescence (fencing was already started synchronously)
      boolean quiescent =
          inFlightTracker.waitForQuiescence(classPattern, methodPattern, drainTimeoutMs);

      if (!quiescent) {
        // Timeout occurred - do not activate
        if (logger.isWarnEnabled()) {
          logger.warn(
              "Async: Timeout waiting for quiescence of {}.{} after {}ms, intercept not activated",
              classPattern,
              methodPattern,
              drainTimeoutMs);
        }
        return;
      }

      // Step 2: Register intercept with matcher after quiescence is achieved
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Async: Quiescence achieved for {}.{}, registering intercept",
            classPattern,
            methodPattern);
      }

      try {
        interceptMatcherProvider.get().registerInterceptRequest(interceptMessage);
        if (logger.isInfoEnabled()) {
          logger.info(
              "Async: Intercept activated after drain for {}.{}", classPattern, methodPattern);
        }
      } catch (DuplicateInterceptException e) {
        if (logger.isWarnEnabled()) {
          logger.warn(
              "Async: Cannot register duplicate intercept for {}.{}",
              classPattern,
              methodPattern,
              e);
        }
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (logger.isWarnEnabled()) {
        logger.warn(
            "Async: Interrupted while activating intercept for {}.{}", classPattern, methodPattern);
      }
    } catch (Exception e) {
      if (logger.isErrorEnabled()) {
        logger.error("Async: Error activating intercept for {}.{}", classPattern, methodPattern, e);
      }
    } finally {
      // Step 3: Always stop fencing to unblock waiting threads
      inFlightTracker.stopFencing(classPattern, methodPattern);
      if (logger.isDebugEnabled()) {
        logger.debug("Async: Fencing stopped for {}.{}", classPattern, methodPattern);
      }
    }
  }

  /**
   * Extracts the method/field pattern from an intercept message.
   *
   * <p>For method intercepts, returns the method name. For field intercepts, returns the field
   * name.
   *
   * @param message the intercept message
   * @return the method or field name pattern string
   */
  private String extractMethodPatternFromMessage(InterceptMessage message) {
    if (message.getMethod() != null) {
      return message.getMethod().getName();
    } else if (message.getField() != null) {
      return message.getField().getName();
    }
    throw new IllegalArgumentException("InterceptMessage must have either method or field defined");
  }

  /**
   * Result of an intercept activation attempt.
   *
   * <p>This class encapsulates the outcome of calling {@link #activateIntercept(InterceptMessage)},
   * indicating whether the activation succeeded, is pending asynchronously, or failed.
   */
  public static class ActivationResult {
    /** Whether the activation succeeded (immediate activation). */
    private final boolean success;

    /** Whether the activation is pending asynchronously (drain in progress). */
    private final boolean asyncPending;

    /** Human-readable message describing the result. */
    private final String message;

    /**
     * Constructs a new ActivationResult.
     *
     * @param success whether the activation succeeded
     * @param asyncPending whether the activation is pending asynchronously
     * @param message a human-readable message describing the result
     */
    private ActivationResult(boolean success, boolean asyncPending, String message) {
      this.success = success;
      this.asyncPending = asyncPending;
      this.message = message;
    }

    /**
     * Creates a success result for immediate activation.
     *
     * @param message a message describing the successful activation
     * @return a success result
     */
    public static ActivationResult success(String message) {
      return new ActivationResult(true, false, message);
    }

    /**
     * Creates a failure result.
     *
     * @param message a message describing why the activation failed
     * @return a failure result
     */
    public static ActivationResult failure(String message) {
      return new ActivationResult(false, false, message);
    }

    /**
     * Creates an async pending result for drain-based activation.
     *
     * <p>This result indicates that the activation has been submitted to the async executor and
     * will complete in the background. The caller should not wait for completion.
     *
     * @param message a message describing the pending activation
     * @return an async pending result
     */
    public static ActivationResult asyncPending(String message) {
      return new ActivationResult(false, true, message);
    }

    /**
     * Returns whether the activation succeeded immediately.
     *
     * @return {@code true} if the activation succeeded, {@code false} otherwise
     */
    public boolean isSuccess() {
      return success;
    }

    /**
     * Returns whether the activation is pending asynchronously.
     *
     * <p>When this returns {@code true}, the activation has been submitted to a background thread
     * for drain-based activation. The intercept will be registered once quiescence is achieved.
     *
     * @return {@code true} if the activation is pending asynchronously, {@code false} otherwise
     */
    public boolean isAsyncPending() {
      return asyncPending;
    }

    /**
     * Returns a human-readable message describing the result.
     *
     * @return the result message
     */
    public String getMessage() {
      return message;
    }

    @Override
    public String toString() {
      return "ActivationResult{success="
          + success
          + ", asyncPending="
          + asyncPending
          + ", message='"
          + message
          + "'}";
    }
  }
}
