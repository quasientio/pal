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

class ObjectStoreBackgroundProcessor extends Thread {

  private final Logger logger;
  private static final int DEFAULT_CLEANUP_INTERVAL_SECS = 2;
  private static final int DEFAULT_STATS_INTERVAL_SECS = 30;

  @Nonnull final ConcurrentHashMapObjectStore objectStore;
  @Nonnull private final ObjectStoreStats objectStoreStats;

  private final int cleanupIntervalSecs;
  private final int statsIntervalSecs;

  ObjectStoreBackgroundProcessor(
      @Nonnull ConcurrentHashMapObjectStore objectStore,
      @Nonnull ObjectStoreStats objectStoreStats,
      int cleanupIntervalSecs,
      int statsIntervalSecs) {
    logger = LoggerFactory.getLogger(ObjectStoreBackgroundProcessor.class);
    this.objectStore = Objects.requireNonNull(objectStore);
    this.objectStoreStats = Objects.requireNonNull(objectStoreStats);
    setName("Object Store GC");
    setPriority(Thread.MIN_PRIORITY);
    setDaemon(true);
    setUncaughtExceptionHandler(
        (t, e) -> logger.error("Uncaught error in ObjectStoreBackgroundProcessor", e));
    this.cleanupIntervalSecs = cleanupIntervalSecs;
    this.statsIntervalSecs = statsIntervalSecs;
  }

  ObjectStoreBackgroundProcessor(
      ConcurrentHashMapObjectStore objectStore, ObjectStoreStats objectStoreStats) {
    this(objectStore, objectStoreStats, DEFAULT_CLEANUP_INTERVAL_SECS, DEFAULT_STATS_INTERVAL_SECS);
  }

  private void printStats() {
    if (logger.isDebugEnabled()) {
      logger.debug("OBJECTS: max size={}", objectStoreStats.getMaxSize());
      logger.debug("OBJECTS: current size={}", objectStore.size());
      logger.debug(
          "OBJECTS: successful lookups={}", objectStoreStats.getSuccessfulStoreLookups().get());
      logger.debug("OBJECTS: total cleared={}", objectStoreStats.getTotalObjectsCleared());
    }
  }

  void removeClearedEntries() {
    long cleanupStart = Instant.now().toEpochMilli();
    final AtomicInteger clearedCount = new AtomicInteger();
    objectStore.getObjects().values().stream()
        .filter(identObject -> identObject.getObject().get() == null)
        .map(identObject -> ObjectRef.from(String.valueOf(identObject.getHash())))
        .forEach(
            objectRef -> {
              objectStore.getObjects().remove(objectRef);
              clearedCount.getAndIncrement();
            });
    objectStoreStats.getTotalObjectsCleared().addAndGet(clearedCount.get());
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
