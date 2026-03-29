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
package io.quasient.pal.core.runtime.objects;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the background clearing of entries in {@link ConcurrentHashMapObjectLookupStore}'
 * refQueue.
 */
class ObjectLookupStoreBackgroundProcessor implements ObjectLookupStoreCleaner {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(ObjectLookupStoreBackgroundProcessor.class);

  /** Default timeout in millis for blocking cleanup. */
  private static final int DEFAULT_CLEANUP_INTERVAL_MS = 5;

  /** The lookup store that holds object references for lookup operations. */
  @Nonnull final ConcurrentHashMapObjectLookupStore objectLookupStore;

  /** Statistics tracker for monitoring the performance and usage of the lookup store. */
  @Nonnull private final ObjectLookupStoreStats objectLookupStoreStats;

  /** Timeout in millis for blocking cleanup. */
  private final int cleanupTimeoutMs;

  /** Flag to stop */
  private volatile boolean running = false;

  /** The cleanup worker thread */
  private Thread worker;

  /**
   * Constructs a background processor with specified stats and cleanup timeout parameter.
   *
   * @param objectLookupStore the store containing object references to process; must not be null
   * @param objectLookupStoreStats the statistics tracker for the lookup store; must not be null
   * @param cleanupTimeoutMs timeout in millis for blocking cleanup.
   * @throws NullPointerException if {@code objectLookupStore} or {@code objectLookupStoreStats} is
   *     null
   */
  ObjectLookupStoreBackgroundProcessor(
      @Nonnull ConcurrentHashMapObjectLookupStore objectLookupStore,
      @Nonnull ObjectLookupStoreStats objectLookupStoreStats,
      int cleanupTimeoutMs) {
    this.objectLookupStore = Objects.requireNonNull(objectLookupStore);
    this.objectLookupStoreStats = Objects.requireNonNull(objectLookupStoreStats);
    this.cleanupTimeoutMs = cleanupTimeoutMs;
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Initialized object store processor with cleanupIntervalMillis: {}", cleanupTimeoutMs);
    }
  }

  /**
   * Constructs a background processor with default interval for cleanup timeout.
   *
   * @param objectLookupStore the store containing object references to process; must not be null
   * @param objectLookupStoreStats the statistics tracker for the lookup store; must not be null
   * @throws NullPointerException if {@code objectLookupStore} or {@code objectLookupStoreStats} is
   *     null
   */
  ObjectLookupStoreBackgroundProcessor(
      ConcurrentHashMapObjectLookupStore objectLookupStore,
      ObjectLookupStoreStats objectLookupStoreStats) {
    this(objectLookupStore, objectLookupStoreStats, DEFAULT_CLEANUP_INTERVAL_MS);
  }

  /**
   * Initiates the thread which will continuously remove cleared entries from the lookup store's ref
   * queue and update relevant statistics.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void start() {
    if (running) {
      return;
    }
    running = true;

    worker =
        new Thread(
            () -> {
              logger.info("Starting OBJECTS background cleanup loop");

              while (running && !Thread.currentThread().isInterrupted()) {
                int cleared = 0;
                try {
                  // Block briefly waiting for at least one reference; null on timeout.
                  IdentifiableObject first =
                      (IdentifiableObject) objectLookupStore.getRefQueue().remove(cleanupTimeoutMs);
                  if (first != null) {
                    objectLookupStore.removeEntry(first);
                    cleared++;

                    // Drain the rest without blocking.
                    for (IdentifiableObject ref;
                        (ref = (IdentifiableObject) objectLookupStore.getRefQueue().poll())
                            != null; ) {
                      objectLookupStore.removeEntry(ref);
                      cleared++;
                    }
                  }

                  if (cleared > 0) {
                    objectLookupStoreStats.getTotalObjectsCleared().addAndGet(cleared);
                  }

                } catch (InterruptedException ie) {
                  // Respect shutdown.
                  Thread.currentThread().interrupt();
                  break;
                } catch (Exception e) {
                  logger.error("Error draining reference queue", e);
                }
              }

              logger.info("OBJECTS background cleanup loop stopped");
            },
            "ObjectLookupStoreBackgroundThread");

    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Calls the {@link ConcurrentHashMapObjectLookupStore#drainRefQueue()} once and returns the
   * number of cleared entries. This method is meant to be called by non-started processors
   * (normally for testing purposes).
   *
   * @return the number of cleared entries
   * @throws IllegalStateException if this is called from a started processor.
   */
  @Override
  public int runOnce() {

    if (running) {
      throw new IllegalStateException(
          "Background cleanup worker is running. Do not call runOnce() after start()");
    }

    return objectLookupStore.drainRefQueue();
  }

  /**
   * Terminates the background processing by shutting down the scheduler and stopping all scheduled
   * tasks.
   */
  @Override
  public void stop() {
    running = false;
    if (worker != null) {
      worker.interrupt();
      try {
        worker.join(TimeUnit.SECONDS.toMillis(2));
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      worker = null;
    }
  }
}
