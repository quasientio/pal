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

package net.ittera.pal.common.objects;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ObjectLookupStoreBackgroundProcessor extends Thread {

  private final Logger logger;
  private static final int DEFAULT_CLEANUP_INTERVAL_SECS = 2;
  private static final int DEFAULT_STATS_INTERVAL_SECS = 30;

  @Nonnull final ConcurrentHashMapObjectLookupStore objectLookupStore;
  @Nonnull private final ObjectLookupStoreStats objectLookupStoreStats;

  private final int cleanupIntervalSecs;
  private final int statsIntervalSecs;

  ObjectLookupStoreBackgroundProcessor(
      @Nonnull ConcurrentHashMapObjectLookupStore objectLookupStore,
      @Nonnull ObjectLookupStoreStats objectLookupStoreStats,
      int cleanupIntervalSecs,
      int statsIntervalSecs) {
    logger = LoggerFactory.getLogger(ObjectLookupStoreBackgroundProcessor.class);
    this.objectLookupStore = Objects.requireNonNull(objectLookupStore);
    this.objectLookupStoreStats = Objects.requireNonNull(objectLookupStoreStats);
    setName("Object Store GC");
    setPriority(Thread.MIN_PRIORITY);
    setDaemon(true);
    setUncaughtExceptionHandler(
        (t, e) -> logger.error("Uncaught error in ObjectLookupStoreBackgroundProcessor", e));
    this.cleanupIntervalSecs = cleanupIntervalSecs;
    this.statsIntervalSecs = statsIntervalSecs;
  }

  ObjectLookupStoreBackgroundProcessor(
      ConcurrentHashMapObjectLookupStore objectLookupStore,
      ObjectLookupStoreStats objectLookupStoreStats) {
    this(
        objectLookupStore,
        objectLookupStoreStats,
        DEFAULT_CLEANUP_INTERVAL_SECS,
        DEFAULT_STATS_INTERVAL_SECS);
  }

  private void printStats() {
    if (logger.isDebugEnabled()) {
      logger.debug("OBJECTS: max size={}", objectLookupStoreStats.getMaxSize());
      logger.debug("OBJECTS: current size={}", objectLookupStore.size());
      logger.debug(
          "OBJECTS: successful lookups={}",
          objectLookupStoreStats.getSuccessfulStoreLookups().get());
      logger.debug("OBJECTS: total cleared={}", objectLookupStoreStats.getTotalObjectsCleared());
    }
  }

  void removeClearedEntries() {
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
  }

  @Override
  public void run() {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting OBJECTS stats");
    }
    long statsIntervalStart = Instant.now().getEpochSecond();
    while (true) {
      try {
        Thread.sleep(cleanupIntervalSecs * 1000L);
      } catch (InterruptedException e) {
        logger.debug("Sleep interrupted, breaking out...", e);
        break;
      }

      removeClearedEntries();

      // check if it's time to print stats
      if (logger.isDebugEnabled()
          && Instant.now().getEpochSecond() - statsIntervalStart >= statsIntervalSecs) {
        printStats();
        statsIntervalStart = Instant.now().getEpochSecond();
      }
    }

    // print stats before stopping
    printStats();
  }
}
