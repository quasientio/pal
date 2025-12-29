/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.common.objects.ObjectRef;
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

      // add 10 objects
      for (int i = 0; i < 10; i++) store.storeObject(i);
      assertThat(stats.getMaxSize().get() >= 10, is(true));

      // remove 9
      for (int i = 1; i < 10; i++)
        store.remove(ObjectRef.from(String.valueOf(System.identityHashCode(i))));

      // add one more – peak should stay at least 10
      store.storeObject("another");
      assertThat(stats.getMaxSize().get() >= 10, is(true));
    } finally {
      if (store != null) {
        store.close();
      }
    }
  }
}
