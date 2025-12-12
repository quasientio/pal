/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.method;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.ProceedResult;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for method intercept integration tests.
 *
 * <p>Provides static callback methods for testing BEFORE, AFTER, AROUND, and ASYNC intercepts on
 * methods. These handlers are organized by intercept type for clarity.
 */
@SuppressWarnings("unused")
public class MethodHandlers {

  private static final Logger logger = LoggerFactory.getLogger(MethodHandlers.class);

  /** Cache for AROUND caching callbacks. */
  private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<>();

  /** Clears the static cache. Call this between tests to ensure isolation. */
  public static void clearCache() {
    CACHE.clear();
    logger.info("clearCache: cache cleared");
  }

  /**
   * Pre-populates the cache with a key-value pair for testing cache hits.
   *
   * @param key the cache key
   * @param value the cached value
   */
  public static void populateCache(String key, Object value) {
    CACHE.put(key, value);
    logger.info("populateCache: key={}, value={}", key, value);
  }

  // <editor-fold desc="BEFORE Callbacks">

  /**
   * Callback that converts first string argument to uppercase.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse uppercaseFirstArg(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof String original) {
      String uppercased = original.toUpperCase(Locale.ROOT);
      ctx.setArg(0, uppercased);
      logger.info("uppercaseFirstArg: {} -> {}", original, uppercased);
    }
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that converts both string arguments to uppercase.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed
   */
  public static InterceptCallbackResponse uppercaseBothArgs(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length >= 2
        && args[0] instanceof String original0
        && args[1] instanceof String original1) {
      String uppercased0 = original0.toUpperCase(Locale.ROOT);
      String uppercased1 = original1.toUpperCase(Locale.ROOT);
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
   * Callback that throws a custom exception.
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
   * Callback that attempts to get return value in BEFORE phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptGetReturnValueInBefore(InterceptContext ctx) {
    logger.info("attemptGetReturnValueInBefore: attempting to get return value in BEFORE phase");
    try {
      Object value = ctx.getReturnValue();
      logger.error(
          "attemptGetReturnValueInBefore: getReturnValue did NOT throw - got value: {}", value);
      throw new AssertionError(
          "Expected UnsupportedOperationException but getReturnValue() returned: " + value);
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptGetReturnValueInBefore: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that attempts to get thrown exception in BEFORE phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptGetThrownExceptionInBefore(InterceptContext ctx) {
    logger.info(
        "attemptGetThrownExceptionInBefore: attempting to get thrown exception in BEFORE phase");
    try {
      Throwable value = ctx.getThrownException();
      logger.error(
          "attemptGetThrownExceptionInBefore: getThrownException did NOT throw - got: {}",
          (Object) value);
      throw new AssertionError(
          "Expected UnsupportedOperationException but getThrownException() returned: " + value);
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptGetThrownExceptionInBefore: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that attempts to set return value in BEFORE phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptSetReturnValueInBefore(InterceptContext ctx) {
    logger.info("attemptSetReturnValueInBefore: attempting to set return value in BEFORE phase");
    try {
      ctx.setReturnValue("should fail");
      logger.error("attemptSetReturnValueInBefore: setReturnValue did NOT throw!");
      throw new AssertionError(
          "Expected UnsupportedOperationException but setReturnValue() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptSetReturnValueInBefore: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that sets exception to throw in BEFORE phase via InterceptContext.
   *
   * <p>This verifies that BEFORE intercepts can reject execution by throwing an exception. Use
   * cases include security checks, validation, and rate limiting.
   *
   * @param ctx the intercept context
   * @return callback response (exception is set on context)
   */
  public static InterceptCallbackResponse setExceptionViaContextInBefore(InterceptContext ctx) {
    logger.info(
        "setExceptionViaContextInBefore: setting SecurityException via ctx.setExceptionToThrow()");
    ctx.setExceptionToThrow(new SecurityException("Access denied by BEFORE intercept via context"));
    logger.info("setExceptionViaContextInBefore: exception set successfully");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to call proceed() in BEFORE phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptProceedInBefore(InterceptContext ctx) {
    logger.info("attemptProceedInBefore: attempting to call proceed() in BEFORE intercept");
    try {
      ctx.proceed();
      logger.error("attemptProceedInBefore: proceed() did NOT throw!");
      throw new AssertionError("Expected UnsupportedOperationException but proceed() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptProceedInBefore: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  // </editor-fold>
  // <editor-fold desc="AFTER Callbacks">

  /**
   * Callback that converts string return value to uppercase.
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
    if (returnValue instanceof String original) {
      String uppercased = original.toUpperCase(Locale.ROOT);
      ctx.setReturnValue(uppercased);
      logger.info("uppercaseReturnValue: {} -> {}", original, uppercased);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles an integer return value.
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
    if (returnValue instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setReturnValue(doubled);
      logger.info("doubleReturnValue: {} -> {}", original, doubled);
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

    logger.info("throwExceptionAfter: throwing SecurityException");
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setExceptionToThrow(
        new SecurityException("Access denied by AFTER intercept callback"));
    return response;
  }

  /**
   * Callback that attempts to set return value on a void method (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (unreachable - should throw)
   */
  public static InterceptCallbackResponse attemptSetReturnValueOnVoid(InterceptContext ctx) {
    if (ctx.getPhase() != InterceptPhase.AFTER) {
      logger.warn("attemptSetReturnValueOnVoid called in wrong phase: {}", ctx.getPhase());
      return new InterceptCallbackResponse();
    }

    logger.info("attemptSetReturnValueOnVoid: attempting to set return value on void method");
    ctx.setReturnValue("should fail");
    logger.error(
        "attemptSetReturnValueOnVoid: UNEXPECTED - setReturnValue succeeded on void method!");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback for void methods that verifies isVoid() returns true.
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
      throw new AssertionError("Expected void method but isVoid() returned false");
    }

    logger.info("checkIsVoid: confirmed method is void");
    return new InterceptCallbackResponse();
  }

  /**
   * No-op callback for AFTER phase.
   *
   * @param ctx the intercept context
   * @return callback response allowing proceed with no changes
   */
  public static InterceptCallbackResponse noOpAfter(InterceptContext ctx) {
    logger.info("noOpAfter: no return value override");
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to mutate arguments in AFTER phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptSetArgInAfter(InterceptContext ctx) {
    logger.info("attemptSetArgInAfter: attempting to set arg in AFTER phase");
    try {
      ctx.setArg(0, "should fail");
      logger.error("attemptSetArgInAfter: setArg did NOT throw!");
      throw new AssertionError("Expected UnsupportedOperationException but setArg() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptSetArgInAfter: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that attempts to call proceed() in AFTER phase (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptProceedInAfter(InterceptContext ctx) {
    logger.info("attemptProceedInAfter: attempting to call proceed() in AFTER intercept");
    try {
      ctx.proceed();
      logger.error("attemptProceedInAfter: proceed() did NOT throw!");
      throw new AssertionError("Expected UnsupportedOperationException but proceed() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptProceedInAfter: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  // </editor-fold>
  // <editor-fold desc="AROUND Callbacks">

  /**
   * Callback that implements caching: returns cached value on hit, proceeds and caches on miss.
   *
   * @param ctx the intercept context
   * @return callback response (skipProceed on hit, default on miss)
   */
  public static InterceptCallbackResponse cachingCallback(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    String cacheKey = args.length > 0 ? String.valueOf(args[0]) : "";

    Object cached = CACHE.get(cacheKey);
    if (cached != null) {
      logger.info(
          "cachingCallback: cache HIT for key={}, returning cached value={}", cacheKey, cached);
      ctx.setReturnValue(cached);
      return InterceptCallbackResponse.skipProceed();
    }

    logger.info("cachingCallback: cache MISS for key={}, proceeding", cacheKey);
    ProceedResult result = ctx.proceed();

    if (!result.hasException()) {
      Object returnValue = result.getReturnValue();
      CACHE.put(cacheKey, returnValue);
      logger.info("cachingCallback: cached result for key={}, value={}", cacheKey, returnValue);
    } else {
      logger.info("cachingCallback: method threw exception, not caching");
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that converts first string argument to uppercase before proceeding.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse uppercaseFirstArgBeforeProceed(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof String original) {
      String uppercased = original.toUpperCase(Locale.ROOT);
      ctx.setArg(0, uppercased);
      logger.info("uppercaseFirstArgBeforeProceed: {} -> {}", original, uppercased);
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles the first integer argument before proceeding.
   *
   * @param ctx the intercept context
   * @return callback response after proceeding
   */
  public static InterceptCallbackResponse doubleFirstIntArgBeforeProceed(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer original) {
      Integer doubled = original * 2;
      ctx.setArg(0, doubled);
      logger.info("doubleFirstIntArgBeforeProceed: {} -> {}", original, doubled);
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that converts string return value to uppercase after proceeding.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse uppercaseReturnAfterProceed(InterceptContext ctx) {
    ProceedResult result = ctx.proceed();

    if (!result.hasException()) {
      Object returnValue = result.getReturnValue();
      if (returnValue instanceof String original) {
        String uppercased = original.toUpperCase(Locale.ROOT);
        ctx.setReturnValue(uppercased);
        logger.info("uppercaseReturnAfterProceed: {} -> {}", original, uppercased);
      }
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that doubles the integer return value after proceeding.
   *
   * @param ctx the intercept context
   * @return callback response with overridden return value
   */
  public static InterceptCallbackResponse doubleReturnAfterProceed(InterceptContext ctx) {
    ProceedResult result = ctx.proceed();

    if (!result.hasException()) {
      Object returnValue = result.getReturnValue();
      if (returnValue instanceof Integer original) {
        Integer doubled = original * 2;
        ctx.setReturnValue(doubled);
        logger.info("doubleReturnAfterProceed: {} -> {}", original, doubled);
      }
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Callback that always skips execution and returns a hardcoded value.
   *
   * @param ctx the intercept context
   * @return callback response with skipProceed
   */
  public static InterceptCallbackResponse skipAndReturnHardcodedValue(InterceptContext ctx) {
    logger.info("skipAndReturnHardcodedValue: skipping execution, returning 42");
    ctx.setReturnValue(42);
    return InterceptCallbackResponse.skipProceed();
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
   * Callback that skips execution without setting return value (INVALID).
   *
   * <p>This callback intentionally violates the contract that skipProceed() requires either
   * setReturnValue() or setExceptionToThrow(). The server should reject this with an
   * IllegalStateException.
   *
   * @param ctx the intercept context
   * @return callback response with skipProceed but no return value
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

  /**
   * Callback that attempts to get return value before proceed() (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown and proceeding)
   */
  public static InterceptCallbackResponse attemptGetReturnValueBeforeProceed(InterceptContext ctx) {
    logger.info(
        "attemptGetReturnValueBeforeProceed: attempting to get return value before proceed()");
    try {
      Object value = ctx.getReturnValue();
      logger.error(
          "attemptGetReturnValueBeforeProceed: getReturnValue did NOT throw - got: {}", value);
      throw new AssertionError(
          "Expected IllegalStateException but getReturnValue() returned: " + value);
    } catch (IllegalStateException e) {
      logger.info(
          "attemptGetReturnValueBeforeProceed: correctly threw IllegalStateException: {}",
          e.getMessage());
    }
    // Proceed to verify test completes successfully
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to get thrown exception before proceed() (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown and proceeding)
   */
  public static InterceptCallbackResponse attemptGetThrownExceptionBeforeProceed(
      InterceptContext ctx) {
    logger.info(
        "attemptGetThrownExceptionBeforeProceed: attempting to get thrown exception before proceed()");
    try {
      Throwable value = ctx.getThrownException();
      logger.error(
          "attemptGetThrownExceptionBeforeProceed: getThrownException did NOT throw - got: {}",
          (Object) value);
      throw new AssertionError(
          "Expected IllegalStateException but getThrownException() returned: " + value);
    } catch (IllegalStateException e) {
      logger.info(
          "attemptGetThrownExceptionBeforeProceed: correctly threw IllegalStateException: {}",
          e.getMessage());
    }
    // Proceed to verify test completes successfully
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to set arg after proceed() (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptSetArgAfterProceed(InterceptContext ctx) {
    logger.info("attemptSetArgAfterProceed: proceeding first, then attempting to set arg");
    ctx.proceed();
    try {
      ctx.setArg(0, "should fail");
      logger.error("attemptSetArgAfterProceed: setArg did NOT throw after proceed()!");
      throw new AssertionError(
          "Expected IllegalStateException but setArg() succeeded after proceed()");
    } catch (IllegalStateException e) {
      logger.info(
          "attemptSetArgAfterProceed: correctly threw IllegalStateException: {}", e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

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
    logger.info("logArgs (BEFORE_ASYNC): args = {}", Arrays.toString(args));
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
    logger.info("logReturnValue (AFTER_ASYNC): returnValue = {}", returnValue);
    return new InterceptCallbackResponse();
  }

  /**
   * Callback that attempts to mutate arguments in BEFORE_ASYNC (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptArgMutation(InterceptContext ctx) {
    logger.info("attemptArgMutation (BEFORE_ASYNC): attempting to mutate arg 0");
    try {
      ctx.setArg(0, "MUTATED");
      logger.error("attemptArgMutation: setArg did NOT throw - this is a bug!");
      throw new AssertionError("Expected UnsupportedOperationException but setArg() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptArgMutation: correctly threw UnsupportedOperationException: {}", e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that attempts to override return value in AFTER_ASYNC (should throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptReturnOverride(InterceptContext ctx) {
    logger.info("attemptReturnOverride (AFTER_ASYNC): attempting to override return value");
    try {
      ctx.setReturnValue("OVERRIDDEN");
      logger.error("attemptReturnOverride: setReturnValue did NOT throw - this is a bug!");
      throw new AssertionError(
          "Expected UnsupportedOperationException but setReturnValue() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptReturnOverride: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Callback that verifies the first argument equals "hello".
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
   * Callback that verifies the return value equals "hello".
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
   * Callback that attempts to throw an exception via setExceptionToThrow in ASYNC context (should
   * throw).
   *
   * @param ctx the intercept context
   * @return callback response (after verifying exception is thrown)
   */
  public static InterceptCallbackResponse attemptThrowException(InterceptContext ctx) {
    logger.info(
        "attemptThrowException ({}): attempting to set exception to throw", ctx.getInterceptType());
    try {
      ctx.setExceptionToThrow(new SecurityException("This should not propagate"));
      logger.error("attemptThrowException: setExceptionToThrow did NOT throw - this is a bug!");
      throw new AssertionError(
          "Expected UnsupportedOperationException but setExceptionToThrow() succeeded");
    } catch (UnsupportedOperationException e) {
      logger.info(
          "attemptThrowException: correctly threw UnsupportedOperationException: {}",
          e.getMessage());
      return new InterceptCallbackResponse();
    }
  }

  // </editor-fold>
}
