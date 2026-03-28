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
package io.quasient.foobar.apps.callbacks.field;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.ProceedResult;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for field intercept integration tests.
 *
 * <p>Provides static callback methods for testing BEFORE, AFTER, AROUND, and ASYNC intercepts on
 * field GET and PUT operations. These handlers are organized by intercept type for clarity.
 *
 * <p><b>Field PUT operations:</b> The value being set is passed as the first argument (index 0).
 *
 * <p><b>Field GET operations:</b> The value read is available via {@code ctx.getReturnValue()}.
 */
@SuppressWarnings("unused")
public class FieldHandlers {

  private static final Logger logger = LoggerFactory.getLogger(FieldHandlers.class);

  // <editor-fold desc="BEFORE Callbacks">

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

  /**
   * Callback that doubles the integer value being PUT to a field.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse doublePutValue(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doublePutValue: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the integer value being PUT to a field.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse addHundredToPutValue(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer modified = original + 100;
      ctx.setArg(0, modified);
      logger.info("addHundredToPutValue: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a SecurityException for PUT operations.
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

  // </editor-fold>
  // <editor-fold desc="AFTER Callbacks">

  /**
   * Callback that doubles the integer value returned from a field GET.
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
    if (returnValue instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setReturnValue(doubled);
      logger.info("doubleGetValue: {} -> {}", original, doubled);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that adds 100 to the integer value returned from a field GET.
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
    if (returnValue instanceof Integer original) {
      Integer modified = original + 100;
      ctx.setReturnValue(modified);
      logger.info("addHundredToGetValue: {} -> {}", original, modified);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that throws a SecurityException for GET operations.
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

  // </editor-fold>
  // <editor-fold desc="AROUND Callbacks">

  /**
   * Callback for field PUT that doubles the value being set before proceeding.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse doublePutValueAndProceed(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doublePutValueAndProceed: {} -> {}", original, doubled);
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback for field GET that doubles the returned value after proceeding.
   *
   * @param ctx the intercept context
   * @return callback response with overridden value
   */
  public static InterceptCallbackResponse doubleGetValueAfterProceed(InterceptContext ctx) {
    ProceedResult result = ctx.proceed();

    if (!result.hasException()) {
      Object returnValue = result.getReturnValue();
      if (returnValue instanceof Integer original) {
        Integer doubled = original * 2;
        ctx.setReturnValue(doubled);
        logger.info("doubleGetValueAfterProceed: {} -> {}", original, doubled);
      }
    }

    return new InterceptCallbackResponse();
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
    logger.info("logArgs (field BEFORE_ASYNC): args = {}", Arrays.toString(args));
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
    logger.info("logReturnValue (field AFTER_ASYNC): returnValue = {}", returnValue);
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the PUT value is 42.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse verifyPutValueIs42(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0) {
      Object firstArg = args[0];
      if (Integer.valueOf(42).equals(firstArg)) {
        logger.info("verifyPutValueIs42: verified PUT value is 42");
      } else {
        logger.error("verifyPutValueIs42: expected 42 but got '{}' - throwing exception", firstArg);
        throw new AssertionError("Expected PUT value to be 42 but was: " + firstArg);
      }
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that verifies the GET value is 100.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse verifyGetValueIs100(InterceptContext ctx) {
    Object returnValue = ctx.getReturnValue();
    if (Integer.valueOf(100).equals(returnValue)) {
      logger.info("verifyGetValueIs100: verified GET value is 100");
    } else {
      logger.error(
          "verifyGetValueIs100: expected 100 but got '{}' - throwing exception", returnValue);
      throw new AssertionError("Expected GET value to be 100 but was: " + returnValue);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to mutate PUT value (should throw UnsupportedOperationException).
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   */
  public static InterceptCallbackResponse attemptPutValueMutation(InterceptContext ctx) {
    logger.info("attemptPutValueMutation (BEFORE_ASYNC): attempting to mutate PUT value");
    ctx.setArg(0, 999);
    logger.error("attemptPutValueMutation: setArg did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to override GET value (should throw UnsupportedOperationException).
   *
   * @param ctx the intercept context
   * @return callback response (unreachable if exception is thrown)
   */
  public static InterceptCallbackResponse attemptGetValueOverride(InterceptContext ctx) {
    logger.info("attemptGetValueOverride (AFTER_ASYNC): attempting to override GET value");
    ctx.setReturnValue(999);
    logger.error("attemptGetValueOverride: setReturnValue did NOT throw - this is a bug!");
    return new InterceptCallbackResponse();
  }

  // </editor-fold>
}
