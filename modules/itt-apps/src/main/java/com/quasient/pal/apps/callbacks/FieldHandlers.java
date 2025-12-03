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
 * Callback handlers for field intercept integration tests.
 *
 * <p>Provides static callback methods that can be invoked via reflection for testing BEFORE and
 * AFTER intercepts on field get/put operations.
 *
 * <p><b>Field PUT operations:</b> In BEFORE phase, the value being set is passed as the first
 * argument (index 0). Use {@code ctx.getArgs()[0]} to access it and {@code ctx.setArg(0, newValue)}
 * to mutate it.
 *
 * <p><b>Field GET operations:</b> In AFTER phase, the value read is available via {@code
 * ctx.getReturnValue()}. Use {@code ctx.setReturnValue(newValue)} to override it.
 */
public class FieldHandlers {

  private static final Logger logger = LoggerFactory.getLogger(FieldHandlers.class);

  /**
   * No-op callback for testing.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse noOp(InterceptContext ctx) {
    logger.info("noOp: no mutations, phase={}", ctx.getPhase());
    return new InterceptCallbackResponse();
  }

  // ===========================================================================
  // BEFORE PUT callbacks - mutate the value being written to the field
  // ===========================================================================

  /**
   * Callback that doubles the integer value being PUT to a field.
   *
   * <p>Used for testing field PUT argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse doublePutValue(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      Integer original = (Integer) args[0];
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doublePutValue: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the integer value being PUT to a field.
   *
   * <p>Used for testing field PUT argument mutation via BEFORE callbacks.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse addHundredToPutValue(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      Integer original = (Integer) args[0];
      Integer modified = original + 100;
      ctx.setArg(0, modified);
      logger.info("addHundredToPutValue: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a SecurityException for PUT operations.
   *
   * <p>Used for testing exception propagation via BEFORE callbacks on field PUT.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwExceptionOnPut(InterceptContext ctx) {
    logger.info("throwExceptionOnPut: throwing SecurityException");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by field PUT intercept callback"));
    return response;
  }

  // ===========================================================================
  // AFTER GET callbacks - override the value read from the field
  // ===========================================================================

  /**
   * Callback that doubles the integer value returned from a field GET.
   *
   * <p>Used for testing field GET return value override via AFTER callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse doubleGetValue(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("doubleGetValue called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    Object returnValue = ctx.getReturnValue();
    if (returnValue instanceof Integer) {
      Integer original = (Integer) returnValue;
      Integer doubled = original * 2;
      ctx.setReturnValue(doubled);
      logger.info("doubleGetValue: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the integer value returned from a field GET.
   *
   * <p>Used for testing field GET return value override via AFTER callbacks.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse addHundredToGetValue(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("addHundredToGetValue called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    Object returnValue = ctx.getReturnValue();
    if (returnValue instanceof Integer) {
      Integer original = (Integer) returnValue;
      Integer modified = original + 100;
      ctx.setReturnValue(modified);
      logger.info("addHundredToGetValue: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a SecurityException for GET operations.
   *
   * <p>Used for testing exception propagation via AFTER callbacks on field GET.
   *
   * @param ctx the intercept context
   * @return callback response with exception to throw
   */
  public static InterceptCallbackResponse throwExceptionOnGet(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("throwExceptionOnGet called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    logger.info("throwExceptionOnGet: throwing SecurityException");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by field GET intercept callback"));
    return response;
  }

  /**
   * Callback that logs the GET value without modifying it.
   *
   * <p>Used for testing no-op AFTER callbacks on field GET operations.
   *
   * @param ctx the intercept context
   * @return callback response with no changes
   */
  public static InterceptCallbackResponse logGetValue(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("logGetValue called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    Object returnValue = ctx.getReturnValue();
    logger.info("logGetValue: read value={}, isVoid={}", returnValue, ctx.isVoid());
    return new InterceptCallbackResponse();
  }
}
