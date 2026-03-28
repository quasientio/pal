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
package io.quasient.foobar.apps.callbacks.exception;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhaseViolationException;
import io.quasient.pal.common.lang.intercept.InterceptTypeNotSupportedException;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for exception-related integration tests.
 *
 * <p>This class provides static callback methods that test various exception scenarios in the
 * interception system:
 *
 * <ul>
 *   <li>API misuse - Callbacks that call context methods in invalid phases (triggers {@link
 *       io.quasient.pal.common.lang.intercept.InterceptTypeNotSupportedException} or {@link
 *       io.quasient.pal.common.lang.intercept.InterceptPhaseViolationException})
 *   <li>Business exceptions - Callbacks that throw unchecked exceptions (e.g., {@link
 *       SecurityException})
 *   <li>Checked exceptions - Callbacks that throw checked exceptions (e.g., {@link SQLException})
 *   <li>Explicit exception API - Callbacks that use {@link InterceptContext#setExceptionToThrow}
 * </ul>
 *
 * <p><b>Note:</b> This class is intentionally placed in the non-quantized callbacks package to
 * avoid AspectJ weaving issues. Callback handler classes themselves should not be intercepted.
 *
 * <p><b>Thread Safety:</b> All methods are stateless and thread-safe. No shared state is maintained
 * between invocations.
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
public final class ExceptionTestCallbacks {

  /** Logger for callback invocations. */
  private static final Logger logger = LoggerFactory.getLogger(ExceptionTestCallbacks.class);

  private ExceptionTestCallbacks() {
    // Prevent instantiation
  }

  // ==================== API Misuse Callbacks ====================

  /**
   * BEFORE callback that attempts to call {@link InterceptContext#getReturnValue()}.
   *
   * <p>This is invalid API usage because return values are not available in BEFORE phase. This
   * callback should trigger an {@link InterceptTypeNotSupportedException}.
   *
   * <p><b>Expected behavior:</b> The context will throw InterceptTypeNotSupportedException when
   * getReturnValue() is called. This callback catches the exception, logs a diagnostic message for
   * integration test verification, and re-throws so it propagates to the caller.
   *
   * @param ctx the intercept context
   * @return the intercept response (not reached due to exception)
   * @throws InterceptTypeNotSupportedException always thrown after catching and logging
   */
  public static InterceptCallbackResponse apiMisuseGetReturnValueInBefore(InterceptContext ctx) {
    try {
      logger.info("API_MISUSE_GET_RETURN_IN_BEFORE: attempting invalid getReturnValue() call");
      // This will throw InterceptTypeNotSupportedException
      ctx.getReturnValue();
      logger.error("API_MISUSE_GET_RETURN_IN_BEFORE: ERROR - did not throw exception");
      return new InterceptCallbackResponse();
    } catch (InterceptTypeNotSupportedException e) {
      logger.info("API_MISUSE_GET_RETURN_IN_BEFORE: caught expected exception: {}", e.getMessage());
      throw e; // Propagate to caller
    }
  }

  /**
   * AROUND callback that attempts to call {@link InterceptContext#setArg} after {@link
   * InterceptContext#proceed()}.
   *
   * <p>This is invalid API usage because argument mutation is only allowed in BEFORE phase (before
   * proceed() is called). This callback should trigger an {@link InterceptPhaseViolationException}.
   *
   * <p><b>Expected behavior:</b> The context will throw InterceptPhaseViolationException when
   * setArg() is called after proceed(). This callback catches the exception, logs a diagnostic
   * message for integration test verification, and re-throws so it propagates to the caller.
   *
   * @param ctx the intercept context
   * @return the intercept response (not reached due to exception)
   * @throws InterceptPhaseViolationException always thrown after catching and logging
   */
  public static InterceptCallbackResponse apiMisuseSetArgAfterProceed(InterceptContext ctx) {
    logger.info("API_MISUSE_SET_ARG_AFTER_PROCEED: calling proceed()");
    ctx.proceed();
    try {
      logger.info("API_MISUSE_SET_ARG_AFTER_PROCEED: attempting invalid setArg() call");
      // This will throw InterceptPhaseViolationException
      ctx.setArg(0, 999);
      logger.error("API_MISUSE_SET_ARG_AFTER_PROCEED: ERROR - did not throw exception");
      return new InterceptCallbackResponse();
    } catch (InterceptPhaseViolationException e) {
      logger.info(
          "API_MISUSE_SET_ARG_AFTER_PROCEED: caught expected exception: {}", e.getMessage());
      throw e; // Propagate to caller
    }
  }

  // ==================== Business Exception Callbacks ====================

  /**
   * Callback that throws a {@link SecurityException} (unchecked exception).
   *
   * <p>This callback tests how the interception system handles business logic exceptions thrown
   * directly by callback handlers. The exception should propagate according to the configured
   * exception policy.
   *
   * <p><b>Use cases:</b>
   *
   * <ul>
   *   <li>Security checks that reject requests (e.g., access control)
   *   <li>Rate limiting that blocks execution
   *   <li>Validation that rejects invalid input
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response (not reached due to exception)
   * @throws SecurityException always thrown to test exception handling
   */
  public static InterceptCallbackResponse throwSecurityException(InterceptContext ctx) {
    logger.info("THROW_SECURITY_EXCEPTION: throwing SecurityException");
    throw new SecurityException("Access denied by callback");
  }

  /**
   * Callback that throws a {@link SQLException} (checked exception).
   *
   * <p>This callback tests how the interception system handles checked exceptions thrown by
   * callback handlers. Note that callbacks returning {@link InterceptCallbackResponse} cannot
   * declare checked exceptions, so this must be wrapped in {@link RuntimeException} or similar.
   *
   * <p><b>Note:</b> This method cannot declare "throws SQLException" because the callback signature
   * doesn't support checked exceptions. We throw it wrapped in RuntimeException to simulate a
   * scenario where callback code encounters a checked exception.
   *
   * @param ctx the intercept context
   * @return the intercept response (not reached due to exception)
   * @throws RuntimeException wrapping SQLException to test checked exception handling
   */
  public static InterceptCallbackResponse throwSqlException(InterceptContext ctx) {
    logger.info("THROW_SQL_EXCEPTION: throwing SQLException wrapped in RuntimeException");
    throw new RuntimeException(new SQLException("Database error in callback", "08000"));
  }

  // ==================== Explicit Exception API Callbacks ====================

  /**
   * Callback that sets an exception via {@link InterceptContext#setExceptionToThrow}.
   *
   * <p>This callback tests the explicit exception API, where callbacks use the context to set an
   * exception to be thrown on the intercepted peer instead of throwing directly. This is the
   * recommended approach for BEFORE intercepts that want to prevent method execution by rejecting
   * the call.
   *
   * <p><b>Difference from throwing directly:</b>
   *
   * <ul>
   *   <li><b>Throwing directly:</b> Exception is caught by callback dispatcher, handled as callback
   *       failure
   *   <li><b>Using setExceptionToThrow:</b> Exception is sent back to intercepted peer and thrown
   *       there, controlled by callback logic
   * </ul>
   *
   * <p><b>Example use cases:</b>
   *
   * <ul>
   *   <li>BEFORE: Reject method execution with a specific exception (e.g., access control)
   *   <li>AFTER: Transform or replace exception thrown by the intercepted method
   *   <li>AROUND: Either of the above, depending on whether proceed() was called
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse setExplicitException(InterceptContext ctx) {
    logger.info("SET_EXPLICIT_EXCEPTION: setting SecurityException via ctx.setExceptionToThrow()");
    ctx.setExceptionToThrow(new SecurityException("Access denied via explicit API"));
    return new InterceptCallbackResponse();
  }
}
