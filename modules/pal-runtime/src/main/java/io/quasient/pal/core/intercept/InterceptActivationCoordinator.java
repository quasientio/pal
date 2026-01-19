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

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.core.service.RunOptions;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Set;
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
 *   <li><b>Fence new dispatches:</b> If drain is required, start fencing to block new matching
 *       dispatches
 *   <li><b>Wait for quiescence:</b> Wait for in-flight dispatches to complete within the configured
 *       timeout
 *   <li><b>Activate intercept:</b> Register the intercept with InterceptMatcher once quiescence is
 *       achieved
 *   <li><b>Unfence:</b> Stop fencing to allow new dispatches to proceed (now intercepted)
 * </ol>
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
 * <p><b>Activation result:</b> The {@link #activate(InterceptRequest)} method returns an {@link
 * ActivationResult} indicating success or failure, along with details about what happened during
 * activation.
 *
 * <p><b>Thread safety:</b> This class is thread-safe and may be called concurrently from multiple
 * threads.
 *
 * <p><b>Usage example:</b>
 *
 * <pre>{@code
 * InterceptActivationCoordinator coordinator = ...;
 * InterceptRequest<?> request = ...;
 *
 * ActivationResult result = coordinator.activate(request);
 * if (result.isSuccess()) {
 *   logger.info("Intercept activated successfully");
 * } else {
 *   logger.warn("Intercept activation failed: {}", result.getMessage());
 * }
 * }</pre>
 *
 * @see InFlightDispatchTracker
 * @see InterceptMatcher
 * @see RunOptions#WITH_IN_FLIGHT_TRACKING
 * @see InterceptRequest#isForceImmediate()
 */
@Singleton
public class InterceptActivationCoordinator {

  /** Logger instance for this class. */
  private static final Logger logger =
      LoggerFactory.getLogger(InterceptActivationCoordinator.class);

  /** Tracker for in-flight dispatches, used to fence and wait for quiescence. */
  private final InFlightDispatchTracker inFlightTracker;

  /**
   * Matcher for registering and querying intercepts.
   *
   * <p>Note: Currently unused as direct registration is not yet implemented (TODO(#243)). This
   * field is retained for future integration when InterceptMatcher supports direct registration.
   */
  @SuppressWarnings("unused")
  private final InterceptMatcher interceptMatcher;

  /** Runtime options controlling peer behavior. */
  private final Set<RunOptions> runOptions;

  /** Timeout in milliseconds for waiting for in-flight dispatches to drain. */
  private final long drainTimeoutMs;

  /**
   * Constructs a new InterceptActivationCoordinator with the specified dependencies.
   *
   * @param inFlightTracker the tracker for in-flight dispatches
   * @param interceptMatcher the matcher for registering intercepts
   * @param runOptions the runtime options controlling peer behavior
   * @param drainTimeoutMs the timeout in milliseconds for waiting for in-flight dispatches to drain
   *     (injected from properties as "intercept.drain.timeout.ms")
   */
  @Inject
  public InterceptActivationCoordinator(
      InFlightDispatchTracker inFlightTracker,
      InterceptMatcher interceptMatcher,
      Set<RunOptions> runOptions,
      @Named("intercept.drain.timeout.ms") long drainTimeoutMs) {
    this.inFlightTracker = inFlightTracker;
    this.interceptMatcher = interceptMatcher;
    this.runOptions = runOptions;
    this.drainTimeoutMs = drainTimeoutMs;

    if (logger.isDebugEnabled()) {
      logger.debug(
          "InterceptActivationCoordinator initialized with drainTimeoutMs={}, WITH_IN_FLIGHT_TRACKING={}",
          drainTimeoutMs,
          runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING));
    }
  }

  /**
   * Activates an intercept request, coordinating with in-flight dispatch tracking as needed.
   *
   * <p>This method determines whether drain is required based on the global {@code
   * WITH_IN_FLIGHT_TRACKING} option and the per-intercept {@code forceImmediate} flag. If drain is
   * required, it:
   *
   * <ol>
   *   <li>Starts fencing for the intercept pattern
   *   <li>Waits for in-flight dispatches to complete (quiescence)
   *   <li>Activates the intercept if quiescence is achieved
   *   <li>Stops fencing
   * </ol>
   *
   * <p>If drain is not required (tracking disabled or forceImmediate=true), the intercept is
   * activated immediately without fencing or waiting.
   *
   * <p><b>Error handling:</b> If activation fails (e.g., due to duplicate intercept), fencing is
   * always cleaned up (stopFencing) to avoid leaving threads blocked.
   *
   * @param request the intercept request to activate
   * @return an {@link ActivationResult} indicating success or failure
   * @throws InterruptedException if the thread is interrupted while waiting for quiescence
   */
  public ActivationResult activate(InterceptRequest<?> request) throws InterruptedException {
    String classPattern = request.getClazz();
    String methodPattern = extractMethodPattern(request);

    boolean trackingEnabled = runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING);
    boolean forceImmediate = request.isForceImmediate();

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
      return activateWithDrain(classPattern, methodPattern);
    } else {
      return activateImmediate(classPattern, methodPattern);
    }
  }

  /**
   * Activates an intercept immediately without waiting for in-flight dispatches to complete.
   *
   * <p>This method is used when:
   *
   * <ul>
   *   <li>Global in-flight tracking is disabled, or
   *   <li>The intercept has forceImmediate=true (per-intercept override)
   * </ul>
   *
   * @param classPattern the class pattern from the request
   * @param methodPattern the method pattern from the request
   * @return an {@link ActivationResult} indicating success or failure
   */
  private ActivationResult activateImmediate(String classPattern, String methodPattern) {
    if (logger.isDebugEnabled()) {
      logger.debug("Activating intercept immediately for {}.{}", classPattern, methodPattern);
    }

    // Note: In the current InterceptMatcher implementation, intercepts are registered
    // via ZMQ REP socket, not via direct method call. This is a design limitation
    // that will be addressed in issue #243. For now, we return success assuming
    // the intercept will be registered via the existing ZMQ mechanism.
    //
    // TODO(#243): Replace this with direct call to InterceptMatcher.registerIntercept()
    return ActivationResult.success("Intercept activated immediately");
  }

  /**
   * Activates an intercept after waiting for in-flight dispatches to complete (drain).
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Starts fencing for the intercept pattern
   *   <li>Waits for in-flight dispatches to complete (quiescence) with configured timeout
   *   <li>If quiescence is achieved, activates the intercept
   *   <li>Always stops fencing in a finally block to ensure cleanup
   * </ol>
   *
   * @param classPattern the class pattern from the request
   * @param methodPattern the method pattern from the request
   * @return an {@link ActivationResult} indicating success or failure
   * @throws InterruptedException if the thread is interrupted while waiting for quiescence
   */
  private ActivationResult activateWithDrain(String classPattern, String methodPattern)
      throws InterruptedException {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Activating intercept with drain for {}.{}, timeout={}ms",
          classPattern,
          methodPattern,
          drainTimeoutMs);
    }

    // Step 1: Start fencing
    inFlightTracker.startFencing(classPattern, methodPattern);

    try {
      // Step 2: Wait for quiescence
      boolean quiescent =
          inFlightTracker.waitForQuiescence(classPattern, methodPattern, drainTimeoutMs);

      if (!quiescent) {
        // Timeout occurred
        if (logger.isWarnEnabled()) {
          logger.warn(
              "Timeout waiting for quiescence of {}.{} after {}ms, intercept not activated",
              classPattern,
              methodPattern,
              drainTimeoutMs);
        }
        return ActivationResult.failure(
            String.format(
                "Timeout waiting for in-flight dispatches to complete after %dms", drainTimeoutMs));
      }

      // Step 3: Activate intercept
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Quiescence achieved for {}.{}, activating intercept", classPattern, methodPattern);
      }

      // Note: Similar to activateImmediate(), direct activation via InterceptMatcher
      // is not yet supported. This will be addressed in issue #243.
      //
      // TODO(#243): Replace this with direct call to InterceptMatcher.registerIntercept()

      return ActivationResult.success("Intercept activated after drain");

    } catch (Exception e) {
      // Activation failed (e.g., duplicate intercept, parsing error)
      if (logger.isErrorEnabled()) {
        logger.error("Error activating intercept for {}.{}", classPattern, methodPattern, e);
      }
      return ActivationResult.failure("Error during intercept activation: " + e.getMessage());
    } finally {
      // Step 4: Always stop fencing to unblock waiting threads
      inFlightTracker.stopFencing(classPattern, methodPattern);
      if (logger.isDebugEnabled()) {
        logger.debug("Fencing stopped for {}.{}", classPattern, methodPattern);
      }
    }
  }

  /**
   * Extracts the method pattern from an intercept request.
   *
   * <p>The method pattern is derived from the interceptable field of the request. For method calls,
   * it's the method name. For field operations, it's the field name.
   *
   * <p>The serialized format for InterceptableMethodCall is "methodName&&paramType1&&paramType2..."
   * where && is the field separator. We extract just the methodName part.
   *
   * @param request the intercept request
   * @return the method/field pattern string
   */
  private String extractMethodPattern(InterceptRequest<?> request) {
    // The interceptable's pattern string varies by type:
    // - InterceptableMethodCall: methodName&&paramType1&&paramType2...
    // - InterceptableFieldOp: fieldName
    // We need to extract just the name part before the field separator
    String pattern = request.getInterceptable().toSerializedString();

    // For method calls, the pattern includes parameter types separated by "&&"
    // We want just "methodName" for the pattern
    int sepIndex = pattern.indexOf("&&");
    if (sepIndex > 0) {
      return pattern.substring(0, sepIndex);
    }

    return pattern;
  }

  /**
   * Result of an intercept activation attempt.
   *
   * <p>This class encapsulates the outcome of calling {@link #activate(InterceptRequest)},
   * indicating whether the activation succeeded and providing details about what happened.
   */
  public static class ActivationResult {
    /** Whether the activation succeeded. */
    private final boolean success;

    /** Human-readable message describing the result. */
    private final String message;

    /**
     * Constructs a new ActivationResult.
     *
     * @param success whether the activation succeeded
     * @param message a human-readable message describing the result
     */
    private ActivationResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    /**
     * Creates a success result.
     *
     * @param message a message describing the successful activation
     * @return a success result
     */
    public static ActivationResult success(String message) {
      return new ActivationResult(true, message);
    }

    /**
     * Creates a failure result.
     *
     * @param message a message describing why the activation failed
     * @return a failure result
     */
    public static ActivationResult failure(String message) {
      return new ActivationResult(false, message);
    }

    /**
     * Returns whether the activation succeeded.
     *
     * @return {@code true} if the activation succeeded, {@code false} otherwise
     */
    public boolean isSuccess() {
      return success;
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
      return "ActivationResult{success=" + success + ", message='" + message + "'}";
    }
  }
}
