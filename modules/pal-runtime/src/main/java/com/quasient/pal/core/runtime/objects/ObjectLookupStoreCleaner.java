/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.objects;

/**
 * Strategy interface that removes {@link java.lang.ref.ReferenceQueue}-enqueued entries from an
 * {@link ObjectLookupStore}.
 *
 * <p>Implementations can operate in two modes:
 *
 * <ul>
 *   <li><b>Manual / Deterministic mode</b> – call {@link #runOnce()} directly whenever the test or
 *       caller wishes to drain the queue.
 *   <li><b>Scheduled mode</b> – call {@link #start()} once; the cleaner will drain the queue on a
 *       background thread at a fixed cadence until {@link #stop()} (or {@link #close()}) is
 *       invoked.
 * </ul>
 *
 * <p>All methods are idempotent and thread-safe: starting an already-started cleaner or stopping an
 * already-stopped cleaner is a no-op.
 *
 * <p>Typical production wiring:
 *
 * <pre>{@code
 * ObjectLookupStore store = ConcurrentHashMapObjectLookupStore.createWithScheduledCleaner();
 * }</pre>
 *
 * <p>Typical unit-test wiring:
 *
 * <pre>{@code
 * ConcurrentHashMapObjectLookupStore store = ConcurrentHashMapObjectLookupStore.createUnmanaged();
 * ObjectLookupStoreCleaner cleaner =
 *     new ObjectLookupStoreBackgroundProcessor(store, store.getStats(), 0, 60);
 * store.attachCleaner(cleaner);
 *
 * // … run test …
 * cleaner.runOnce();   // deterministic drain
 * }</pre>
 */
public interface ObjectLookupStoreCleaner extends AutoCloseable {

  /**
   * Drains the reference queue <em>exactly once</em>, removing every entry that is currently
   * queued. Non-blocking; typically completes in micro- to low-millisecond time.
   */
  void runOnce();

  /**
   * Starts periodic clean-up on a daemon thread. If the cleaner is already running, the call has no
   * effect.
   */
  void start();

  /**
   * Stops the periodic clean-up thread, if one is running, and waits for it to terminate. Safe to
   * call multiple times.
   */
  void stop();

  /** Delegates to {@link #stop()}; satisfies {@link AutoCloseable}. */
  @Override
  default void close() {
    stop();
  }
}
