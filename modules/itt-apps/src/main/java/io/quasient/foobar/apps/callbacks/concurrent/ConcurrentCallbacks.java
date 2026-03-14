/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.callbacks.concurrent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.ProceedResult;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent callback handlers for integration tests.
 *
 * <p>This class demonstrates various concurrentty patterns for intercept callbacks:
 *
 * <ul>
 *   <li><b>Stateless callbacks:</b> No shared state (preferred)
 *   <li><b>Concurrent state:</b> Using concurrent collections (ConcurrentHashMap)
 *   <li><b>Manual locking:</b> Using ReadWriteLock for non-concurrent collections
 * </ul>
 *
 * <p>All callback methods are static and designed to handle concurrent invocations correctly.
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
@SuppressFBWarnings(
    value = {"EI_EXPOSE_STATIC_REP2", "MS_EXPOSE_REP"},
    justification = "Test helper class; mutable static state is intentional for test coordination")
public final class ConcurrentCallbacks {

  /** Logger for callback invocations. */
  private static final Logger logger = LoggerFactory.getLogger(ConcurrentCallbacks.class);

  // ==================== Concurrent BEFORE Callback State ====================

  /** Counter for BEFORE callback invocations (concurrent). */
  private static final AtomicInteger beforeCount = new AtomicInteger(0);

  /** Tracks arg mutations by thread (concurrent). */
  private static final ConcurrentHashMap<String, Integer> argMutations = new ConcurrentHashMap<>();

  // ==================== Concurrent AFTER Callback State ====================

  /** Counter for AFTER callback invocations (concurrent). */
  private static final AtomicInteger afterCount = new AtomicInteger(0);

  /** Tracks return value overrides by thread (concurrent). */
  private static final ConcurrentHashMap<String, Integer> returnOverrides =
      new ConcurrentHashMap<>();

  // ==================== Concurrent AROUND Callback State (Cache) ====================

  /** Counter for AROUND callback invocations (concurrent). */
  private static final AtomicInteger aroundCount = new AtomicInteger(0);

  /** Cache for AROUND callbacks (concurrent). */
  private static final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

  /** Cache hit counter (concurrent). */
  private static final AtomicInteger cacheHits = new AtomicInteger(0);

  /** Cache miss counter (concurrent). */
  private static final AtomicInteger cacheMisses = new AtomicInteger(0);

  // ==================== Manual Locking State ====================

  /** Counter for callbacks using manual locking. */
  private static final AtomicInteger manualLockCount = new AtomicInteger(0);

  /** ReadWriteLock for protecting non-concurrent collections. */
  private static final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * Non-concurrent map protected by ReadWriteLock. This demonstrates manual locking pattern for
   * callbacks that need to use non-concurrent data structures.
   */
  private static final HashMap<String, Integer> manualLockState = new HashMap<>();

  private ConcurrentCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    beforeCount.set(0);
    argMutations.clear();
    afterCount.set(0);
    returnOverrides.clear();
    aroundCount.set(0);
    cache.clear();
    cacheHits.set(0);
    cacheMisses.set(0);
    manualLockCount.set(0);
    lock.writeLock().lock();
    try {
      manualLockState.clear();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // ==================== Getters ====================

  /**
   * Returns the number of BEFORE callback invocations.
   *
   * @return the call count
   */
  public static int getBeforeCount() {
    return beforeCount.get();
  }

  /**
   * Returns the number of AFTER callback invocations.
   *
   * @return the call count
   */
  public static int getAfterCount() {
    return afterCount.get();
  }

  /**
   * Returns the number of AROUND callback invocations.
   *
   * @return the call count
   */
  public static int getAroundCount() {
    return aroundCount.get();
  }

  /**
   * Returns the number of cache hits.
   *
   * @return the cache hit count
   */
  public static int getCacheHits() {
    return cacheHits.get();
  }

  /**
   * Returns the number of cache misses.
   *
   * @return the cache miss count
   */
  public static int getCacheMisses() {
    return cacheMisses.get();
  }

  /**
   * Returns the current cache size.
   *
   * @return the number of entries in the cache
   */
  public static int getCacheSize() {
    return cache.size();
  }

  /**
   * Returns the number of arg mutations recorded.
   *
   * @return the number of unique threads that mutated args
   */
  public static int getArgMutationCount() {
    return argMutations.size();
  }

  /**
   * Returns the number of return overrides recorded.
   *
   * @return the number of unique threads that overrode returns
   */
  public static int getReturnOverrideCount() {
    return returnOverrides.size();
  }

  /**
   * Returns the manual lock callback count.
   *
   * @return the count
   */
  public static int getManualLockCount() {
    return manualLockCount.get();
  }

  /**
   * Returns the size of the manual lock state map.
   *
   * @return the map size (protected by read lock)
   */
  public static int getManualLockStateSize() {
    lock.readLock().lock();
    try {
      return manualLockState.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  // ==================== Concurrent BEFORE Callback ====================

  /**
   * BEFORE callback that mutates the first argument by adding 100.
   *
   * <p>This callback is concurrent because:
   *
   * <ul>
   *   <li>It uses AtomicInteger for the counter
   *   <li>It uses ConcurrentHashMap to track mutations per thread
   *   <li>Each thread has its own InterceptContext instance
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeConcurrentMutation(InterceptContext ctx) {
    int count = beforeCount.incrementAndGet();
    String threadName = Thread.currentThread().getName();

    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      int original = (Integer) args[0];
      int mutated = original + 100;
      ctx.setArg(0, mutated);
      argMutations.put(threadName, mutated);
      logger.debug(
          "THREAD_SAFE_BEFORE [{}]: mutated arg {} -> {} (count={})",
          threadName,
          original,
          mutated,
          count);
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Concurrent AFTER Callback ====================

  /**
   * AFTER callback that overrides the return value by adding 1000.
   *
   * <p>This callback is concurrent because:
   *
   * <ul>
   *   <li>It uses AtomicInteger for the counter
   *   <li>It uses ConcurrentHashMap to track overrides per thread
   *   <li>Each thread has its own InterceptContext instance
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterConcurrentOverride(InterceptContext ctx) {
    int count = afterCount.incrementAndGet();
    String threadName = Thread.currentThread().getName();

    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int overridden = original + 1000;
        ctx.setReturnValue(overridden);
        returnOverrides.put(threadName, overridden);
        logger.debug(
            "THREAD_SAFE_AFTER [{}]: overrode return {} -> {} (count={})",
            threadName,
            original,
            overridden,
            count);
      }
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Concurrent AROUND Callback with Caching ====================

  /**
   * AROUND callback that implements a simple cache.
   *
   * <p>This callback is concurrent because:
   *
   * <ul>
   *   <li>It uses ConcurrentHashMap for the cache
   *   <li>It uses AtomicInteger for cache hit/miss counters
   *   <li>Cache lookups and updates are atomic
   * </ul>
   *
   * <p>Pattern:
   *
   * <pre>
   * 1. Generate cache key from arguments
   * 2. Check cache (if hit, return cached value and skip proceed)
   * 3. If miss, proceed and cache the result
   * </pre>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundCachedExecution(InterceptContext ctx) {
    int count = aroundCount.incrementAndGet();
    String threadName = Thread.currentThread().getName();

    // Generate cache key from first argument
    Object[] args = ctx.getArgs();
    String cacheKey = args.length > 0 ? String.valueOf(args[0]) : "no-args";

    // Check cache
    Object cached = cache.get(cacheKey);
    if (cached != null) {
      // Cache hit - skip execution
      int hits = cacheHits.incrementAndGet();
      ctx.setReturnValue(cached);
      logger.debug(
          "THREAD_SAFE_AROUND [{}]: cache HIT for key={} (count={}, hits={})",
          threadName,
          cacheKey,
          count,
          hits);
      return InterceptCallbackResponse.skipProceed();
    }

    // Cache miss - proceed with execution
    int misses = cacheMisses.incrementAndGet();
    logger.debug(
        "THREAD_SAFE_AROUND [{}]: cache MISS for key={} (count={}, misses={})",
        threadName,
        cacheKey,
        count,
        misses);

    ProceedResult result = ctx.proceed();

    // Cache the result (if no exception)
    if (!result.hasException() && !ctx.isVoid()) {
      Object returnValue = result.getReturnValue();
      cache.put(cacheKey, returnValue);
      logger.debug(
          "THREAD_SAFE_AROUND [{}]: cached result for key={} -> {}",
          threadName,
          cacheKey,
          returnValue);
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Manual Locking Callback ====================

  /**
   * AROUND callback that uses manual ReadWriteLock to protect non-concurrent state.
   *
   * <p>This callback demonstrates the manual locking pattern. It uses a ReadWriteLock to protect a
   * non-concurrent HashMap. This is a cautionary example - prefer using concurrent collections when
   * possible.
   *
   * <p><b>Concurrentty considerations:</b>
   *
   * <ul>
   *   <li>Use read lock for cache lookups (allows concurrent reads)
   *   <li>Use write lock for cache updates (exclusive access)
   *   <li>Minimize critical section duration
   *   <li>Be careful with lock ordering to avoid deadlocks
   * </ul>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundManualLocking(InterceptContext ctx) {
    int count = manualLockCount.incrementAndGet();
    String threadName = Thread.currentThread().getName();

    // Generate cache key from first argument
    Object[] args = ctx.getArgs();
    String cacheKey = args.length > 0 ? String.valueOf(args[0]) : "no-args";

    // Check cache with read lock
    lock.readLock().lock();
    Integer cached;
    try {
      cached = manualLockState.get(cacheKey);
    } finally {
      lock.readLock().unlock();
    }

    if (cached != null) {
      // Cache hit - skip execution
      ctx.setReturnValue(cached);
      logger.debug(
          "MANUAL_LOCK_AROUND [{}]: cache HIT for key={} (count={})", threadName, cacheKey, count);
      return InterceptCallbackResponse.skipProceed();
    }

    // Cache miss - proceed with execution
    logger.debug(
        "MANUAL_LOCK_AROUND [{}]: cache MISS for key={} (count={})", threadName, cacheKey, count);

    ProceedResult result = ctx.proceed();

    // Cache the result with write lock (if no exception)
    if (!result.hasException() && !ctx.isVoid()) {
      Object returnValue = result.getReturnValue();
      if (returnValue instanceof Integer) {
        lock.writeLock().lock();
        try {
          manualLockState.put(cacheKey, (Integer) returnValue);
          logger.debug(
              "MANUAL_LOCK_AROUND [{}]: cached result for key={} -> {}",
              threadName,
              cacheKey,
              returnValue);
        } finally {
          lock.writeLock().unlock();
        }
      }
    }

    return new InterceptCallbackResponse();
  }
}
