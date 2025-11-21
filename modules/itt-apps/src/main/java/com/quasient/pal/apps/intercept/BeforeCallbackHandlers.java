/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.intercept;

import com.quasient.pal.core.intercept.InterceptCallbackResponse;
import com.quasient.pal.core.intercept.InterceptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for BEFORE intercept integration tests.
 *
 * <p>Provides static callback methods that can be invoked via reflection for testing argument
 * mutation via BEFORE intercept callbacks. These handlers are in the itt-apps module so they are
 * available on the intercepted peer's classpath.
 */
public class BeforeCallbackHandlers {

  private static final Logger logger = LoggerFactory.getLogger(BeforeCallbackHandlers.class);

  /**
   * Callback that converts first string argument to uppercase.
   *
   * <p>Used for testing single-argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse uppercaseFirstArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof String) {
      String original = (String) args[0];
      String uppercased = original.toUpperCase();
      ctx.setArg(0, uppercased);
      logger.info("uppercaseFirstArg: {} -> {}", original, uppercased);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that converts both string arguments to uppercase.
   *
   * <p>Used for testing multi-argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse uppercaseBothArgs(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length >= 2 && args[0] instanceof String && args[1] instanceof String) {
      String original0 = (String) args[0];
      String original1 = (String) args[1];
      String uppercased0 = original0.toUpperCase();
      String uppercased1 = original1.toUpperCase();
      ctx.setArg(0, uppercased0);
      ctx.setArg(1, uppercased1);
      logger.info(
          "uppercaseBothArgs: ({}, {}) -> ({}, {})",
          original0,
          original1,
          uppercased0,
          uppercased1);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles the first integer argument.
   *
   * <p>Used for testing primitive argument mutation via BEFORE callbacks.
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
   * Callback that throws a custom exception.
   *
   * <p>Used for testing exception propagation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwException(InterceptContext ctx) {
    logger.info("throwException: throwing SecurityException");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(new SecurityException("Access denied by intercept callback"));
    return response;
  }

  /**
   * Callback that does nothing (no mutation).
   *
   * <p>Used for testing no-op callback behavior.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse noOp(InterceptContext ctx) {
    logger.info("noOp: no mutations");
    return new InterceptCallbackResponse();
  }
}
