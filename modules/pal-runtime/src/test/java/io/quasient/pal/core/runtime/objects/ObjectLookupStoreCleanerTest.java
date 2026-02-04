/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.Test;

/**
 * Test specifications for {@link ObjectLookupStoreCleaner} interface and implementations.
 *
 * <p>This test class focuses on the cleaner interface contract, particularly the close() method
 * behavior across different implementations.
 */
public class ObjectLookupStoreCleanerTest {

  /* ====================================================================== */
  /* Test specifications for issue #527 - Awaiting implementation in #528   */
  /* ====================================================================== */

  /**
   * Verifies that close() releases all resources successfully on a background processor.
   *
   * <p>Given: Open cleaner with resources (ObjectLookupStoreBackgroundProcessor with running worker
   * thread) When: close() called Then: All resources released without exception; worker thread
   * terminated
   */
  @Test
  public void testClose_closesResourcesSuccessfully() throws Exception {
    // Given: Open cleaner with resources
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    ObjectLookupStoreCleaner cleaner = null;
    Thread workerBefore = null;

    try {
      cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Start the cleaner to initialize the worker thread
      cleaner.start();

      // Verify the worker thread is running
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      workerBefore = (Thread) workerField.get(cleaner);

      assertNotNull("Worker thread should be created after start()", workerBefore);
      assertTrue("Worker thread should be alive before close()", workerBefore.isAlive());

      // When: close() called
      cleaner.close();

      // Then: All resources released without exception

      // Wait for the thread to terminate
      workerBefore.join(3000);

      // Verify the worker thread is terminated
      assertFalse("Worker thread should no longer be alive after close()", workerBefore.isAlive());

      // Verify the running flag is false
      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      boolean running = (Boolean) runningField.get(cleaner);
      assertFalse("running flag should be false after close()", running);

      cleaner = null; // Prevent double-close in finally
    } finally {
      if (cleaner != null) {
        cleaner.close();
      }
      store.close();
    }
  }

  /**
   * Verifies that close() is idempotent - calling it multiple times has no adverse effect.
   *
   * <p>Given: Cleaner that has already been closed When: close() called again Then: No exception
   * thrown; remains in closed state
   */
  @Test
  public void testClose_calledMultipleTimes_isIdempotent() throws Exception {
    // Given: Cleaner that has already been closed
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

    try {
      ObjectLookupStoreCleaner cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);
      cleaner.start();

      // Wait for thread to start
      Thread.sleep(50);

      // First close
      cleaner.close();

      // Verify the running flag is false after first close
      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      boolean runningAfterFirstClose = (Boolean) runningField.get(cleaner);
      assertFalse("running flag should be false after first close()", runningAfterFirstClose);

      // When: close() called again (multiple times)
      cleaner.close();
      cleaner.close();
      cleaner.close();

      // Then: No exception thrown; remains in closed state
      boolean runningAfterMultipleCloses = (Boolean) runningField.get(cleaner);
      assertFalse(
          "running flag should remain false after multiple close() calls",
          runningAfterMultipleCloses);
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that close() delegates to stop() as documented in the interface.
   *
   * <p>Given: Started cleaner When: close() called Then: stop() behavior is executed (thread
   * terminates, running flag is false)
   */
  @Test
  public void testClose_delegatesToStop() throws Exception {
    // Given: Started cleaner
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    ObjectLookupStoreCleaner cleaner = null;

    try {
      cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);
      cleaner.start();

      // Verify the worker thread is running
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread workerBefore = (Thread) workerField.get(cleaner);

      assertNotNull("Worker thread should be created after start()", workerBefore);
      assertTrue("Worker thread should be alive before close()", workerBefore.isAlive());

      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      assertTrue("running flag should be true before close()", (Boolean) runningField.get(cleaner));

      // When: close() called
      cleaner.close();

      // Then: stop() behavior is executed (thread terminates, running flag is false)
      // Wait for the thread to terminate
      workerBefore.join(3000);

      assertFalse("Worker thread should no longer be alive after close()", workerBefore.isAlive());
      assertFalse(
          "running flag should be false after close()", (Boolean) runningField.get(cleaner));

      // Verify worker field is set to null (as stop() does)
      Thread workerAfter = (Thread) workerField.get(cleaner);
      assertTrue(
          "Worker should be null or terminated after close()",
          workerAfter == null || !workerAfter.isAlive());

      cleaner = null; // Prevent double-close in finally
    } finally {
      if (cleaner != null) {
        cleaner.close();
      }
      store.close();
    }
  }

  /**
   * Verifies that close() on an unstarted cleaner does not throw.
   *
   * <p>Given: Cleaner that was never started When: close() called Then: No exception thrown
   */
  @Test
  public void testClose_onUnstartedCleaner_noException() throws Exception {
    // Given: Cleaner that was never started
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

    try {
      ObjectLookupStoreCleaner cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Verify the cleaner is not started
      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      assertFalse(
          "running flag should be false for unstarted cleaner",
          (Boolean) runningField.get(cleaner));

      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      assertTrue("Worker should be null for unstarted cleaner", workerField.get(cleaner) == null);

      // When: close() called on unstarted cleaner
      // Then: No exception thrown
      cleaner.close();

      // Verify state remains consistent
      assertFalse(
          "running flag should remain false after close() on unstarted cleaner",
          (Boolean) runningField.get(cleaner));
    } finally {
      store.close();
    }
  }
}
