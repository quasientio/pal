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
package io.quasient.pal.core.intercept;

import io.quasient.pal.core.dispatcher.InterceptAsyncThreadFactory;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating the optimal {@link ExecutorService} for async intercept callback dispatch.
 *
 * <p>This factory selects an executor implementation based on the Java runtime version and an
 * optional system property override. The executor is used by {@link
 * LocalInterceptCallbackDispatcher} to run BEFORE_ASYNC and AFTER_ASYNC callbacks in the
 * background.
 *
 * <p><b>Executor Selection Strategy:</b>
 *
 * <ol>
 *   <li>If the system property {@value #EXECUTOR_TYPE_PROPERTY} is set, the specified executor type
 *       is used (regardless of Java version)
 *   <li>If running on Java 21+, virtual threads are used ({@code
 *       Executors.newVirtualThreadPerTaskExecutor()})
 *   <li>Otherwise, a cached thread pool is used ({@code Executors.newCachedThreadPool()})
 * </ol>
 *
 * <p><b>Executor Types:</b>
 *
 * <ul>
 *   <li>{@code VIRTUAL} - Virtual thread executor (requires Java 21+). Each callback runs on a new
 *       virtual thread. Ideal for the callback workload: short-lived, potentially blocking on I/O,
 *       eliminates thread pool sizing concerns and reduces memory per-callback.
 *   <li>{@code CACHED} - Cached thread pool ({@link Executors#newCachedThreadPool()}). Creates
 *       threads on demand and reuses idle threads. Good default for Java 17.
 *   <li>{@code FIXED} - Fixed thread pool ({@link Executors#newFixedThreadPool(int)}). Uses a
 *       configurable number of threads (default: {@value #DEFAULT_FIXED_POOL_SIZE}). Useful for
 *       limiting concurrency.
 * </ul>
 *
 * <p><b>System Properties:</b>
 *
 * <ul>
 *   <li>{@code pal.intercept.async.executor} - Executor type: {@code VIRTUAL}, {@code CACHED}, or
 *       {@code FIXED}
 *   <li>{@code pal.intercept.async.executor.fixed.size} - Thread count for FIXED executor (default:
 *       {@value #DEFAULT_FIXED_POOL_SIZE})
 * </ul>
 *
 * <p><b>Virtual Thread Opportunity (Java 21+):</b> Virtual threads are ideal for the async callback
 * workload because callbacks are typically short-lived and may block on I/O (e.g., logging,
 * metrics). Virtual threads eliminate thread pool sizing concerns and reduce memory overhead from
 * ~1 MB per platform thread to ~1 KB per virtual thread. When the project upgrades to Java 21+, the
 * factory will automatically use virtual threads unless overridden.
 *
 * @see LocalInterceptCallbackDispatcher
 * @see InterceptAsyncThreadFactory
 */
public final class VirtualThreadCallbackExecutor {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(VirtualThreadCallbackExecutor.class);

  /** System property to select executor type. */
  public static final String EXECUTOR_TYPE_PROPERTY = "pal.intercept.async.executor";

  /** System property for fixed pool size. */
  public static final String FIXED_POOL_SIZE_PROPERTY = "pal.intercept.async.executor.fixed.size";

  /** Default size for fixed thread pool. */
  static final int DEFAULT_FIXED_POOL_SIZE = 16;

  /** Minimum Java version that supports virtual threads as a stable feature. */
  static final int VIRTUAL_THREADS_MIN_VERSION = 21;

  /** Private constructor to prevent instantiation. */
  private VirtualThreadCallbackExecutor() {}

  /**
   * Executor implementation types for async intercept callbacks.
   *
   * @see VirtualThreadCallbackExecutor
   */
  public enum ExecutorType {
    /** Virtual thread executor (Java 21+ required). */
    VIRTUAL,
    /** Cached thread pool (auto-scaling, Java 17+ compatible). */
    CACHED,
    /** Fixed thread pool (configurable size, Java 17+ compatible). */
    FIXED
  }

  /**
   * Creates an {@link ExecutorService} for async intercept callback dispatch.
   *
   * <p>The executor type is determined by the system property {@value #EXECUTOR_TYPE_PROPERTY}, or
   * auto-detected based on the Java runtime version.
   *
   * @param threadFactory the thread factory for CACHED and FIXED executors (may be null for
   *     VIRTUAL)
   * @return an executor service configured for async callback dispatch
   */
  public static ExecutorService create(ThreadFactory threadFactory) {
    ExecutorType type = resolveExecutorType();
    return create(type, threadFactory);
  }

  /**
   * Creates an {@link ExecutorService} of the specified type.
   *
   * @param type the executor type to create
   * @param threadFactory the thread factory for CACHED and FIXED executors (may be null for
   *     VIRTUAL)
   * @return an executor service of the specified type
   * @throws IllegalStateException if VIRTUAL is requested but Java version is below 21
   */
  public static ExecutorService create(ExecutorType type, ThreadFactory threadFactory) {
    return switch (type) {
      case VIRTUAL -> createVirtualThreadExecutor();
      case CACHED -> {
        logger.info("Using cached thread pool for async intercept callbacks");
        yield threadFactory != null
            ? Executors.newCachedThreadPool(threadFactory)
            : Executors.newCachedThreadPool();
      }
      case FIXED -> {
        int poolSize = resolveFixedPoolSize();
        logger.info("Using fixed thread pool (size={}) for async intercept callbacks", poolSize);
        yield threadFactory != null
            ? Executors.newFixedThreadPool(poolSize, threadFactory)
            : Executors.newFixedThreadPool(poolSize);
      }
    };
  }

  /**
   * Resolves the executor type from system property or Java version auto-detection.
   *
   * @return the resolved executor type
   */
  static ExecutorType resolveExecutorType() {
    String configuredType = System.getProperty(EXECUTOR_TYPE_PROPERTY);
    if (configuredType != null && !configuredType.isBlank()) {
      try {
        ExecutorType type = ExecutorType.valueOf(configuredType.trim().toUpperCase(Locale.ENGLISH));
        logger.info("Async intercept executor configured via system property: {}", type);
        return type;
      } catch (IllegalArgumentException e) {
        logger.warn(
            "Invalid executor type '{}' from system property {}, falling back to auto-detection",
            configuredType,
            EXECUTOR_TYPE_PROPERTY);
      }
    }

    // Auto-detect based on Java version
    if (isVirtualThreadsAvailable()) {
      logger.info(
          "Java {} detected, using virtual thread executor for async intercept callbacks",
          Runtime.version().feature());
      return ExecutorType.VIRTUAL;
    }

    logger.info(
        "Java {} detected (< {}), using cached thread pool for async intercept callbacks",
        Runtime.version().feature(),
        VIRTUAL_THREADS_MIN_VERSION);
    return ExecutorType.CACHED;
  }

  /**
   * Checks if virtual threads are available in the current Java runtime.
   *
   * <p>Virtual threads are a stable feature starting from Java 21 (JEP 444). This method checks the
   * Java runtime version to determine availability.
   *
   * @return true if virtual threads are available (Java 21+)
   */
  public static boolean isVirtualThreadsAvailable() {
    return Runtime.version().feature() >= VIRTUAL_THREADS_MIN_VERSION;
  }

  /**
   * Creates a virtual thread executor via reflection.
   *
   * <p>Uses reflection to call {@code Executors.newVirtualThreadPerTaskExecutor()} so this class
   * compiles on Java 17 but uses virtual threads on Java 21+.
   *
   * @return a virtual thread per-task executor
   * @throws IllegalStateException if virtual threads are not available or reflection fails
   */
  @SuppressWarnings("CloseableProvides")
  private static ExecutorService createVirtualThreadExecutor() {
    if (!isVirtualThreadsAvailable()) {
      throw new IllegalStateException(
          "Virtual threads require Java "
              + VIRTUAL_THREADS_MIN_VERSION
              + "+, but running on Java "
              + Runtime.version().feature());
    }

    try {
      Method method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
      ExecutorService executor = (ExecutorService) method.invoke(null);
      logger.info("Using virtual thread executor for async intercept callbacks");
      return executor;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "Failed to create virtual thread executor via reflection. "
              + "Java version reports "
              + Runtime.version().feature()
              + " but Executors.newVirtualThreadPerTaskExecutor() is not available.",
          e);
    }
  }

  /**
   * Resolves the fixed pool size from system property or default.
   *
   * @return the pool size to use for fixed thread pools
   */
  private static int resolveFixedPoolSize() {
    String sizeStr = System.getProperty(FIXED_POOL_SIZE_PROPERTY);
    if (sizeStr != null && !sizeStr.isBlank()) {
      try {
        int size = Integer.parseInt(sizeStr.trim());
        if (size > 0) {
          return size;
        }
        logger.warn("Invalid fixed pool size {} (must be > 0), using default", size);
      } catch (NumberFormatException e) {
        logger.warn("Invalid fixed pool size '{}', using default", sizeStr);
      }
    }
    return DEFAULT_FIXED_POOL_SIZE;
  }
}
