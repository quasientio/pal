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

import javax.annotation.Nullable;

/**
 * Response object returned by {@link InterceptCallback#handle(InterceptContext)} to control
 * intercept execution flow.
 *
 * <p>This class provides control over:
 *
 * <ul>
 *   <li><b>Proceed control (AROUND only, BEFORE phase):</b> Whether the intercepted method should
 *       execute
 *   <li><b>Exception throwing:</b> Whether to throw an exception instead of normal execution/return
 *   <li><b>Argument/return value modification:</b> Done via {@link InterceptContext} methods, not
 *       this response
 * </ul>
 *
 * <p><b>Default Behavior:</b>
 *
 * <ul>
 *   <li>{@link #shouldProceed} = {@code true} (method executes normally)
 *   <li>{@link #exceptionToThrow} = {@code null} (no exception thrown)
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Normal case: proceed with execution, no modifications
 * return new InterceptCallbackResponse();
 *
 * // AROUND: skip method execution and provide custom return value via context
 * InterceptCallbackResponse response = new InterceptCallbackResponse();
 * response.setShouldProceed(false);
 * ctx.setReturnValue(cachedValue);
 * return response;
 *
 * // Throw an exception instead of executing/returning
 * InterceptCallbackResponse response = new InterceptCallbackResponse();
 * response.setExceptionToThrow(new SecurityException("Access denied"));
 * return response;
 *
 * // Alternative: throw directly from callback (same effect)
 * throw new SecurityException("Access denied");
 * }</pre>
 *
 * @see InterceptCallback
 * @see InterceptContext
 */
public class InterceptCallbackResponse {

  /**
   * Whether the intercepted method should proceed with execution.
   *
   * <p>This field is only meaningful for {@link InterceptType#AROUND} intercepts in the {@link
   * InterceptPhase#BEFORE} phase. For other intercept types and phases, this field is ignored.
   *
   * <p>Default: {@code true} (proceed with execution)
   *
   * <p>If set to {@code false}, the intercepted method will not execute, and the callback should
   * provide a return value via {@link InterceptContext#setReturnValue(Object)} or throw an
   * exception.
   */
  private boolean shouldProceed = true;

  /**
   * An exception to throw instead of normal execution or return.
   *
   * <p>If non-null, this exception will be thrown on the intercepted peer instead of:
   *
   * <ul>
   *   <li><b>BEFORE phase:</b> Instead of executing the method
   *   <li><b>AFTER phase:</b> Instead of returning the method's result
   * </ul>
   *
   * <p>Default: {@code null} (no exception)
   *
   * <p><b>Note:</b> Callbacks can also throw exceptions directly from {@link
   * InterceptCallback#handle(InterceptContext)}, which has the same effect as setting this field.
   */
  @Nullable private Throwable exceptionToThrow;

  /** Default constructor with default values (proceed=true, no exception). */
  public InterceptCallbackResponse() {}

  /**
   * Returns whether the intercepted method should proceed with execution.
   *
   * <p>Only meaningful for {@link InterceptType#AROUND} intercepts in {@link InterceptPhase#BEFORE}
   * phase.
   *
   * @return {@code true} if the method should execute, {@code false} to skip execution
   */
  public boolean isShouldProceed() {
    return shouldProceed;
  }

  /**
   * Sets whether the intercepted method should proceed with execution.
   *
   * <p>This is only meaningful for {@link InterceptType#AROUND} intercepts in the {@link
   * InterceptPhase#BEFORE} phase.
   *
   * <p>If set to {@code false}, the intercepted method will not execute. The callback should
   * either:
   *
   * <ul>
   *   <li>Provide a return value via {@link InterceptContext#setReturnValue(Object)}, or
   *   <li>Throw an exception via {@link #setExceptionToThrow(Throwable)} or by throwing from the
   *       callback
   * </ul>
   *
   * @param shouldProceed {@code true} to execute the method, {@code false} to skip
   * @return this response instance for method chaining
   */
  public InterceptCallbackResponse setShouldProceed(boolean shouldProceed) {
    this.shouldProceed = shouldProceed;
    return this;
  }

  /**
   * Returns the exception to throw instead of normal execution or return.
   *
   * @return the exception, or {@code null} if no exception should be thrown
   */
  @Nullable
  public Throwable getExceptionToThrow() {
    return exceptionToThrow;
  }

  /**
   * Sets an exception to throw instead of normal execution or return.
   *
   * <p>If set, this exception will be thrown on the intercepted peer instead of:
   *
   * <ul>
   *   <li><b>BEFORE phase:</b> Instead of executing the method
   *   <li><b>AFTER phase:</b> Instead of returning the method's result or original exception
   * </ul>
   *
   * <p><b>Alternative:</b> Callbacks can also throw exceptions directly from {@link
   * InterceptCallback#handle(InterceptContext)}, which has the same effect.
   *
   * @param exceptionToThrow the exception to throw, or {@code null} for normal execution/return
   * @return this response instance for method chaining
   */
  public InterceptCallbackResponse setExceptionToThrow(@Nullable Throwable exceptionToThrow) {
    this.exceptionToThrow = exceptionToThrow;
    return this;
  }

  /**
   * Creates a response that skips method execution (for AROUND intercepts).
   *
   * <p>Equivalent to:
   *
   * <pre>{@code
   * InterceptCallbackResponse response = new InterceptCallbackResponse();
   * response.setShouldProceed(false);
   * }</pre>
   *
   * @return a new response with shouldProceed=false
   */
  public static InterceptCallbackResponse skipProceed() {
    return new InterceptCallbackResponse().setShouldProceed(false);
  }

  /**
   * Creates a response that throws an exception.
   *
   * <p>Equivalent to:
   *
   * <pre>{@code
   * InterceptCallbackResponse response = new InterceptCallbackResponse();
   * response.setExceptionToThrow(exception);
   * }</pre>
   *
   * @param exception the exception to throw
   * @return a new response with the specified exception
   */
  public static InterceptCallbackResponse throwException(Throwable exception) {
    return new InterceptCallbackResponse().setExceptionToThrow(exception);
  }
}
