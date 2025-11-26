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
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for AFTER intercept integration tests.
 *
 * <p>Provides static callback methods that can be invoked via reflection for testing return value
 * override via AFTER intercept callbacks. These handlers are in the itt-apps module so they are
 * available on the intercepted peer's classpath.
 */
public class AfterCallbackHandlers {

  private static final Logger logger = LoggerFactory.getLogger(AfterCallbackHandlers.class);

  /**
   * Callback that converts string return value to uppercase.
   *
   * <p>Used for testing simple return value override via AFTER callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse uppercaseReturnValue(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("uppercaseReturnValue called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    Object returnValue = ctx.getReturnValue();
    if (returnValue instanceof String) {
      String original = (String) returnValue;
      String uppercased = original.toUpperCase();
      ctx.setReturnValue(uppercased);
      logger.info("uppercaseReturnValue: {} -> {}", original, uppercased);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles an integer return value.
   *
   * <p>Used for testing primitive return value override via AFTER callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse doubleReturnValue(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("doubleReturnValue called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    Object returnValue = ctx.getReturnValue();
    if (returnValue instanceof Integer) {
      Integer original = (Integer) returnValue;
      Integer doubled = original * 2;
      ctx.setReturnValue(doubled);
      logger.info("doubleReturnValue: {} -> {}", original, doubled);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a custom exception in AFTER phase.
   *
   * <p>Used for testing exception propagation via AFTER callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwException(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("throwException called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    logger.info("throwException: throwing SecurityException");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by AFTER intercept callback"));
    return response;
  }

  /**
   * Callback that attempts to set return value on a void method (should throw).
   *
   * <p>Used for testing that void method handling correctly prevents return value modification.
   * This callback intentionally tries to call setReturnValue() on a void method, which should throw
   * IllegalStateException.
   *
   * @param ctx the intercept context
   * @return callback response (unreachable - should throw)
   */
  public static InterceptCallbackResponse attemptSetReturnValueOnVoid(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("attemptSetReturnValueOnVoid called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    // This should throw IllegalStateException for void methods
    logger.info("attemptSetReturnValueOnVoid: attempting to set return value on void method");
    ctx.setReturnValue("should fail"); // Will throw IllegalStateException

    // Should never reach here
    logger.error(
        "attemptSetReturnValueOnVoid: UNEXPECTED - setReturnValue succeeded on void method!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback for void methods that verifies isVoid() returns true.
   *
   * <p>Used for testing void method handling in AFTER callbacks. Simply verifies that the context
   * correctly identifies the method as void.
   *
   * @param ctx the intercept context
   * @return callback response with no changes
   */
  public static InterceptCallbackResponse checkIsVoid(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("checkIsVoid called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    if (!ctx.isVoid()) {
      // Throw exception if method is not void (test expects void)
      throw new AssertionError("Expected void method but isVoid() returned false");
    }

    logger.info("checkIsVoid: confirmed method is void");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that does nothing (no override).
   *
   * <p>Used for testing no-op callback behavior for AFTER intercepts.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse noOp(InterceptContext ctx) {
    logger.info("noOp: no return value override");
    return new InterceptCallbackResponse();
  }
}
