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
package io.quasient.pal.common.lang.intercept;

/**
 * Defines policies for propagating exceptions thrown during intercept callback execution.
 *
 * <p>When an intercept callback throws an exception, this policy determines whether and how that
 * exception should propagate to the caller of the intercepted method. Different policies provide
 * different trade-offs between callback error visibility and system resilience.
 *
 * <p>This policy works in conjunction with {@link CheckedExceptionPolicy} to provide comprehensive
 * exception handling control for the interception system.
 *
 * @see InterceptCallback
 * @see CheckedExceptionPolicy
 * @see InvalidCallbackExceptionException
 */
public enum ExceptionPropagationPolicy {

  /**
   * All exceptions thrown by intercept callbacks propagate to the caller.
   *
   * <p>This policy provides maximum visibility into callback failures but may cause the intercepted
   * method to fail even when the callback logic is non-critical. Use this policy when:
   *
   * <ul>
   *   <li>Callback failures should prevent the intercepted operation from completing
   *   <li>Debugging callback behavior and need full exception visibility
   *   <li>The callback represents critical validation or authorization logic
   * </ul>
   *
   * <p><b>Example:</b> An authorization callback that throws {@code UnauthorizedException} should
   * propagate that exception to prevent unauthorized method execution.
   */
  PROPAGATE_ALL((byte) 0),

  /**
   * Only exceptions explicitly set via {@link InterceptCallbackResponse#setExceptionToThrow}
   * propagate to the caller.
   *
   * <p>This policy distinguishes between intentional exception propagation (where the callback
   * explicitly chooses to throw an exception) and accidental callback errors (bugs in callback
   * code). Accidental errors are logged but do not propagate. Use this policy when:
   *
   * <ul>
   *   <li>Callbacks should be able to signal exceptional conditions without exposing internal
   *       errors
   *   <li>You want to prevent callback bugs from breaking the intercepted application
   *   <li>Logging callback failures is sufficient for debugging
   * </ul>
   *
   * <p><b>Example:</b> A monitoring callback that throws {@code NullPointerException} due to a bug
   * will be logged but won't break the monitored method. However, if the callback explicitly sets
   * an exception via {@code setExceptionToThrow()}, that exception will propagate.
   */
  PROPAGATE_EXPLICIT_ONLY((byte) 1),

  /**
   * All exceptions thrown by intercept callbacks are swallowed and logged.
   *
   * <p>This policy maximizes resilience by ensuring callback failures never impact the intercepted
   * method. Exceptions are logged for debugging but do not propagate. Use this policy when:
   *
   * <ul>
   *   <li>Callbacks perform non-critical tasks (logging, metrics, monitoring)
   *   <li>System availability is more important than callback correctness
   *   <li>You want to add observability without risk of breaking existing functionality
   * </ul>
   *
   * <p><b>Example:</b> A metrics collection callback should not cause application failures even if
   * the metrics system is misconfigured or throws exceptions.
   *
   * <p><b>Warning:</b> This policy can hide serious errors in callback logic. Use with caution and
   * ensure proper logging and monitoring are in place.
   */
  SWALLOW_ALL((byte) 2),

  /**
   * Only exceptions from successfully-executed callbacks that explicitly set an exception
   * propagate.
   *
   * <p>This policy is the most restrictive, requiring both:
   *
   * <ol>
   *   <li>The callback must complete successfully (not throw an exception during execution)
   *   <li>The callback must explicitly call {@link InterceptCallbackResponse#setExceptionToThrow}
   * </ol>
   *
   * <p>Use this policy when:
   *
   * <ul>
   *   <li>You want maximum isolation between callback errors and application logic
   *   <li>Only controlled, deliberate exceptions should propagate
   *   <li>Any callback execution error indicates a bug that should be logged but not propagated
   * </ul>
   *
   * <p><b>Example:</b> A validation callback that successfully checks preconditions and explicitly
   * sets {@code ValidationException} will propagate that exception. However, if the callback throws
   * {@code NullPointerException} due to a bug, that will be logged but not propagated.
   *
   * <p>This is the recommended policy for production systems where callback stability is important
   * but controlled exception signaling is needed.
   */
  PROPAGATE_CONTROLLED_ONLY((byte) 3);

  /** The byte identifier associated with this exception propagation policy. */
  private final byte idx;

  /**
   * Constructs an {@code ExceptionPropagationPolicy} with the specified byte identifier.
   *
   * @param idx the byte value representing this policy
   */
  ExceptionPropagationPolicy(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code ExceptionPropagationPolicy}.
   *
   * @param policyAsByte the byte value representing an exception propagation policy
   * @return the {@code ExceptionPropagationPolicy} corresponding to the provided byte
   * @throws IllegalArgumentException if the byte value does not match any defined policy
   * @see #toByte()
   */
  public static ExceptionPropagationPolicy fromByte(byte policyAsByte) {
    return switch (policyAsByte) {
      case 0 -> PROPAGATE_ALL;
      case 1 -> PROPAGATE_EXPLICIT_ONLY;
      case 2 -> SWALLOW_ALL;
      case 3 -> PROPAGATE_CONTROLLED_ONLY;
      default ->
          throw new IllegalArgumentException(
              "Unknown exception propagation policy: " + policyAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this {@code ExceptionPropagationPolicy}.
   *
   * @return the byte value representing this policy
   * @see #fromByte(byte)
   */
  public byte toByte() {
    return idx;
  }
}
