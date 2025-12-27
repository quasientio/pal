/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.chain;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback handlers for testing AROUND intercept chain behavior.
 *
 * <p>These callbacks test the "onion" model where local AROUND intercepts wrap remote AROUND
 * intercepts, and each layer's proceed() invokes the next layer rather than the method directly.
 *
 * <p>The chain order should be:
 *
 * <pre>
 * Local AROUND-1 BEFORE (outermost)
 *   → Local AROUND-2 BEFORE
 *     → Remote AROUND-1 BEFORE
 *       → [METHOD EXECUTION] (innermost)
 *     ← Remote AROUND-1 AFTER
 *   ← Local AROUND-2 AFTER
 * ← Local AROUND-1 AFTER
 * </pre>
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
@SuppressFBWarnings(
    value = {"EI_EXPOSE_STATIC_REP2", "MS_EXPOSE_REP"},
    justification = "Test helper class; mutable static state is intentional for test coordination")
public final class AroundChainCallbacks {

  /** Logger for callback invocations. */
  private static final Logger logger = LoggerFactory.getLogger(AroundChainCallbacks.class);

  /** Invocation counter for outerDoubler callback. */
  private static final AtomicInteger outerDoublerCount = new AtomicInteger(0);

  /** Invocation counter for middleLogger callback. */
  private static final AtomicInteger middleLoggerCount = new AtomicInteger(0);

  /** Invocation counter for caching callback. */
  private static final AtomicInteger cachingCount = new AtomicInteger(0);

  /** Invocation counter for exception thrower callback. */
  private static final AtomicInteger exceptionThrowerCount = new AtomicInteger(0);

  /** Invocation counter for exception suppressor callback. */
  private static final AtomicInteger exceptionSuppressorCount = new AtomicInteger(0);

  /** Invocation counter for exception replacer callback. */
  private static final AtomicInteger exceptionReplacerCount = new AtomicInteger(0);

  /** Cache for caching callback. Key: method+args hash, Value: cached return value. */
  private static final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

  /** Flag to track if cache was hit on last call. */
  private static volatile boolean lastCallWasCacheHit = false;

  private AroundChainCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    outerDoublerCount.set(0);
    middleLoggerCount.set(0);
    cachingCount.set(0);
    exceptionThrowerCount.set(0);
    exceptionSuppressorCount.set(0);
    exceptionReplacerCount.set(0);
    cache.clear();
    lastCallWasCacheHit = false;
    logger.info("AROUND_CHAIN_CALLBACKS: state reset");
  }

  // ==================== Getters ====================

  /**
   * Returns the invocation count for outerDoubler.
   *
   * @return the count
   */
  public static int getOuterDoublerCount() {
    return outerDoublerCount.get();
  }

  /**
   * Returns the invocation count for middleLogger.
   *
   * @return the count
   */
  public static int getMiddleLoggerCount() {
    return middleLoggerCount.get();
  }

  /**
   * Returns the invocation count for caching callback.
   *
   * @return the count
   */
  public static int getCachingCount() {
    return cachingCount.get();
  }

  /**
   * Returns whether the last caching call was a cache hit.
   *
   * @return true if cache hit, false if cache miss
   */
  public static boolean wasLastCallCacheHit() {
    return lastCallWasCacheHit;
  }

  /**
   * Returns the invocation count for exception thrower callback.
   *
   * @return the count
   */
  public static int getExceptionThrowerCount() {
    return exceptionThrowerCount.get();
  }

  /**
   * Returns the invocation count for exception suppressor callback.
   *
   * @return the count
   */
  public static int getExceptionSuppressorCount() {
    return exceptionSuppressorCount.get();
  }

  /**
   * Returns the invocation count for exception replacer callback.
   *
   * @return the count
   */
  public static int getExceptionReplacerCount() {
    return exceptionReplacerCount.get();
  }

  // ==================== AROUND Callbacks for Chain Testing ====================

  /**
   * Outermost local AROUND callback that doubles the return value.
   *
   * <p>This callback:
   *
   * <ol>
   *   <li>Logs entry with sequence number
   *   <li>Calls proceed() to invoke the next layer
   *   <li>Doubles the return value (if integer)
   *   <li>Logs exit with original and modified values
   * </ol>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse outerDoubler(InterceptContext ctx) {
    int seq = outerDoublerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: outerDoubler BEFORE, seq={}", seq);

    // Proceed to next layer
    ctx.proceed();

    // Double the return value
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int doubled = original * 2;
        ctx.setReturnValue(doubled);
        logger.info("AROUND_CHAIN: outerDoubler AFTER, seq={}, {} -> {}", seq, original, doubled);
      } else {
        logger.info(
            "AROUND_CHAIN: outerDoubler AFTER, seq={}, return not Integer: {}", seq, returnValue);
      }
    } else {
      logger.info("AROUND_CHAIN: outerDoubler AFTER, seq={}, void method", seq);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Middle local AROUND callback that logs but doesn't modify return value.
   *
   * <p>This callback:
   *
   * <ol>
   *   <li>Logs entry with sequence number
   *   <li>Calls proceed() to invoke the next layer
   *   <li>Logs exit with return value (no modification)
   * </ol>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse middleLogger(InterceptContext ctx) {
    int seq = middleLoggerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: middleLogger BEFORE, seq={}", seq);

    // Proceed to next layer
    ctx.proceed();

    // Log but don't modify
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      logger.info("AROUND_CHAIN: middleLogger AFTER, seq={}, return={}", seq, returnValue);
    } else {
      logger.info("AROUND_CHAIN: middleLogger AFTER, seq={}, void method", seq);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback implementing a simple cache.
   *
   * <p>This callback:
   *
   * <ol>
   *   <li>Generates a cache key from method name and args
   *   <li>If cached value exists, returns it WITHOUT calling proceed()
   *   <li>If not cached, calls proceed() and caches the result
   * </ol>
   *
   * <p>This tests that when a local AROUND skips proceed(), the entire inner chain (including
   * remote AROUND intercepts) is skipped.
   *
   * @param ctx the intercept context
   * @return the intercept response (with skipProceed if cache hit)
   */
  public static InterceptCallbackResponse cachingCallback(InterceptContext ctx) {
    cachingCount.incrementAndGet();

    // Generate cache key
    String methodName = ctx.getLocalMetadata() != null ? ctx.getLocalMetadata().methodName() : "?";
    Object[] args = ctx.getArgs();
    String cacheKey = methodName + ":" + Arrays.toString(args);

    // Check cache
    if (cache.containsKey(cacheKey)) {
      Object cachedValue = cache.get(cacheKey);
      lastCallWasCacheHit = true;
      logger.info(
          "AROUND_CHAIN: cachingCallback CACHE_HIT, key={}, value={}", cacheKey, cachedValue);

      // Return cached value without proceeding
      if (!ctx.isVoid()) {
        ctx.setReturnValue(cachedValue);
      }
      return InterceptCallbackResponse.skipProceed();
    }

    // Cache miss - proceed and cache result
    lastCallWasCacheHit = false;
    logger.info("AROUND_CHAIN: cachingCallback CACHE_MISS, key={}", cacheKey);

    ctx.proceed();

    // Cache the result
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      cache.put(cacheKey, returnValue);
      logger.info("AROUND_CHAIN: cachingCallback CACHED, key={}, value={}", cacheKey, returnValue);
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Exception Handling Callbacks ====================

  /**
   * Local AROUND callback that throws an exception BEFORE proceeding.
   *
   * <p>This callback throws a RuntimeException without calling proceed(), testing that:
   *
   * <ul>
   *   <li>The inner chain (including method execution) is NOT invoked
   *   <li>The exception propagates outward through the chain
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response (never reached)
   */
  public static InterceptCallbackResponse exceptionThrowerBefore(InterceptContext ctx) {
    int seq = exceptionThrowerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: exceptionThrowerBefore THROWING, seq={}", seq);

    // Throw exception using the context API
    ctx.setExceptionToThrow(
        new RuntimeException("Exception from exceptionThrowerBefore (seq=" + seq + ")"));
    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback that throws an exception AFTER proceeding.
   *
   * <p>This callback calls proceed() to execute the method, then throws an exception, testing that:
   *
   * <ul>
   *   <li>The method executes successfully
   *   <li>The exception from the callback overrides the method's return value
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse exceptionThrowerAfter(InterceptContext ctx) {
    int seq = exceptionThrowerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: exceptionThrowerAfter BEFORE, seq={}", seq);

    // Proceed to execute the method
    ctx.proceed();

    Object returnValue = ctx.isVoid() ? "void" : ctx.getReturnValue();
    logger.info(
        "AROUND_CHAIN: exceptionThrowerAfter AFTER, seq={}, got return={}, NOW THROWING",
        seq,
        returnValue);

    // Throw exception after proceed
    ctx.setExceptionToThrow(
        new RuntimeException("Exception from exceptionThrowerAfter (seq=" + seq + ")"));
    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback that suppresses an exception from the inner chain.
   *
   * <p>This callback calls proceed() and checks if the method threw. If it did, it suppresses the
   * exception and returns a fallback value instead.
   *
   * <p>This tests exception suppression/recovery patterns.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse exceptionSuppressor(InterceptContext ctx) {
    int seq = exceptionSuppressorCount.incrementAndGet();
    logger.info("AROUND_CHAIN: exceptionSuppressor BEFORE, seq={}", seq);

    // Proceed to execute the method
    ctx.proceed();

    // Check if inner chain threw
    Throwable thrown = ctx.getThrownException();
    if (thrown != null) {
      logger.info(
          "AROUND_CHAIN: exceptionSuppressor SUPPRESSING exception, seq={}, exception={}",
          seq,
          thrown.getMessage());
      // Suppress exception by setting a fallback return value
      ctx.setReturnValue(-999);
    } else {
      logger.info(
          "AROUND_CHAIN: exceptionSuppressor AFTER, seq={}, no exception, return={}",
          seq,
          ctx.isVoid() ? "void" : ctx.getReturnValue());
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback that replaces one exception type with another.
   *
   * <p>This callback calls proceed() and if the method threw an IllegalArgumentException, it
   * replaces it with an IllegalStateException, wrapping the original.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse exceptionReplacer(InterceptContext ctx) {
    int seq = exceptionReplacerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: exceptionReplacer BEFORE, seq={}", seq);

    // Proceed to execute the method
    ctx.proceed();

    // Check if inner chain threw
    Throwable thrown = ctx.getThrownException();
    if (thrown != null) {
      if (thrown instanceof IllegalArgumentException) {
        logger.info(
            "AROUND_CHAIN: exceptionReplacer REPLACING IllegalArgumentException, seq={}", seq);
        ctx.setExceptionToThrow(
            new IllegalStateException("Replaced exception (seq=" + seq + ")", thrown));
      } else {
        logger.info(
            "AROUND_CHAIN: exceptionReplacer NOT REPLACING (not IllegalArgumentException), "
                + "seq={}, type={}",
            seq,
            thrown.getClass().getSimpleName());
      }
    } else {
      logger.info(
          "AROUND_CHAIN: exceptionReplacer AFTER, seq={}, no exception, return={}",
          seq,
          ctx.isVoid() ? "void" : ctx.getReturnValue());
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback that logs exceptions propagating through but doesn't modify them.
   *
   * <p>This is useful as an outer layer to verify that exceptions propagate correctly through the
   * chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse exceptionLogger(InterceptContext ctx) {
    int seq = middleLoggerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: exceptionLogger BEFORE, seq={}", seq);

    ctx.proceed();

    Throwable thrown = ctx.getThrownException();
    if (thrown != null) {
      logger.info(
          "AROUND_CHAIN: exceptionLogger AFTER, seq={}, exception propagating: {}",
          seq,
          thrown.getMessage());
    } else {
      logger.info(
          "AROUND_CHAIN: exceptionLogger AFTER, seq={}, return={}",
          seq,
          ctx.isVoid() ? "void" : ctx.getReturnValue());
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Chain Order Verification Callbacks ====================

  /**
   * Local AROUND callback "A" for chain order verification.
   *
   * <p>Logs "LOCAL_AROUND_A_BEFORE" and "LOCAL_AROUND_A_AFTER" to verify execution order.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAroundA(InterceptContext ctx) {
    logger.info("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_BEFORE");

    ctx.proceed();

    logger.info("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_AFTER");
    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback "C" for chain order verification.
   *
   * <p>Logs "LOCAL_AROUND_C_BEFORE" and "LOCAL_AROUND_C_AFTER" to verify execution order.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAroundC(InterceptContext ctx) {
    logger.info("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_BEFORE");

    ctx.proceed();

    logger.info("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_AFTER");
    return new InterceptCallbackResponse();
  }

  // ==================== Arg Mutation Callbacks ====================

  /**
   * Local AROUND callback that mutates the first argument to 10.
   *
   * <p>Used for testing argument mutation propagation through the chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse mutateFirstArgTo10(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    Object originalArg = args.length > 0 ? args[0] : null;
    logger.info("AROUND_CHAIN: mutateFirstArgTo10 BEFORE, original arg[0]={}", originalArg);

    // Mutate first arg to 10
    ctx.setArg(0, 10);

    ctx.proceed();

    logger.info(
        "AROUND_CHAIN: mutateFirstArgTo10 AFTER, return={}",
        ctx.isVoid() ? "void" : ctx.getReturnValue());
    return new InterceptCallbackResponse();
  }

  // ==================== Return Value Modification Callbacks ====================

  /**
   * Local AROUND callback that adds 1 to the return value.
   *
   * <p>Used for testing return value modification propagation through the chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse addOneToReturn(InterceptContext ctx) {
    logger.info("AROUND_CHAIN: addOneToReturn BEFORE");

    ctx.proceed();

    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int modified = original + 1;
        ctx.setReturnValue(modified);
        logger.info("AROUND_CHAIN: addOneToReturn AFTER, {} + 1 = {}", original, modified);
      }
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Skip Callbacks ====================

  /**
   * Local AROUND callback that skips execution with a cached value.
   *
   * <p>Always skips proceed() and returns a hardcoded value (999). Used for testing that skipping
   * in the middle of the chain prevents inner layers from executing.
   *
   * @param ctx the intercept context
   * @return the intercept response with skip
   */
  public static InterceptCallbackResponse alwaysSkipWithCachedValue(InterceptContext ctx) {
    logger.info("AROUND_CHAIN: alwaysSkipWithCachedValue SKIPPING");

    // Skip execution and return cached value
    ctx.setReturnValue(999);
    return InterceptCallbackResponse.skipProceed();
  }
}
