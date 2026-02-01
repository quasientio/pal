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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
import java.util.List;
import org.junit.Ignore;
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
  // Test specifications for issue #466 - Awaiting implementation in #467
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
  @Ignore("Awaiting implementation in #467")
  public void createAsyncManaged_startsBackgroundCleaner() throws Exception {
    // Given: Stats object (created internally by factory)

    // When: createAsyncManaged() called
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createAsyncManaged();

      // Then: Store is created with a running background cleaner

      // TODO(#467): Implement test logic
      // - Use reflection to access the private 'cleaner' field
      // - Verify cleaner is not null
      // - Verify the cleaner is an instance of ObjectLookupStoreBackgroundProcessor
      // - Verify the background thread is running (via reflection on worker field)

      fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #467")
  public void createSyncManaged_cleansOnStore() {
    // Given: Sync-managed store

    // When: Object stored after previous reference cleared
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createSyncManaged();

      // Then: Cleanup runs during storeObject call

      // TODO(#467): Implement test logic
      // - Store an object and get its ObjectRef
      // - Get the IdentifiableObject wrapper from the internal map
      // - Simulate GC by calling clear() and enqueue() on the wrapper
      // - Store a new object (should trigger drainRefQueue)
      // - Verify the first entry is no longer in the store
      // - Verify stats record the cleared object

      fail("Not yet implemented");
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  /**
   * Verifies that storing two objects with the same identity hash code logs a warning about
   * collision.
   *
   * <p>Given: Two different objects with the same identity hash (requires mocking
   * System.identityHashCode or using special test objects) When: Both objects stored Then: Warning
   * is logged about the collision; second object replaces first
   *
   * <p>Implementation notes: This test is challenging because System.identityHashCode cannot be
   * directly mocked. Consider:
   *
   * <ul>
   *   <li>Using a log capture framework (e.g., LogCaptor) to verify warning is logged
   *   <li>Accepting that hash collisions are rare and may require many iterations
   *   <li>Or testing the observable behavior: when two objects have the same hash, the second
   *       replaces the first
   * </ul>
   *
   * <p>Note: The current implementation does NOT log warnings for hash collisions. This test
   * specification documents the expected behavior per the acceptance criteria. If the
   * implementation does not support this, the test should verify the actual collision behavior
   * (replacement semantics) instead.
   */
  @Test
  @Ignore("Awaiting implementation in #467")
  public void storeObject_hashCollision_logsWarning() {
    // Given: Two different objects with the same identity hash

    // When: Both objects stored

    // Then: Warning logged about collision (or verify replacement semantics)

    // TODO(#467): Implement test logic
    // Option A (if warning logging is added):
    // - Use LogCaptor or similar to capture log output
    // - Create two objects that collide (may need special handling)
    // - Verify warning is logged
    //
    // Option B (test observable behavior without logging):
    // - Store first object, get ObjectRef
    // - Simulate storing a second object that would have the same hash
    // - Verify the first object is replaced by the second
    // - This tests the replacement semantics documented in the class Javadoc

    fail("Not yet implemented");
  }

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
  @Ignore("Awaiting implementation in #467")
  public void drainRefQueue_multipleRefs_clearsAll() {
    // Given: Store with 5 cleared weak references in queue

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // When: drainRefQueue called

      // Then: All 5 entries removed from map

      // TODO(#467): Implement test logic
      // - Store 5 objects and capture their ObjectRefs
      // - Get the IdentifiableObject wrappers for each
      // - Clear and enqueue each wrapper to simulate GC
      // - Call store.drainRefQueue()
      // - Verify return value is 5
      // - Verify each ObjectRef is no longer in the store
      // - Verify stats.getTotalObjectsCleared() equals 5

      fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #467")
  public void close_stopsBackgroundProcessor() throws Exception {
    // Given: Async-managed store with running processor
    ConcurrentHashMapObjectLookupStore store = null;
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

      // When: close() called
      store.close();
      store = null; // Prevent double-close in finally

      // Then: Background processor stopped

      // TODO(#467): Implement test logic
      // - Get worker thread via: Thread workerBefore = (Thread) workerField.get(cleaner);
      // - Verify the worker thread is no longer alive (with timeout)
      // - Or verify the running flag is false via reflection

      fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #467")
  public void lookupObject_clearedWeakRef_returnsNull() {
    // Given: Store with object whose WeakReference was cleared

    ObjectLookupStoreStats stats = new ObjectLookupStoreStats();
    ConcurrentHashMapObjectLookupStore store = null;
    try {
      store = ConcurrentHashMapObjectLookupStore.createUnmanaged(stats);

      // When: lookupObject called

      // Then: Returns null (object was GC'd)

      // TODO(#467): Implement test logic
      // - Store an object and get its ObjectRef
      // - Get the IdentifiableObject wrapper from store.getObjects().get(ref)
      // - Call wrapper.clear() to simulate GC clearing the weak reference
      // - Call store.lookupObject(ref)
      // - Verify null is returned
      // - Verify store.containsObjectRef(ref) is still true (entry exists but referent is gone)

      fail("Not yet implemented");
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }

  // </editor-fold>
}
