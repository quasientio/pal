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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/**
 * Result of {@link InterceptContext#proceed()} for AROUND intercepts.
 *
 * <p>Contains the return value and/or exception from the intercepted method execution. Callbacks
 * can inspect this to decide how to handle the result.
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * ProceedResult result = ctx.proceed();
 *
 * if (result.hasException()) {
 *     // Handle exception
 *     logger.error("Method threw", result.getThrownException());
 * } else {
 *     // Use return value
 *     Object value = result.getReturnValue();
 * }
 * }</pre>
 *
 * @see InterceptContext#proceed()
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Callback API - exception must be accessible to callback handlers")
public final class ProceedResult {

  /** The return value from the intercepted method (null if void or exception thrown). */
  @Nullable private final Object returnValue;

  /** The exception thrown by the intercepted method (null if normal completion). */
  @Nullable private final Throwable thrownException;

  /**
   * Constructs a new ProceedResult.
   *
   * @param returnValue the method's return value (null if void or exception)
   * @param thrownException the exception thrown by the method (null if normal completion)
   */
  public ProceedResult(@Nullable Object returnValue, @Nullable Throwable thrownException) {
    this.returnValue = returnValue;
    this.thrownException = thrownException;
  }

  /**
   * Returns the method's return value.
   *
   * <p>This is {@code null} if:
   *
   * <ul>
   *   <li>The method is void
   *   <li>The method threw an exception
   *   <li>The method explicitly returned null
   * </ul>
   *
   * @return the return value, or null
   */
  @Nullable
  public Object getReturnValue() {
    return returnValue;
  }

  /**
   * Returns the exception thrown by the method.
   *
   * @return the exception, or null if normal completion
   */
  @Nullable
  public Throwable getThrownException() {
    return thrownException;
  }

  /**
   * Returns whether the method threw an exception.
   *
   * @return {@code true} if the method threw, {@code false} if it completed normally
   */
  public boolean hasException() {
    return thrownException != null;
  }

  /**
   * Returns the return value or throws if the method threw.
   *
   * <p>Convenience method for callbacks that want exception propagation.
   *
   * @return the return value
   * @throws Throwable if the method threw an exception
   */
  @Nullable
  public Object getReturnValueOrThrow() throws Throwable {
    if (thrownException != null) {
      throw thrownException;
    }
    return returnValue;
  }
}
