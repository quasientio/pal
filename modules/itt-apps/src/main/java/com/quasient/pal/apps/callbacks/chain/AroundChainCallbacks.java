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
}
