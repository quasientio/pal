/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for ASYNC intercept integration tests (BEFORE_ASYNC and AFTER_ASYNC).
 *
 * <p>ASYNC callbacks are fire-and-forget - they cannot mutate arguments or override return values.
 * These handlers test that restriction and verify that args/return values can still be read.
 */
public class AsyncCallbackHandlers {

  private static final Logger logger = LoggerFactory.getLogger(AsyncCallbackHandlers.class);

  /**
   * Callback that logs arguments (no mutation - for BEFORE_ASYNC).
   *
   * <p>Used for testing that BEFORE_ASYNC callbacks can read arguments.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse logArgs(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    logger.info("logArgs (BEFORE_ASYNC): args = {}", Arrays.toString(args));
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that logs return value (no override - for AFTER_ASYNC).
   *
   * <p>Used for testing that AFTER_ASYNC callbacks can read return values.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse logReturnValue(InterceptContext ctx) {
    Object returnValue = ctx.getReturnValue();
    logger.info("logReturnValue (AFTER_ASYNC): returnValue = {}", returnValue);
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to mutate arguments (should throw UnsupportedOperationException).
   *
   * <p>Used for testing that BEFORE_ASYNC callbacks cannot mutate arguments.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   * @throws UnsupportedOperationException always thrown because ASYNC cannot mutate
   */
  public static InterceptCallbackResponse attemptArgMutation(InterceptContext ctx) {
    logger.info("attemptArgMutation (BEFORE_ASYNC): attempting to mutate arg 0");
    // This should throw UnsupportedOperationException for BEFORE_ASYNC
    ctx.setArg(0, "MUTATED");
    // If we get here, something is wrong
    logger.error("attemptArgMutation: setArg did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to override return value (should throw UnsupportedOperationException).
   *
   * <p>Used for testing that AFTER_ASYNC callbacks cannot override return values.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   * @throws UnsupportedOperationException always thrown because ASYNC cannot override
   */
  public static InterceptCallbackResponse attemptReturnOverride(InterceptContext ctx) {
    logger.info("attemptReturnOverride (AFTER_ASYNC): attempting to override return value");
    // This should throw UnsupportedOperationException for AFTER_ASYNC
    ctx.setReturnValue("OVERRIDDEN");
    // If we get here, something is wrong
    logger.error("attemptReturnOverride: setReturnValue did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the first argument equals expected value.
   *
   * <p>Used for testing that BEFORE_ASYNC callbacks receive correct argument values.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse verifyFirstArgIsHello(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0) {
      Object firstArg = args[0];
      if ("hello".equals(firstArg)) {
        logger.info("verifyFirstArgIsHello: verified first arg is 'hello'");
      } else {
        logger.error(
            "verifyFirstArgIsHello: expected 'hello' but got '{}' - throwing exception", firstArg);
        throw new AssertionError("Expected first arg to be 'hello' but was: " + firstArg);
      }
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the return value equals expected value.
   *
   * <p>Used for testing that AFTER_ASYNC callbacks receive correct return values.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse verifyReturnValueIsHello(InterceptContext ctx) {
    Object returnValue = ctx.getReturnValue();
    if ("hello".equals(returnValue)) {
      logger.info("verifyReturnValueIsHello: verified return value is 'hello'");
    } else {
      logger.error(
          "verifyReturnValueIsHello: expected 'hello' but got '{}' - throwing exception",
          returnValue);
      throw new AssertionError("Expected return value to be 'hello' but was: " + returnValue);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the first argument is an Integer with value 42.
   *
   * <p>Used for testing that BEFORE_ASYNC callbacks on constructors receive correct argument
   * values.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse verifyFirstArgIs42(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0) {
      Object firstArg = args[0];
      if (Integer.valueOf(42).equals(firstArg)) {
        logger.info("verifyFirstArgIs42: verified first arg is 42");
      } else {
        logger.error("verifyFirstArgIs42: expected 42 but got '{}' - throwing exception", firstArg);
        throw new AssertionError("Expected first arg to be 42 but was: " + firstArg);
      }
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to mutate an Integer argument (for constructor BEFORE_ASYNC tests).
   *
   * <p>Used for testing that BEFORE_ASYNC callbacks on constructors cannot mutate arguments.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   * @throws UnsupportedOperationException always thrown because ASYNC cannot mutate
   */
  public static InterceptCallbackResponse attemptIntArgMutation(InterceptContext ctx) {
    logger.info("attemptIntArgMutation (BEFORE_ASYNC): attempting to mutate Integer arg 0");
    // This should throw UnsupportedOperationException for BEFORE_ASYNC
    ctx.setArg(0, Integer.valueOf(999));
    // If we get here, something is wrong
    logger.error("attemptIntArgMutation: setArg did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to throw an exception via setExceptionToThrow (should throw
   * UnsupportedOperationException).
   *
   * <p>Used for testing that ASYNC callbacks (both BEFORE_ASYNC and AFTER_ASYNC) cannot affect
   * execution flow by throwing exceptions.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown correctly)
   * @throws UnsupportedOperationException always thrown because ASYNC cannot throw exceptions
   */
  public static InterceptCallbackResponse attemptThrowException(InterceptContext ctx) {
    logger.info(
        "attemptThrowException ({}): attempting to set exception to throw", ctx.getInterceptType());
    // This should throw UnsupportedOperationException for both BEFORE_ASYNC and AFTER_ASYNC
    ctx.setExceptionToThrow(new SecurityException("This should not propagate"));
    // If we get here, something is wrong
    logger.error("attemptThrowException: setExceptionToThrow did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }
}
