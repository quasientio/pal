/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.constructor;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.ProceedResult;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for constructor intercept integration tests.
 *
 * <p>Provides static callback methods for testing BEFORE, AFTER, AROUND, and ASYNC intercepts on
 * constructors. These handlers are organized by intercept type for clarity.
 */
@SuppressWarnings("unused")
public class ConstructorHandlers {

  private static final Logger logger = LoggerFactory.getLogger(ConstructorHandlers.class);

  // <editor-fold desc="BEFORE Callbacks">

  /**
   * No-op callback for testing.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse noOp(InterceptContext ctx) {
    logger.info("noOp: no mutations");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles the first integer argument.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse doubleFirstIntArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doubleFirstIntArg: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the first integer argument.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse addHundredToFirstArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer modified = original + 100;
      ctx.setArg(0, modified);
      logger.info("addHundredToFirstArg: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a custom exception.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwException(InterceptContext ctx) {
    logger.info("throwException: throwing SecurityException from constructor callback");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by constructor intercept callback"));
    return response;
  }

  // </editor-fold>
  // <editor-fold desc="AFTER Callbacks">

  /**
   * Callback for AFTER phase that logs the constructed object.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse logConstructedObject(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("logConstructedObject called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    logger.info("logConstructedObject: isVoid={}", ctx.isVoid());
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      logger.info("logConstructedObject: constructed object class={}", returnValue.getClass());
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a custom exception in AFTER phase.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwExceptionAfter(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("throwExceptionAfter called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    logger.info("throwExceptionAfter: throwing SecurityException from AFTER constructor callback");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by AFTER constructor intercept callback"));
    return response;
  }

  // </editor-fold>
  // <editor-fold desc="AROUND Callbacks">

  /**
   * Callback for constructor that doubles the integer constructor argument before proceeding.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse doubleConstructorArgAndProceed(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doubleConstructorArgAndProceed: {} -> {}", original, doubled);
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback for constructor that logs the constructed object after proceeding.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse logConstructedObjectAfterProceed(InterceptContext ctx) {
    ProceedResult result = ctx.proceed();

    if (!result.hasException()) {
      Object constructed = result.getReturnValue();
      logger.info(
          "logConstructedObjectAfterProceed: constructed object of type {}",
          constructed != null ? constructed.getClass().getSimpleName() : "null");
    } else {
      logger.info(
          "logConstructedObjectAfterProceed: constructor threw {}",
          result.getThrownException().getClass().getSimpleName());
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that skips execution and throws an exception.
   *
   * @param ctx the intercept context
   * @return callback response with exception
   */
  public static InterceptCallbackResponse skipAndThrowException(InterceptContext ctx) {
    logger.info("skipAndThrowException: skipping execution, throwing SecurityException");
    return InterceptCallbackResponse.throwException(
        new SecurityException("Access denied by AROUND intercept callback"));
  }

  /**
   * Callback that simply proceeds with no modifications.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse noOpAround(InterceptContext ctx) {
    logger.info("noOp: proceeding with no modifications");
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  // CPD-OFF - These test callbacks are intentionally duplicated across handler classes
  /**
   * Callback that skips execution without setting a return value (INVALID).
   *
   * <p>This callback intentionally violates the skipProceed() contract by not calling
   * ctx.setReturnValue() or ctx.setExceptionToThrow() before skipProceed(). The server should throw
   * IllegalStateException.
   *
   * @param ctx the intercept context
   * @return callback response with skipProceed but no return value set
   */
  public static InterceptCallbackResponse skipWithoutReturnValue(InterceptContext ctx) {
    logger.info(
        "skipWithoutReturnValue: skipping execution without setting return value (INVALID)");
    // Intentionally NOT calling ctx.setReturnValue() or ctx.setExceptionToThrow()
    return InterceptCallbackResponse.skipProceed();
  }

  /**
   * Callback that skips execution and sets return value to null (VALID).
   *
   * <p>This callback explicitly sets the return value to null, which is valid. The server should
   * accept this and return null to the caller.
   *
   * @param ctx the intercept context
   * @return callback response with skipProceed and null return value
   */
  public static InterceptCallbackResponse skipWithNullReturnValue(InterceptContext ctx) {
    logger.info("skipWithNullReturnValue: skipping execution with explicit null return value");
    ctx.setReturnValue(null);
    return InterceptCallbackResponse.skipProceed();
  }

  // CPD-ON

  // </editor-fold>
  // <editor-fold desc="ASYNC Callbacks">

  /**
   * Callback that logs arguments (no mutation - for BEFORE_ASYNC).
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse logArgs(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    logger.info("logArgs (constructor BEFORE_ASYNC): args = {}", Arrays.toString(args));
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that logs return value (no override - for AFTER_ASYNC).
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse logReturnValue(InterceptContext ctx) {
    Object returnValue = ctx.getReturnValue();
    logger.info("logReturnValue (constructor AFTER_ASYNC): returnValue = {}", returnValue);
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the first argument is an Integer with value 42.
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
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   */
  public static InterceptCallbackResponse attemptIntArgMutation(InterceptContext ctx) {
    logger.info("attemptIntArgMutation (BEFORE_ASYNC): attempting to mutate Integer arg 0");
    ctx.setArg(0, 999);
    logger.error("attemptIntArgMutation: setArg did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to throw an exception via setExceptionToThrow.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown correctly)
   */
  public static InterceptCallbackResponse attemptThrowException(InterceptContext ctx) {
    logger.info(
        "attemptThrowException ({}): attempting to set exception to throw", ctx.getInterceptType());
    ctx.setExceptionToThrow(new SecurityException("This should not propagate"));
    logger.error("attemptThrowException: setExceptionToThrow did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  // </editor-fold>
}
