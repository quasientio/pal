/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

/**
 * Thrown when AROUND {@code proceed()} times out waiting for method execution.
 *
 * <p>This occurs when the interceptable peer does not send the AFTER phase request within the
 * configured timeout. Callbacks can catch this exception to handle timeout gracefully:
 *
 * <pre>{@code
 * try {
 *     ProceedResult result = ctx.proceed();
 *     // ... handle result
 * } catch (AroundTimeoutException e) {
 *     // Handle timeout - maybe return fallback value
 *     ctx.setReturnValue(fallbackValue);
 *     return new InterceptCallbackResponse();
 * }
 * }</pre>
 *
 * @see InterceptContext#proceed()
 */
public class AroundTimeoutException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new AroundTimeoutException with the specified message.
   *
   * @param message the detail message
   */
  public AroundTimeoutException(String message) {
    super(message);
  }

  /**
   * Constructs a new AroundTimeoutException with the specified message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public AroundTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
