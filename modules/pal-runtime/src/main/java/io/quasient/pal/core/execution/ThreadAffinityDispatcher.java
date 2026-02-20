/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Routes invocations to registered executors based on thread affinity key.
 *
 * <p>When no affinity is specified ({@code null} or empty), uses direct invocation. When an
 * affinity is specified but no matching executor is registered, logs a warning and falls back to
 * direct invocation.
 *
 * <p>This class is thread-safe. All methods can be called concurrently from multiple threads.
 */
public class ThreadAffinityDispatcher {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(ThreadAffinityDispatcher.class);

  /** Map of affinity keys to their registered executors. */
  private final Map<String, InvocationExecutor> executors = new ConcurrentHashMap<>();

  /** Fallback executor for direct invocation on the current thread. */
  private final InvocationExecutor directExecutor = new DirectInvocationExecutor();

  /** Creates a new thread affinity dispatcher with no registered executors. */
  public ThreadAffinityDispatcher() {}

  /**
   * Registers an executor for the given affinity key.
   *
   * @param affinityKey the affinity key to register (e.g., "fx-thread")
   * @param executor the executor to use for this affinity
   * @throws NullPointerException if affinityKey or executor is null
   */
  public void register(String affinityKey, InvocationExecutor executor) {
    Objects.requireNonNull(affinityKey, "affinityKey must not be null");
    Objects.requireNonNull(executor, "executor must not be null");
    executors.put(affinityKey, executor);
    logger.info("Registered thread affinity executor: {}", affinityKey);
  }

  /**
   * Executes the given invocation using the executor registered for the specified thread affinity.
   *
   * <p>If the thread affinity is {@code null} or empty, the invocation is executed directly on the
   * current thread. If no executor is registered for the given affinity, a warning is logged and
   * direct execution is used as a fallback.
   *
   * @param threadAffinity the thread affinity key, or {@code null} for direct execution
   * @param invocation the callable to execute
   * @return the result of the invocation
   * @throws Exception if the invocation throws an exception
   */
  public Object execute(String threadAffinity, Callable<Object> invocation) throws Exception {
    if (threadAffinity == null || threadAffinity.isEmpty()) {
      return directExecutor.execute(invocation);
    }
    InvocationExecutor executor = executors.get(threadAffinity);
    if (executor == null) {
      logger.warn(
          "Unknown thread affinity '{}' requested. No matching executor registered. "
              + "Executing on invoker thread.",
          threadAffinity);
      return directExecutor.execute(invocation);
    }
    return executor.execute(invocation);
  }

  /**
   * Returns whether an executor is registered for the given affinity key.
   *
   * @param affinityKey the affinity key to check
   * @return {@code true} if an executor is registered, {@code false} otherwise
   */
  public boolean hasExecutor(String affinityKey) {
    return executors.containsKey(affinityKey);
  }
}
