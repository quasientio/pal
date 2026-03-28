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
