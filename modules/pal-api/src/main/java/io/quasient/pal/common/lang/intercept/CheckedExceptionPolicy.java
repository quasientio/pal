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
 * Defines policies for handling checked exceptions thrown by intercept callbacks.
 *
 * <p>Java's type system enforces that methods can only throw checked exceptions declared in their
 * {@code throws} clause. When an intercept callback attempts to throw a checked exception that is
 * not in the intercepted method's declared exceptions list, this policy determines how to handle
 * the violation.
 *
 * <p>This policy applies to exceptions set via {@link
 * InterceptCallbackResponse#setExceptionToThrow} that would violate the intercepted method's
 * exception contract.
 *
 * @see InterceptCallback
 * @see ExceptionPropagationPolicy
 * @see InvalidCallbackExceptionException
 */
public enum CheckedExceptionPolicy {

  /**
   * Wrap invalid checked exceptions in {@link RuntimeException}.
   *
   * <p>When a callback attempts to throw a checked exception that is not declared by the
   * intercepted method, the exception is wrapped in a {@link RuntimeException} (or a custom
   * unchecked wrapper if configured) and thrown.
   *
   * <p>Use this policy when:
   *
   * <ul>
   *   <li>Callbacks may legitimately need to signal exceptional conditions with checked exceptions
   *   <li>You want to preserve the original exception information
   *   <li>Callers can handle runtime exceptions appropriately
   *   <li>You prefer automatic handling over explicit validation
   * </ul>
   *
   * <p><b>Example:</b> A callback throws {@code IOException} but the intercepted method only
   * declares {@code throws SQLException}. The {@code IOException} is wrapped in {@code
   * RuntimeException} and thrown, preserving the original exception as the cause.
   *
   * <p><b>Trade-off:</b> Provides maximum flexibility but may hide type system violations that
   * indicate programming errors.
   */
  WRAP((byte) 0),

  /**
   * Reject invalid checked exceptions by throwing {@link InvalidCallbackExceptionException}.
   *
   * <p>When a callback attempts to throw a checked exception that is not declared by the
   * intercepted method, the interception system throws {@link InvalidCallbackExceptionException} to
   * signal that the callback violated the exception contract.
   *
   * <p>Use this policy when:
   *
   * <ul>
   *   <li>Type safety is critical and violations should be detected immediately
   *   <li>You want to fail fast on callback misconfiguration
   *   <li>Callbacks should respect the intercepted method's exception contract
   *   <li>Programming errors should be obvious and not silently handled
   * </ul>
   *
   * <p><b>Example:</b> A callback throws {@code IOException} but the intercepted method only
   * declares {@code throws SQLException}. The interception system throws {@code
   * InvalidCallbackExceptionException} to indicate that the callback is attempting an invalid
   * operation.
   *
   * <p><b>Trade-off:</b> Provides strong type safety but requires callbacks to be carefully
   * designed to match the intercepted method's exception contract. Best for development and testing
   * environments where strict validation is preferred.
   */
  REJECT((byte) 1),

  /**
   * Allow all exceptions without validation.
   *
   * <p>No validation is performed on checked exceptions. The callback can set any exception via
   * {@link InterceptCallbackResponse#setExceptionToThrow}, regardless of whether it appears in the
   * intercepted method's {@code throws} clause.
   *
   * <p>Use this policy when:
   *
   * <ul>
   *   <li>Maximum flexibility is required and type safety is not a concern
   *   <li>You are migrating legacy code and need backward compatibility
   *   <li>You want to avoid any runtime overhead from exception type checking
   *   <li>Callers already handle unexpected exception types defensively
   * </ul>
   *
   * <p><b>Example:</b> A callback throws {@code IOException} and the intercepted method only
   * declares {@code throws SQLException}. The {@code IOException} propagates directly without
   * wrapping or validation.
   *
   * <p><b>Warning:</b> This policy bypasses Java's checked exception system, which can lead to
   * unexpected {@link java.lang.reflect.UndeclaredThrowableException} if the caller is not prepared
   * to handle arbitrary exception types. This was the default behavior in earlier versions for
   * backward compatibility but is not recommended for new code.
   *
   * <p><b>Trade-off:</b> Provides maximum flexibility but completely bypasses type safety,
   * potentially leading to runtime surprises and {@link
   * java.lang.reflect.UndeclaredThrowableException} in reflection or proxy scenarios.
   */
  ALLOW_ALL((byte) 2);

  /** The byte identifier associated with this checked exception policy. */
  private final byte idx;

  /**
   * Constructs a {@code CheckedExceptionPolicy} with the specified byte identifier.
   *
   * @param idx the byte value representing this policy
   */
  CheckedExceptionPolicy(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code CheckedExceptionPolicy}.
   *
   * @param policyAsByte the byte value representing a checked exception policy
   * @return the {@code CheckedExceptionPolicy} corresponding to the provided byte
   * @throws IllegalArgumentException if the byte value does not match any defined policy
   * @see #toByte()
   */
  public static CheckedExceptionPolicy fromByte(byte policyAsByte) {
    return switch (policyAsByte) {
      case 0 -> WRAP;
      case 1 -> REJECT;
      case 2 -> ALLOW_ALL;
      default ->
          throw new IllegalArgumentException("Unknown checked exception policy: " + policyAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this {@code CheckedExceptionPolicy}.
   *
   * @return the byte value representing this policy
   * @see #fromByte(byte)
   */
  public byte toByte() {
    return idx;
  }
}
