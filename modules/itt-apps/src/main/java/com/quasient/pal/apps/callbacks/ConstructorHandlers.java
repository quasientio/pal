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
 * Callback handlers for constructor intercept integration tests.
 *
 * <p>Provides static callback methods that can be invoked via reflection for testing BEFORE and
 * AFTER intercepts on constructors.
 */
public class ConstructorHandlers {

  private static final Logger logger = LoggerFactory.getLogger(ConstructorHandlers.class);

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
   * <p>Used for testing constructor argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse doubleFirstIntArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      Integer original = (Integer) args[0];
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doubleFirstIntArg: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the first integer argument.
   *
   * <p>Used for testing constructor argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse addHundredToFirstArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      Integer original = (Integer) args[0];
      Integer modified = original + 100;
      ctx.setArg(0, modified);
      logger.info("addHundredToFirstArg: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a custom exception.
   *
   * <p>Used for testing exception propagation via BEFORE callbacks on constructors.
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

  /**
   * Callback for AFTER phase that logs the constructed object.
   *
   * <p>Note: Constructor return values (the constructed object) are typically not wrappable for
   * serialization, so this callback only logs and does not attempt to override.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse logConstructedObject(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("logConstructedObject called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    // For constructors, the return value is the constructed object
    // We can access it but not easily override it (not wrappable)
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
   * <p>Used for testing exception propagation via AFTER callbacks on constructors.
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
}
