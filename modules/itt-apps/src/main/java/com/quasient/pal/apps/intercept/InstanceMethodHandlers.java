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

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for instance method intercept integration tests.
 *
 * <p>Provides static callback methods that can be invoked via reflection for testing BEFORE
 * intercepts on instance methods.
 */
public class InstanceMethodHandlers {

  private static final Logger logger = LoggerFactory.getLogger(InstanceMethodHandlers.class);

  /**
   * Callback that doubles the first integer argument.
   *
   * <p>Used for testing single BEFORE callback on instance methods.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse doubleMultiplier(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      Integer original = (Integer) args[0];
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doubleMultiplier: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

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
}
