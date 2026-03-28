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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.collection.IsMapWithSize.aMapWithSize;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import org.junit.Test;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior. */
public class ConcurrentHashMapObjectLookupStoreTest {

  private ConcurrentHashMapObjectLookupStore objectLookupStore;

  // <editor-fold desc="storeObject">
  @Test
  public void storeObject_nullObject_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.storeObject(null);
      fail("Trying to store null should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void storeObject_newObject_objectRef() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ObjectRef objRef = objectLookupStore.storeObject(new ArrayList<>());
    assertNotNull(objRef);
    assertThat(objectLookupStore.size(), is(1L));
  }

  @Test
  public void storeObject_sameObjectTwice_getExistingRef() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ArrayList<Integer> listOfInts = new ArrayList<>();
    ObjectRef firstObjRef = objectLookupStore.storeObject(listOfInts);
    assertEquals(firstObjRef, objectLookupStore.storeObject(listOfInts));
    assertThat(objectLookupStore.size(), is(1L));
  }

  @Test
  public void storeObject_differentObjsStored_sizeAsExpected() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(34182);
    objectLookupStore.storeObject("some chars");
    assertThat(objectLookupStore.size(), is(3L));
  }

  @Test
  public void storeObject_equalButNotSameObjectStored_noException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(2L));
  }

  // </editor-fold>

  // <editor-fold desc="lookupObject">
  @Test
  public void lookupObject_nullObjectRefParam_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.lookupObject(null);
      fail("Trying to look up a null objectRef should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void lookupObject_objectIsStored_object() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ArrayList<Integer> listOfInts = new ArrayList<>();
    ObjectRef objRef = objectLookupStore.storeObject(listOfInts);
    assertThat(objectLookupStore.size(), is(1L));
    assertEquals(listOfInts, objectLookupStore.lookupObject(objRef));
  }

  @Test
  public void lookupObject_madeUpObjectRef_null() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertNull(objectLookupStore.lookupObject(ObjectRef.from("2323823")));
  }

  // </editor-fold>

  // <editor-fold desc="containsObjectRef">
  @Test
  public void containsObjectRef_nullObjectRef_nullPointerException() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      objectLookupStore.containsObjectRef(null);
      fail("Checking for a null key should throw a NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }

  @Test
  public void containsObjectRef_ofStoredObject_true() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    ObjectRef objectRef = objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(objectLookupStore.containsObjectRef(objectRef));
  }

  @Test
  public void containsObjectRef_fakeObjectRef_false() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertFalse(objectLookupStore.containsObjectRef(ObjectRef.from("2092373")));
  }

  // </editor-fold>

  // <editor-fold desc="size">
  @Test
  public void size_noObjectsStored_sizeIsZero() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void size_someObjectsStored_numberOfObjects() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new HashMap<>());
    assertThat(objectLookupStore.size(), is(2L));
  }

  // </editor-fold>

  // <editor-fold desc="clear">
  @Test
  public void clear_objectsStored_sizeIsZero() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    // store objects
    objectLookupStore.storeObject(new ArrayList<>());
    objectLookupStore.storeObject(new HashMap<>());
    assertThat(objectLookupStore.size(), is(2L));
    objectLookupStore.clear();
    assertThat(objectLookupStore.size(), is(0L));
  }

  // </editor-fold>

  // <editor-fold desc="isEmpty">
  @Test
  public void isEmpty_noObjectsStored_true() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void isEmpty_someObjectsStored_false() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(objectLookupStore.isEmpty());
  }

  // </editor-fold>

  // <editor-fold desc="getObjects">
  @Test
  public void getObjects_noObjectsStored_objectsEmpty() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
    assertTrue(objectLookupStore.isEmpty());
  }

  @Test
  public void getObjects_someObjectsStored_objects() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

    objectLookupStore.storeObject(new ArrayList<>());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(1)));
  }

  // </editor-fold>

  // <editor-fold desc="removeObject">
  @Test
  public void remove_objectIsStored_objectRemoved() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    final ObjectRef objRef = objectLookupStore.storeObject(new ArrayList<>());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertFalse(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(1)));

    // remove and check
    objectLookupStore.remove(objRef);
    assertFalse(objectLookupStore.containsObjectRef(objRef));
    assertTrue(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
  }

  @Test
  public void removeAll_someObjectsStored_allRemoved() {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    List<ObjectRef> objectRefList = new ArrayList<>();
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.add(objectLookupStore.storeObject(new ArrayList<>()));
    objectRefList.forEach(objRef -> assertTrue(objectLookupStore.containsObjectRef(objRef)));
    assertFalse(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(aMapWithSize(objectRefList.size())));

    // remove and check
    objectLookupStore.removeAll(objectRefList);
    objectRefList.forEach(objRef -> assertFalse(objectLookupStore.containsObjectRef(objRef)));
    assertTrue(objectLookupStore.isEmpty());
    assertThat(objectLookupStore.getObjects(), is(anEmptyMap()));
  }

  // </editor-fold>

  // ============================================================================
  // Test specifications for ObjectLookupStore
  // ============================================================================

  // <editor-fold desc="Factory methods and cleanup scenarios">

  /**
   * Verifies that createAsyncManaged() creates a store with a running background cleaner.
   *
   * <p>Given: Stats object (created internally by factory) When: createAsyncManaged() called Then:
   * Store is created with a running background cleaner (ObjectLookupStoreBackgroundProcessor)
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Use reflection to access the private 'cleaner' field
   *   <li>Verify cleaner is not null
   *   <li>Verify the cleaner is an instance of ObjectLookupStoreBackgroundProcessor
   *   <li>Verify the background thread is running (via reflection on worker field)
   * </ul>
   */
  @Test
  public void createAsyncManaged_startsBackgroundCleaner() throws Exception {
    // Given: Stats object (created internally by factory)

    // When: createAsyncManaged() called
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

      // Then: Store is created with a running background cleaner

      // Access the private 'cleaner' field
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);

      // Verify cleaner is not null
      assertNotNull("Cleaner should not be null for async-managed store", cleaner);

      // Verify the cleaner is an instance of ObjectLookupStoreBackgroundProcessor
      assertTrue(
          "Cleaner should be an ObjectLookupStoreBackgroundProcessor",
          cleaner instanceof ObjectLookupStoreBackgroundProcessor);

      // Get the worker thread from the background processor
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);

      // Allow thread to start
      Thread.sleep(50);

      Thread worker = (Thread) workerField.get(cleaner);

      // Verify worker thread exists and is alive
      assertNotNull("Worker thread should be created", worker);
      assertTrue("Worker thread should be alive", worker.isAlive());
      assertTrue("Worker thread should be a daemon", worker.isDaemon());
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that sync-managed store cleans the ref queue during storeObject calls.
   *
   * <p>Given: Sync-managed store with an object whose WeakReference was cleared When: Another
   * object stored after previous reference cleared and enqueued Then: Cleanup runs during
   * storeObject call; cleared entry is removed
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Create store using createSyncManaged()
   *   <li>Store an object, then simulate GC by clearing and enqueueing its wrapper
   *   <li>Store another object (triggers sync cleanup)
   *   <li>Verify the first entry is removed
   *   <li>Verify stats track the cleared object
   * </ul>
   */
  @Test
  public void createSyncManaged_cleansOnStore() {
    // Given: Sync-managed store
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createSyncManaged();

      // Store an object and get its ObjectRef
      Object obj1 = new Object();
      ObjectRef ref1 = store.storeObject(obj1);
      assertThat(store.size(), is(1L));

      // Get the IdentifiableObject wrapper from the internal map
      IdentifiableObject wrapper = store.getObjects().get(ref1);
      assertNotNull("Wrapper should exist for stored object", wrapper);

      // Simulate GC by calling clear() and enqueue() on the wrapper
      wrapper.clear();
      wrapper.enqueue();

      // Store a new object (should trigger drainRefQueue in sync-managed mode)
      Object obj2 = new Object();
      store.storeObject(obj2);

      // Then: Cleanup runs during storeObject call

      // Verify the first entry is no longer in the store
      assertFalse(
          "First object ref should be removed after cleanup", store.containsObjectRef(ref1));

      // Verify stats record the cleared object
      assertThat(store.getStats().getTotalObjectsCleared().get(), is(1L));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  // <editor-fold desc="Collision handling (two-level map)">

  /**
   * Two distinct objects forced to the same identity hash must each receive a unique {@link
   * ObjectRef} and be individually retrievable.
   */
  @Test
  public void storeObject_twoObjectsSameHash_bothStoredWithDistinctRefs() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object a = new Object();
      Object b = new Object();

      ObjectRef refA = store.storeObject(a);
      ObjectRef refB = store.storeObject(b);

      assertThat("Refs must differ", refA, is(not(refB)));
      assertThat("Both stored", store.size(), is(2L));
      assertEquals("Ref A resolves to A", a, store.lookupObject(refA));
      assertEquals("Ref B resolves to B", b, store.lookupObject(refB));

      // Verify bucket state
      List<IdentifiableObject> bucket = store.getByHash().get(42);
      assertNotNull("Bucket should exist", bucket);
      assertThat("Bucket should contain both entries", bucket.size(), is(2));
    } finally {
      store.close();
    }
  }

  /** Storing the same object (identity) twice returns the same {@link ObjectRef}. */
  @Test
  public void storeObject_sameObjectTwice_returnsSameRef() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object obj = new Object();

      ObjectRef first = store.storeObject(obj);
      ObjectRef second = store.storeObject(obj);

      assertEquals("Same object must return same ref", first, second);
      assertThat("Size must be 1", store.size(), is(1L));
      assertThat(
          "Successful lookup stat incremented", stats.getSuccessfulStoreLookups().get(), is(1L));
    } finally {
      store.close();
    }
  }

  /** Dead entries in a bucket are evicted opportunistically during a subsequent store. */
  @Test
  public void storeObject_collision_deadEntryEvictedDuringScan() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object dead = new Object();
      ObjectRef deadRef = store.storeObject(dead);

      // Simulate GC of the first object
      IdentifiableObject wrapper = store.getObjects().get(deadRef);
      wrapper.clear();
      // Note: NOT enqueued on the ReferenceQueue — eviction happens inside the bucket scan

      Object alive = new Object();
      store.storeObject(alive);

      // Dead entry should have been evicted from both maps
      assertFalse("Dead ref should be gone from byRef", store.containsObjectRef(deadRef));
      assertThat("Only the live object remains", store.size(), is(1L));

      List<IdentifiableObject> bucket = store.getByHash().get(42);
      assertThat("Bucket should contain only the live entry", bucket.size(), is(1));
    } finally {
      store.close();
    }
  }

  /** Three objects with the same hash all coexist in a single bucket. */
  @Test
  public void storeObject_collision_threeObjects_allRetrievable() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 7);
    try {
      Object a = new Object();
      Object b = new Object();
      Object c = new Object();

      ObjectRef ra = store.storeObject(a);
      ObjectRef rb = store.storeObject(b);
      ObjectRef rc = store.storeObject(c);

      assertThat("All three stored", store.size(), is(3L));
      assertEquals(a, store.lookupObject(ra));
      assertEquals(b, store.lookupObject(rb));
      assertEquals(c, store.lookupObject(rc));

      Set<ObjectRef> refs = new HashSet<>();
      refs.add(ra);
      refs.add(rb);
      refs.add(rc);
      assertThat("All refs must be distinct", refs.size(), is(3));
    } finally {
      store.close();
    }
  }

  /** Removing one collision partner leaves the other intact. */
  @Test
  public void remove_collision_removesOnlyTargeted() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object a = new Object();
      Object b = new Object();
      ObjectRef ra = store.storeObject(a);
      ObjectRef rb = store.storeObject(b);

      store.remove(ra);

      assertFalse("A's ref should be gone", store.containsObjectRef(ra));
      assertTrue("B's ref should survive", store.containsObjectRef(rb));
      assertEquals("B still retrievable", b, store.lookupObject(rb));
      assertThat("Size should be 1", store.size(), is(1L));

      // Bucket should still exist with one entry
      List<IdentifiableObject> bucket = store.getByHash().get(42);
      assertNotNull("Bucket should still exist", bucket);
      assertThat("Bucket should have 1 entry", bucket.size(), is(1));
    } finally {
      store.close();
    }
  }

  /** drainRefQueue removes the dead entry from both byRef and byHash. */
  @Test
  public void drainRefQueue_collision_cleansBothMaps() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object a = new Object();
      Object b = new Object();
      ObjectRef ra = store.storeObject(a);
      ObjectRef rb = store.storeObject(b);

      // Simulate GC of object A
      IdentifiableObject wrapperA = store.getObjects().get(ra);
      wrapperA.clear();
      wrapperA.enqueue();

      int cleared = store.drainRefQueue();
      assertThat("Should clear 1 entry", cleared, is(1));
      assertFalse("A removed from byRef", store.containsObjectRef(ra));
      assertTrue("B survives in byRef", store.containsObjectRef(rb));
      assertEquals("B still retrievable", b, store.lookupObject(rb));
      assertThat("Size should be 1", store.size(), is(1L));

      // Bucket should still exist with one entry
      List<IdentifiableObject> bucket = store.getByHash().get(42);
      assertNotNull("Bucket should still exist", bucket);
      assertThat("Bucket should have 1 entry", bucket.size(), is(1));
    } finally {
      store.close();
    }
  }

  /** When the last entry in a bucket is drained, the bucket itself is removed from byHash. */
  @Test
  public void drainRefQueue_lastInBucket_removesBucket() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      Object obj = new Object();
      ObjectRef ref = store.storeObject(obj);

      IdentifiableObject wrapper = store.getObjects().get(ref);
      wrapper.clear();
      wrapper.enqueue();

      store.drainRefQueue();

      assertNull("Bucket should be removed from byHash", store.getByHash().get(42));
      assertTrue("Store should be empty", store.isEmpty());
    } finally {
      store.close();
    }
  }

  /** clear() empties both byRef and byHash. */
  @Test
  public void clear_resetsBothMaps() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats, obj -> 42);
    try {
      store.storeObject(new Object());
      store.storeObject(new Object());
      assertThat(store.size(), is(2L));
      assertFalse(store.getByHash().isEmpty());

      store.clear();

      assertThat(store.size(), is(0L));
      assertTrue("byHash should be empty after clear", store.getByHash().isEmpty());
    } finally {
      store.close();
    }
  }

  // </editor-fold>

  // <editor-fold desc="Counter-based ObjectRef generation">

  /** Consecutive stores of distinct objects yield unique, non-zero refs. */
  @Test
  public void storeObject_producesUniqueRefs() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    try {
      Set<ObjectRef> refs = new HashSet<>();
      for (int i = 0; i < 100; i++) {
        refs.add(store.storeObject(new Object()));
      }
      assertThat("All 100 refs must be unique", refs.size(), is(100));
    } finally {
      store.close();
    }
  }

  /** The first generated ObjectRef must not be zero (0 = "no reference" on the wire). */
  @Test
  public void storeObject_refStartsAtOne() {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    try {
      ObjectRef ref = store.storeObject(new Object());
      assertThat("First ref must be 1 (0 is reserved for null on wire)", ref.getRef(), is(1));
    } finally {
      store.close();
    }
  }

  // </editor-fold>

  // <editor-fold desc="Concurrency">

  /** Multiple threads storing the same object must all receive the same ObjectRef. */
  @Test
  public void storeObject_concurrentSameObject_returnsSameRef() throws Exception {
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    try {
      Object shared = new Object();
      int threadCount = 8;
      CyclicBarrier barrier = new CyclicBarrier(threadCount);
      List<Thread> threads = new ArrayList<>();
      List<ObjectRef> results = new ArrayList<>();
      Object lock = new Object();

      for (int i = 0; i < threadCount; i++) {
        Thread t =
            new Thread(
                () -> {
                  try {
                    barrier.await();
                    ObjectRef ref = store.storeObject(shared);
                    synchronized (lock) {
                      results.add(ref);
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        threads.add(t);
        t.start();
      }
      for (Thread t : threads) {
        t.join(5000);
      }

      assertThat("All threads should have completed", results.size(), is(threadCount));
      ObjectRef expected = results.get(0);
      for (ObjectRef ref : results) {
        assertEquals("All threads must get the same ref", expected, ref);
      }
      assertThat("Only one entry in store", store.size(), is(1L));
    } finally {
      store.close();
    }
  }

  // </editor-fold>

  /**
   * Verifies that drainRefQueue removes all cleared entries in the queue.
   *
   * <p>Given: Store with 5 objects whose WeakReferences have been cleared and enqueued When:
   * drainRefQueue called Then: All 5 entries removed from map; stats updated with count of 5
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Use createUnmanaged() to get a store without background processing
   *   <li>Store 5 objects and capture their refs and wrappers
   *   <li>Clear and enqueue all 5 wrappers to simulate GC
   *   <li>Call drainRefQueue() directly (package-private method)
   *   <li>Verify return value is 5
   *   <li>Verify all 5 ObjectRefs are no longer in the store
   *   <li>Verify stats.getTotalObjectsCleared() is 5
   * </ul>
   */
  @Test
  public void drainRefQueue_multipleRefs_clearsAll() {
    // Given: Store with 5 cleared weak references in queue

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // Store 5 objects and capture their ObjectRefs
      List<ObjectRef> refs = new ArrayList<>();
      List<IdentifiableObject> wrappers = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Object obj = new Object();
        ObjectRef ref = store.storeObject(obj);
        refs.add(ref);
        wrappers.add(store.getObjects().get(ref));
      }

      assertThat("Should have 5 objects stored", store.size(), is(5L));

      // Clear and enqueue all 5 wrappers to simulate GC
      for (IdentifiableObject wrapper : wrappers) {
        wrapper.clear();
        wrapper.enqueue();
      }

      // When: drainRefQueue called
      int cleared = store.drainRefQueue();

      // Then: All 5 entries removed from map

      // Verify return value is 5
      assertThat("drainRefQueue should return 5", cleared, is(5));

      // Verify each ObjectRef is no longer in the store
      for (ObjectRef ref : refs) {
        assertFalse("ObjectRef should no longer be in the store", store.containsObjectRef(ref));
      }

      // Verify stats.getTotalObjectsCleared() equals 5
      assertThat(
          "Stats should track 5 cleared objects", stats.getTotalObjectsCleared().get(), is(5L));

      // Verify store is empty
      assertThat("Store should be empty", store.size(), is(0L));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that close() stops the background processor.
   *
   * <p>Given: Async-managed store with running background processor When: close() called Then:
   * Background processor is stopped; worker thread terminates
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Create store using createAsyncManaged()
   *   <li>Use reflection to get the cleaner and its worker thread
   *   <li>Verify worker thread is alive before close()
   *   <li>Call close()
   *   <li>Verify worker thread is no longer alive (or terminated within timeout)
   * </ul>
   */
  @Test
  public void close_stopsBackgroundProcessor() throws Exception {
    // Given: Async-managed store with running processor
    ConcurrentHashMapObjectLookupStore store = null;
    Thread workerBefore = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

      // Use reflection to access the cleaner field
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);
      assertNotNull("Cleaner should not be null for async-managed store", cleaner);

      // Get the worker thread from the background processor
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);

      // Verify worker is running before close
      // Note: Thread might not be immediately available; add small delay if needed
      Thread.sleep(50); // Allow thread to start

      workerBefore = (Thread) workerField.get(cleaner);
      assertNotNull("Worker thread should exist", workerBefore);
      assertTrue("Worker thread should be alive before close", workerBefore.isAlive());

      // When: close() called
      store.close();
      store = null; // Prevent double-close in finally

      // Then: Background processor stopped

      // Wait for the thread to terminate (with timeout)
      workerBefore.join(3000);

      // Verify the worker thread is no longer alive
      assertFalse("Worker thread should no longer be alive after close", workerBefore.isAlive());

      // Also verify the running flag is false via reflection
      Field runningField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("running");
      runningField.setAccessible(true);
      boolean running = (Boolean) runningField.get(cleaner);
      assertFalse("Running flag should be false after stop", running);
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that lookupObject returns null when the WeakReference has been cleared by GC.
   *
   * <p>Given: Store with an object whose WeakReference has been cleared (but not yet drained) When:
   * lookupObject called with the ObjectRef Then: Returns null because the object was GC'd
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Use createUnmanaged() to control cleanup timing
   *   <li>Store an object and get its ObjectRef
   *   <li>Get the IdentifiableObject wrapper and call clear() on it (simulates GC)
   *   <li>Do NOT enqueue or drain - the entry is still in the map but referent is null
   *   <li>Call lookupObject() with the original ObjectRef
   *   <li>Verify null is returned
   *   <li>Verify the entry is still in the map (containsObjectRef returns true)
   * </ul>
   */
  @Test
  public void lookupObject_clearedWeakRef_returnsNull() {
    // Given: Store with object whose WeakReference was cleared

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // Store an object and get its ObjectRef
      Object obj = new Object();
      ObjectRef ref = store.storeObject(obj);

      // Verify object is retrievable initially
      assertEquals("Object should be retrievable initially", obj, store.lookupObject(ref));
      assertTrue("Store should contain the ref", store.containsObjectRef(ref));

      // Get the IdentifiableObject wrapper from the internal map
      IdentifiableObject wrapper = store.getObjects().get(ref);
      assertNotNull("Wrapper should exist", wrapper);

      // Simulate GC by calling clear() on the wrapper
      // Note: Do NOT call enqueue() - the entry is still in the map but referent is null
      wrapper.clear();

      // When: lookupObject called
      Object result = store.lookupObject(ref);

      // Then: Returns null (object was GC'd)
      assertNull("lookupObject should return null for cleared weak reference", result);

      // Verify the entry is still in the map (containsObjectRef returns true)
      // The map entry exists, but its referent has been cleared
      assertTrue(
          "containsObjectRef should still return true (entry exists but referent is gone)",
          store.containsObjectRef(ref));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  // </editor-fold>

  // ============================================================================
  // Test specifications for ObjectLookupStore cleaner
  // ============================================================================

  // <editor-fold desc="ObjectLookupStore cleaner test specifications">

  /**
   * Verifies that createAsyncManaged(int, float) creates a store with custom parameters.
   *
   * <p>Given: Custom capacity (5000) and load factor (0.5f) When: createAsyncManaged(int, float)
   * called Then: Store created with specified parameters; background cleaner running
   */
  @Test
  public void testCreateAsyncManaged_createsWithCustomParams() throws Exception {
    // Given: Custom capacity and load factor
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      // When: createAsyncManaged(int, float) called
      store = ConcurrentHashMapObjectLookupStore.createAsyncManaged(5000, 0.5f);

      // Then: Store created with specified parameters
      assertNotNull("Store should not be null", store);

      // Verify cleaner is attached (via reflection)
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);
      assertNotNull("Cleaner should be attached", cleaner);

      // Verify cleaner is running by checking worker thread
      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread.sleep(50); // Allow thread to start
      Thread worker = (Thread) workerField.get(cleaner);
      assertNotNull("Worker thread should exist", worker);
      assertTrue("Worker thread should be alive", worker.isAlive());

      // Verify functionality by storing objects
      Object obj = new Object();
      ObjectRef ref = store.storeObject(obj);
      assertNotNull("storeObject should return ObjectRef", ref);
      assertEquals("lookupObject should retrieve stored object", obj, store.lookupObject(ref));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that createAsyncManaged() creates a store with default parameters.
   *
   * <p>Given: No parameters When: createAsyncManaged() called Then: Store created with default
   * parameters (DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR)
   *
   * <p>Note: This verifies the same behavior as createAsyncManaged_startsBackgroundCleaner, using
   * the naming convention from acceptance criteria.
   */
  @Test
  public void testCreateAsyncManaged_createsWithDefaults() throws Exception {
    // Given: No parameters
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      // When: createAsyncManaged() called
      store = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

      // Then: Store created with default parameters
      assertNotNull("Store should not be null", store);

      // Verify cleaner is attached and running
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);
      assertNotNull("Cleaner should be attached", cleaner);
      assertTrue(
          "Cleaner should be ObjectLookupStoreBackgroundProcessor",
          cleaner instanceof ObjectLookupStoreBackgroundProcessor);

      // Verify store is functional
      assertTrue("Store should be empty initially", store.isEmpty());
      assertEquals("Store size should be 0", 0L, store.size());
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that createUnmanaged() creates a store without background processing.
   *
   * <p>Given: Valid ObjectLookupStoreStats parameter When: createUnmanaged() called Then: Unmanaged
   * store created (no background processing); cleaner field is null
   */
  @Test
  public void testCreateUnmanaged_createsUnmanagedStore() throws Exception {
    // Given: Valid parameters
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();

    // When: createUnmanaged() called
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

    try {
      // Then: Unmanaged store created (no background processing)
      assertNotNull("Store should not be null", store);

      // Verify cleaner is null via reflection
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);
      assertNull("Cleaner should be null for unmanaged store", cleaner);

      // Verify store uses provided stats
      assertEquals("Store should use provided stats", stats, store.getStats());

      // Verify store functions correctly without background cleaner
      Object obj = new Object();
      ObjectRef ref = store.storeObject(obj);
      assertNotNull("storeObject should return ObjectRef", ref);
      assertEquals("lookupObject should retrieve stored object", obj, store.lookupObject(ref));

      // Verify manual drainRefQueue works
      IdentifiableObject wrapper = store.getObjects().get(ref);
      wrapper.clear();
      wrapper.enqueue();
      int cleared = store.drainRefQueue();
      assertEquals("drainRefQueue should clear 1 entry", 1, cleared);
      assertFalse("Ref should be removed after drainRefQueue", store.containsObjectRef(ref));
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that attachCleaner() successfully attaches a cleaner to the store.
   *
   * <p>Given: Store without cleaner (created via createUnmanaged) When: attachCleaner() called with
   * valid cleaner Then: Cleaner attached and active
   */
  @Test
  public void testAttachCleaner_attachesSuccessfully() throws Exception {
    // Given: Store without cleaner
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    ObjectLookupStoreCleaner cleaner = null;

    try {
      // Verify cleaner is initially null
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      assertNull("Cleaner should be null initially", cleanerField.get(store));

      // When: attachCleaner() called with valid cleaner
      cleaner = new ObjectLookupStoreBackgroundProcessor(store, stats, 10);
      store.attachCleaner(cleaner);

      // Then: Cleaner attached and active
      assertEquals("Cleaner should be attached", cleaner, cleanerField.get(store));

      // Verify cleaner can be started and processes refs
      cleaner.start();

      // Add object, simulate GC, and verify cleanup
      Object obj = new byte[256];
      ObjectRef ref = store.storeObject(obj);
      IdentifiableObject wrapper = store.getObjects().get(ref);
      wrapper.clear();
      wrapper.enqueue();

      // Wait for background cleanup
      long deadline = System.currentTimeMillis() + 500;
      while (store.containsObjectRef(ref) && System.currentTimeMillis() < deadline) {
        Thread.sleep(10);
      }
      assertFalse("Background cleaner should have processed the ref", store.containsObjectRef(ref));
    } finally {
      if (cleaner != null) {
        cleaner.stop();
      }
      store.close();
    }
  }

  /**
   * Verifies that clear() removes all objects from the store.
   *
   * <p>Given: Store with multiple objects When: clear() called Then: Store is empty
   *
   * <p>Note: This verifies the same behavior as clear_objectsStored_sizeIsZero, using the naming
   * convention from acceptance criteria.
   */
  @Test
  public void testClear_removesAllObjects() {
    // Given: Store with multiple objects
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      store.storeObject(new Object());
      store.storeObject(new Object());
      store.storeObject(new Object());
      assertEquals("Store should have 3 objects", 3L, store.size());

      // When: clear() called
      store.clear();

      // Then: Store is empty
      assertEquals("Store should be empty after clear()", 0L, store.size());
      assertTrue("isEmpty() should return true after clear()", store.isEmpty());
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that getRefQueue() returns a non-null ReferenceQueue.
   *
   * <p>Given: Store instance When: getRefQueue() called Then: Non-null ReferenceQueue returned
   */
  @Test
  public void testGetRefQueue_returnsQueue() {
    // Given: Store instance
    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);
    try {
      // When: getRefQueue() called
      java.lang.ref.ReferenceQueue<Object> refQueue = store.getRefQueue();

      // Then: Non-null ReferenceQueue returned
      assertNotNull("getRefQueue() should return non-null ReferenceQueue", refQueue);
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that isEmpty() returns true for an empty store.
   *
   * <p>Given: Empty store When: isEmpty() called Then: Returns true
   *
   * <p>Note: This verifies the same behavior as isEmpty_noObjectsStored_true, using the naming
   * convention from acceptance criteria.
   */
  @Test
  public void testIsEmpty_returnsTrueWhenEmpty() {
    // Given: Empty store
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      // When: isEmpty() called
      boolean result = store.isEmpty();

      // Then: Returns true
      assertTrue("isEmpty() should return true for empty store", result);
      assertEquals("size() should be 0", 0L, store.size());
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that isEmpty() returns false when store contains objects.
   *
   * <p>Given: Store with objects When: isEmpty() called Then: Returns false
   *
   * <p>Note: This verifies the same behavior as isEmpty_someObjectsStored_false, using the naming
   * convention from acceptance criteria.
   */
  @Test
  public void testIsEmpty_returnsFalseWhenNotEmpty() {
    // Given: Store with objects
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      store.storeObject(new Object());

      // When: isEmpty() called
      boolean result = store.isEmpty();

      // Then: Returns false
      assertFalse("isEmpty() should return false for non-empty store", result);
      assertEquals("size() should be 1", 1L, store.size());
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that getStats() returns a non-null stats object.
   *
   * <p>Given: Store instance When: getStats() called Then: Non-null stats object returned
   */
  @Test
  public void testGetStats_returnsStats() {
    // Given: Store instance
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    try {
      // When: getStats() called
      ObjectLookupStoreStats stats = store.getStats();

      // Then: Non-null stats object returned
      assertNotNull("getStats() should return non-null stats object", stats);

      // Verify initial counter values are 0
      assertEquals(
          "totalObjectsCleared should be 0 initially", 0L, stats.getTotalObjectsCleared().get());
      assertEquals(
          "successfulStoreLookups should be 0 initially",
          0L,
          stats.getSuccessfulStoreLookups().get());
      assertEquals("maxSize should be 0 initially", 0L, stats.getMaxSize().get());
    } finally {
      store.close();
    }
  }

  /**
   * Verifies that close() stops both cleaner and background processor.
   *
   * <p>Given: Store with attached cleaner and processor When: close() called Then: Both cleaner and
   * processor stopped; store cleared
   *
   * <p>Note: This verifies the same behavior as close_stopsBackgroundProcessor, using the naming
   * convention from acceptance criteria.
   */
  @Test
  public void testClose_closesCleanerAndProcessor() throws Exception {
    // Given: Store with attached cleaner and processor
    ConcurrentHashMapObjectLookupStore store =
        ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    Thread workerBefore = null;

    try {
      // Store some objects
      store.storeObject(new Object());
      store.storeObject(new Object());
      assertEquals("Store should have 2 objects", 2L, store.size());

      // Get reference to worker thread before close
      Field cleanerField = ConcurrentHashMapObjectLookupStore.class.getDeclaredField("cleaner");
      cleanerField.setAccessible(true);
      ObjectLookupStoreCleaner cleaner = (ObjectLookupStoreCleaner) cleanerField.get(store);

      Field workerField = ObjectLookupStoreBackgroundProcessor.class.getDeclaredField("worker");
      workerField.setAccessible(true);
      Thread.sleep(50); // Allow thread to start
      workerBefore = (Thread) workerField.get(cleaner);
      assertNotNull("Worker should exist before close", workerBefore);
      assertTrue("Worker should be alive before close", workerBefore.isAlive());

      // When: close() called
      store.close();

      // Then: Both cleaner and processor stopped; store cleared
      workerBefore.join(3000);
      assertFalse("Worker should be stopped after close", workerBefore.isAlive());

      // Verify store is cleared
      assertEquals("Store should be empty after close", 0L, store.size());

      store = null; // Prevent double-close in finally
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  // </editor-fold>
}
