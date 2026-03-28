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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.common.objects.ObjectRef;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class ObjectLookupStoreBackgroundProcessorTest {

  @Test
  public void removeClearedEntries_removesQueuedReferences() {

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, 60);

      // 1. add an object to the store
      Object bigObject = new byte[256];
      ObjectRef ref = store.storeObject(bigObject);

      // 2. simulate GC: clear & enqueue wrapper into the same queue
      IdentifiableObject wrapper = store.getObjects().get(ref);
      wrapper.clear(); // referent = null
      wrapper.enqueue(); // place it on the store’s ReferenceQueue

      // 3. invoke the cleaner
      proc.runOnce();

      // 4. verify the entry is gone and stats updated
      assertThat(store.containsObjectRef(ref), is(false));
      assertThat(stats.getTotalObjectsCleared().get(), is(1L));

      // sanity-check: if we insert again, lookup works
      ObjectRef newRef = store.storeObject("foo");
      assertThat(store.lookupObject(newRef), is("foo"));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /* ------------------------------------------------------------------ */
  /* empty queue ⇒ cleaner is a no-op
  /* ------------------------------------------------------------------ */
  @Test
  public void removeClearedEntries_whenQueueEmpty_doesNothing() {

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, 60);

      // put one live object, don’t clear/enqueue anything
      ObjectRef ref = store.storeObject("foo");

      proc.runOnce(); // queue is empty

      assertThat(store.containsObjectRef(ref), is(true)); // still there
      assertThat(stats.getTotalObjectsCleared().get(), is(0L)); // counter unchanged
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /* ------------------------------------------------------------------ */
  /* many refs enqueued at once are all removed in one pass
  /* ------------------------------------------------------------------ */
  @Test
  public void removeClearedEntries_bulkRemoval() {

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, 60);

      int n = 25;
      ObjectRef[] refs = new ObjectRef[n];
      for (int i = 0; i < n; i++) {
        refs[i] = store.storeObject(i);
        IdentifiableObject w = store.getObjects().get(refs[i]);
        w.clear();
        w.enqueue();
      }

      proc.runOnce();

      for (ObjectRef r : refs) {
        assertThat(store.containsObjectRef(r), is(false));
      }
      assertThat(stats.getTotalObjectsCleared().get(), is((long) n));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /* ------------------------------------------------------------------ */
  /* lookup counter increments only for live entries
  /* ------------------------------------------------------------------ */
  @Test
  public void successfulLookupCounter_incrementsForLiveObject() {

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // manual cleaner bound to this store; not started
      ObjectLookupStoreCleaner cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 60);
      store.attachCleaner(cleaner);

      ObjectRef ref = store.storeObject("bar");
      store.lookupObject(ref);
      store.lookupObject(ref);
      assertThat(stats.getSuccessfulStoreLookups().get(), is(2L));

      // simulate GC and drain deterministically
      IdentifiableObject w = store.getObjects().get(ref);
      w.clear();
      w.enqueue();
      cleaner.runOnce();

      store.lookupObject(ref); // now null
      assertThat(stats.getSuccessfulStoreLookups().get(), is(2L));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /** maxSize tracks peak size correctly */
  @Test
  public void maxSizeTracking_recordsPeak() {

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // add 10 objects, capturing their refs for later removal
      List<ObjectRef> refs = new ArrayList<>();
      for (int i = 0; i < 10; i++) refs.add(store.storeObject(i));
      assertThat(stats.getMaxSize().get() >= 10, is(true));

      // remove 9 (keep the first)
      for (int i = 1; i < 10; i++) store.remove(refs.get(i));

      // add one more – peak should stay at least 10
      store.storeObject("another");
      assertThat(stats.getMaxSize().get() >= 10, is(true));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /* ====================================================================== */
  /* Test specifications for ObjectLookupStoreBackgroundProcessor   */
  /* ====================================================================== */

  /**
   * Verifies that start() launches a daemon thread and processes weak references.
   *
   * <p>Given: Store with background processor configured with 10ms timeout When: Add object to
   * store, clear weak reference, enqueue to ReferenceQueue Then: Background thread cleans up entry
   * within 100ms; stats updated
   */
  @Test
  public void start_launchesDaemonThread_andProcessesReferences() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Start the background thread
      proc.start();

      // Verify worker thread is running and is a daemon
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread worker = (Thread) workerField.get(proc);
      assertThat("Worker thread should be created", worker, notNullValue());
      assertThat("Worker thread should be a daemon", worker.isDaemon(), is(true));

      // Add an object to the store
      Object obj = new byte[256];
      ObjectRef ref = store.storeObject(obj);
      assertThat(store.containsObjectRef(ref), is(true));

      // Simulate GC: clear and enqueue the wrapper
      IdentifiableObject wrapper = store.getObjects().get(ref);
      wrapper.clear();
      wrapper.enqueue();

      // Poll for cleanup with timeout (500ms should be more than enough)
      long deadline = System.currentTimeMillis() + 500;
      while (store.containsObjectRef(ref) && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }

      // Verify cleanup occurred
      assertThat(
          "Entry should be cleaned up by background thread",
          store.containsObjectRef(ref),
          is(false));
      assertThat(
          "Stats should record cleared object", stats.getTotalObjectsCleared().get(), is(1L));

    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that calling start() twice is idempotent.
   *
   * <p>Given: Background processor not started When: start() called twice Then: Second call is
   * no-op; only one worker thread exists
   */
  @Test
  public void start_calledTwice_isIdempotent() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Call start() the first time
      proc.start();

      // Capture the worker thread reference
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread firstWorker = (Thread) workerField.get(proc);
      assertThat(
          "Worker thread should be created after first start()", firstWorker, notNullValue());

      // Call start() the second time
      proc.start();

      // Verify the same worker thread is still in use
      Thread secondWorker = (Thread) workerField.get(proc);
      assertThat(
          "Worker thread should remain the same after second start()",
          secondWorker,
          is(firstWorker));

    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that stop() terminates the background thread gracefully.
   *
   * <p>Given: Running background processor When: stop() called Then: Thread terminates within 2
   * seconds; running flag is false
   */
  @Test
  public void stop_terminatesBackgroundThread_gracefully() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Start the processor
      proc.start();

      // Get reference to worker thread
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread worker = (Thread) workerField.get(proc);
      assertThat("Worker should be running", worker.isAlive(), is(true));

      // Record start time
      long startTime = System.currentTimeMillis();

      // Call stop()
      proc.stop();

      // Verify stop() completed within 2 seconds
      long elapsed = System.currentTimeMillis() - startTime;
      assertThat("stop() should complete within 2 seconds", elapsed, lessThan(2000L));

      // Verify running flag is false
      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      boolean running = (Boolean) runningField.get(proc);
      assertThat("running flag should be false after stop()", running, is(false));

      // Verify worker thread is terminated
      Thread workerAfterStop = (Thread) workerField.get(proc);
      assertThat("Worker thread should be null after stop()", workerAfterStop, nullValue());

    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that the worker thread handles interruption gracefully.
   *
   * <p>Given: Running background processor with worker thread When: Worker thread is interrupted
   * externally Then: Thread exits gracefully; no exception propagated
   */
  @Test
  public void stop_whenInterrupted_logsAndExits() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    AtomicReference<Throwable> uncaughtException = new AtomicReference<>();
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 100);

      // Start the processor
      proc.start();

      // Get reference to worker thread
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread worker = (Thread) workerField.get(proc);
      assertThat("Worker should be running", worker.isAlive(), is(true));

      // Set up uncaught exception handler to detect any propagated exceptions
      worker.setUncaughtExceptionHandler((t, e) -> uncaughtException.set(e));

      // Interrupt the worker thread directly
      worker.interrupt();

      // Wait for thread to terminate
      worker.join(2000);

      // Verify thread terminated gracefully
      assertThat("Worker thread should have terminated", worker.isAlive(), is(false));
      assertThat("No uncaught exception should propagate", uncaughtException.get(), nullValue());

    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that runOnce() throws IllegalStateException after start() is called.
   *
   * <p>Given: Background processor already started When: runOnce() called Then:
   * IllegalStateException thrown
   */
  @Test(expected = IllegalStateException.class)
  public void runOnce_afterStart_throwsIllegalStateException() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Start the background processing
      proc.start();

      // This should throw IllegalStateException
      proc.runOnce();

    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that the 3-arg constructor respects the custom timeout parameter.
   *
   * <p>Given: 3-arg constructor with 50ms timeout When: Background processor created Then: Cleanup
   * timeout is set to 50ms (verify via reflection)
   */
  @Test
  public void constructor_customTimeout_isRespected() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      int customTimeout = 50;
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, customTimeout);

      // Use reflection to verify the cleanupTimeoutMs field
      Field timeoutField =
          ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("cleanupTimeoutMs");
      timeoutField.setAccessible(true);
      int actualTimeout = (Integer) timeoutField.get(proc);

      assertThat("Custom timeout should be set correctly", actualTimeout, is(customTimeout));

    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /* ====================================================================== */
  /* Test specifications for ObjectLookupStore cleaner   */
  /* ====================================================================== */

  /**
   * Verifies that the 3-arg constructor creates a processor with the specified custom timeout.
   *
   * <p>Given: Custom timeout value (e.g., 5000ms) When: Constructor called with custom timeout
   * Then: Processor created with specified timeout
   *
   * <p>Note: This verifies the same behavior as constructor_customTimeout_isRespected, using the
   * naming convention from acceptance criteria.
   */
  @Test
  public void testConstructorWithCustomTimeout_createsProcessorWithTimeout() throws Exception {
    // Given: Custom timeout value
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      int customTimeout = 5000;

      // When: Constructor called with custom timeout
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, customTimeout);

      // Then: Processor created with specified timeout
      Field timeoutField =
          ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("cleanupTimeoutMs");
      timeoutField.setAccessible(true);
      int actualTimeout = (Integer) timeoutField.get(proc);

      assertThat("Custom timeout should be set correctly", actualTimeout, is(customTimeout));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that start() creates and launches a background daemon thread.
   *
   * <p>Given: Processor not started When: start() called Then: Background thread is created and
   * running
   *
   * <p>Note: This verifies the same behavior as start_launchesDaemonThread_andProcessesReferences,
   * using the naming convention from acceptance criteria.
   */
  @Test
  public void testStart_createsBackgroundThread() throws Exception {
    // Given: Processor not started
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // When: start() called
      proc.start();

      // Then: Background thread is created and running
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread worker = (Thread) workerField.get(proc);

      assertThat("Worker thread should be created", worker, notNullValue());
      assertThat("Worker thread should be alive", worker.isAlive(), is(true));
      assertThat("Worker thread should be a daemon", worker.isDaemon(), is(true));
    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that stop() terminates the worker thread gracefully.
   *
   * <p>Given: Running processor When: stop() called Then: Thread terminates gracefully
   *
   * <p>Note: This verifies the same behavior as stop_terminatesBackgroundThread_gracefully, using
   * the naming convention from acceptance criteria.
   */
  @Test
  public void testStop_terminatesThread() throws Exception {
    // Given: Running processor
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);
      proc.start();

      // Capture worker thread reference before stop
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread worker = (Thread) workerField.get(proc);
      assertThat("Worker should be running before stop", worker.isAlive(), is(true));

      // When: stop() called
      long startTime = System.currentTimeMillis();
      proc.stop();
      long elapsed = System.currentTimeMillis() - startTime;

      // Then: Thread terminates gracefully
      assertThat("stop() should complete within 2 seconds", elapsed, lessThan(2000L));

      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      boolean running = (Boolean) runningField.get(proc);
      assertThat("running flag should be false after stop()", running, is(false));

      Thread workerAfterStop = (Thread) workerField.get(proc);
      assertThat("Worker thread should be null after stop()", workerAfterStop, nullValue());
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that runOnce() drains the reference queue and processes all queued items.
   *
   * <p>Given: Processor with items in queue When: runOnce() called Then: Queue is drained and items
   * processed
   *
   * <p>Note: This verifies the same behavior as removeClearedEntries_removesQueuedReferences, using
   * the naming convention from acceptance criteria.
   */
  @Test
  public void testRunOnce_drainsQueue() {
    // Given: Processor with items in queue
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      ObjectLookupStoreBackgroundProcessor proc =
          new ObjectLookupStoreBackgroundProcessor(store, stats, 60);

      // Add objects to the store and simulate GC by clearing and enqueueing
      int n = 5;
      ObjectRef[] refs = new ObjectRef[n];
      for (int i = 0; i < n; i++) {
        refs[i] = store.storeObject(new byte[64 + i]);
        IdentifiableObject wrapper = store.getObjects().get(refs[i]);
        wrapper.clear();
        wrapper.enqueue();
      }

      // When: runOnce() called
      int cleared = proc.runOnce();

      // Then: Queue is drained and items processed
      assertThat("runOnce should return count of cleared entries", cleared, is(n));
      assertThat(
          "Stats should track cleared objects", stats.getTotalObjectsCleared().get(), is(5L));
      for (ObjectRef ref : refs) {
        assertThat(
            "All refs should be removed from store", store.containsObjectRef(ref), is(false));
      }
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that the background loop drains multiple references in batch.
   *
   * <p>Given: 10 objects added to store When: All references cleared and enqueued Then: All 10
   * entries cleaned within timeout; batch processing occurs
   */
  @Test
  public void backgroundLoop_drainsMultipleReferences_inBatch() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);

      // Add 10 objects to the store
      int n = 10;
      ObjectRef[] refs = new ObjectRef[n];
      IdentifiableObject[] wrappers = new IdentifiableObject[n];
      for (int i = 0; i < n; i++) {
        refs[i] = store.storeObject(new byte[64 + i]);
        wrappers[i] = store.getObjects().get(refs[i]);
      }

      // Verify all objects are in the store
      assertThat("Store should contain all objects", store.size(), is((long) n));

      // Start the background processor
      proc.start();

      // Clear and enqueue all references to simulate GC
      for (int i = 0; i < n; i++) {
        wrappers[i].clear();
        wrappers[i].enqueue();
      }

      // Poll for cleanup with timeout (500ms should be enough)
      long deadline = System.currentTimeMillis() + 500;
      while (stats.getTotalObjectsCleared().get() < n && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }

      // Verify all objects were cleared
      assertThat(
          "All objects should be cleared",
          stats.getTotalObjectsCleared().get(),
          greaterThanOrEqualTo((long) n));

      // Verify all refs are removed from store
      for (ObjectRef ref : refs) {
        assertThat("Ref should be removed from store", store.containsObjectRef(ref), is(false));
      }

      // Verify store is empty
      assertThat("Store should be empty", store.size(), is(0L));

    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that the background processor correctly cleans entries from both the primary ({@code
   * byRef}) and secondary ({@code byHash}) maps when two objects share the same identity hash and
   * one is garbage collected.
   */
  @Test
  public void backgroundProcessor_collision_cleansBothMaps() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    ObjectLookupStoreBackgroundProcessor proc = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
      proc = new ObjectLookupStoreBackgroundProcessor(store, stats, 5);

      Object alive = new Object();
      Object dead = new Object();
      ObjectRef aliveRef = store.storeObject(alive);
      ObjectRef deadRef = store.storeObject(dead);
      assertThat("Both stored", store.size(), is(2L));

      // Simulate GC of one collision partner
      IdentifiableObject deadWrapper = store.getObjects().get(deadRef);
      deadWrapper.clear();
      deadWrapper.enqueue();

      proc.start();

      // Wait for background cleanup
      long deadline = System.currentTimeMillis() + 500;
      while (store.containsObjectRef(deadRef) && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }

      assertThat("Dead ref removed from byRef", store.containsObjectRef(deadRef), is(false));
      assertThat("Live ref still in byRef", store.containsObjectRef(aliveRef), is(true));
      assertThat("Live object still retrievable", store.lookupObject(aliveRef), is(alive));
      assertThat("Size should be 1", store.size(), is(1L));

      // Bucket should still exist with one entry
      List<IdentifiableObject> bucket = store.getByHash().get(42);
      assertThat("Bucket should have 1 entry", bucket, notNullValue());
      assertThat("Bucket size should be 1", bucket.size(), is(1));
    } finally {
      if (proc != null) {
        proc.stop();
      }
      if (store != null) {
        store.close();
      }
    }
  }
}
