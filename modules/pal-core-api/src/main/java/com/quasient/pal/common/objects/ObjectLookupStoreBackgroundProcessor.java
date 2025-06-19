/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.common.objects;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages background processing tasks for the {@link ConcurrentHashMapObjectLookupStore}.
 *
 * <p>This processor schedules periodic tasks to remove cleared entries from the lookup store and to
 * log statistics about the store's usage and performance.
 */
class ObjectLookupStoreBackgroundProcessor {

  /** Logger instance for recording processor activities and errors. */
  private final Logger logger = LoggerFactory.getLogger(ObjectLookupStoreBackgroundProcessor.class);

  /** Default interval in seconds between cleanup operations. */
  private static final int DEFAULT_CLEANUP_INTERVAL_SECS = 2;

  /** Default interval in seconds between statistics logging operations. */
  private static final int DEFAULT_STATS_INTERVAL_SECS = 30;

  /** Executor service responsible for scheduling and executing background tasks. */
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  /** The lookup store that holds object references for lookup operations. */
  @Nonnull final ConcurrentHashMapObjectLookupStore objectLookupStore;

  /** Statistics tracker for monitoring the performance and usage of the lookup store. */
  @Nonnull private final ObjectLookupStoreStats objectLookupStoreStats;

  /** Interval in seconds between consecutive cleanup operations. */
  private final int cleanupIntervalSecs;

  /** Interval in seconds between consecutive statistics logging operations. */
  private final int statsIntervalSecs;

  /**
   * Constructs a background processor with specified intervals for cleanup and statistics logging.
   *
   * @param objectLookupStore the store containing object references to process; must not be null
   * @param objectLookupStoreStats the statistics tracker for the lookup store; must not be null
   * @param cleanupIntervalSecs interval in seconds for cleanup tasks; must be positive
   * @param statsIntervalSecs interval in seconds for statistics logging; must be positive
   * @throws NullPointerException if {@code objectLookupStore} or {@code objectLookupStoreStats} is
   *     null
   */
  ObjectLookupStoreBackgroundProcessor(
      @Nonnull ConcurrentHashMapObjectLookupStore objectLookupStore,
      @Nonnull ObjectLookupStoreStats objectLookupStoreStats,
      int cleanupIntervalSecs,
      int statsIntervalSecs) {
    this.objectLookupStore = Objects.requireNonNull(objectLookupStore);
    this.objectLookupStoreStats = Objects.requireNonNull(objectLookupStoreStats);
    this.cleanupIntervalSecs = cleanupIntervalSecs;
    this.statsIntervalSecs = statsIntervalSecs;
  }

  /**
   * Constructs a background processor with default intervals for cleanup and statistics logging.
   *
   * @param objectLookupStore the store containing object references to process; must not be null
   * @param objectLookupStoreStats the statistics tracker for the lookup store; must not be null
   * @throws NullPointerException if {@code objectLookupStore} or {@code objectLookupStoreStats} is
   *     null
   */
  ObjectLookupStoreBackgroundProcessor(
      ConcurrentHashMapObjectLookupStore objectLookupStore,
      ObjectLookupStoreStats objectLookupStoreStats) {
    this(
        objectLookupStore,
        objectLookupStoreStats,
        DEFAULT_CLEANUP_INTERVAL_SECS,
        DEFAULT_STATS_INTERVAL_SECS);
  }

  /**
   * Initiates the background processing by scheduling cleanup and statistics logging tasks.
   *
   * <p>Once started, the processor will periodically remove cleared entries from the lookup store
   * and log relevant statistics at the configured intervals.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  public void start() {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting OBJECTS stats");
    }

    scheduler.scheduleAtFixedRate(
        this::removeClearedEntries, 0, cleanupIntervalSecs, TimeUnit.SECONDS);
    scheduler.scheduleAtFixedRate(this::printStats, 0, statsIntervalSecs, TimeUnit.SECONDS);
  }

  /**
   * Logs current statistics about the lookup store.
   *
   * <p>This method retrieves and logs metrics such as the maximum size, current size, number of
   * successful lookups, and total objects cleared.
   */
  private void printStats() {
    try {
      if (logger.isDebugEnabled()) {
        logger.debug("OBJECTS: max size={}", objectLookupStoreStats.getMaxSize());
        logger.debug("OBJECTS: current size={}", objectLookupStore.size());
        logger.debug(
            "OBJECTS: successful lookups={}",
            objectLookupStoreStats.getSuccessfulStoreLookups().get());
        logger.debug("OBJECTS: total cleared={}", objectLookupStoreStats.getTotalObjectsCleared());
      }
    } catch (Exception e) {
      logger.error("Error printing stats", e);
    }
  }

  /**
   * Removes entries from the lookup store that have been cleared or are no longer valid.
   *
   * <p>This method scans the lookup store for entries whose associated objects have been cleared
   * and removes them. It updates the statistics with the number of entries cleared and logs the
   * cleanup duration.
   */
  void removeClearedEntries() {
    try {
      long cleanupStart = Instant.now().toEpochMilli();
      final AtomicInteger clearedCount = new AtomicInteger();
      objectLookupStore.getObjects().values().stream()
          .filter(identObject -> identObject.getObject().get() == null)
          .map(identObject -> ObjectRef.from(String.valueOf(identObject.getHash())))
          .forEach(
              objectRef -> {
                objectLookupStore.getObjects().remove(objectRef);
                clearedCount.getAndIncrement();
              });
      objectLookupStoreStats.getTotalObjectsCleared().addAndGet(clearedCount.get());
      if (logger.isDebugEnabled()) {
        long cleanupEnd = Instant.now().toEpochMilli();
        logger.debug(
            "Cleaned up {} object refs in {} ms", clearedCount.get(), cleanupEnd - cleanupStart);
      }
    } catch (Exception e) {
      logger.error("Error removing cleared entries", e);
    }
  }

  /**
   * Terminates the background processing by shutting down the scheduler and stopping all scheduled
   * tasks.
   */
  public void stop() {
    scheduler.shutdownNow();
  }
}
