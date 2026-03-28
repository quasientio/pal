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

import io.quasient.pal.common.objects.ObjectRef;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe implementation backed by a two-level map and a {@link java.lang.ref.ReferenceQueue}.
 *
 * <p>A primary map ({@code byRef}) provides O(1) lookup by {@link ObjectRef}. A secondary map
 * ({@code byHash}) groups entries by identity hash for deduplication during {@link
 * #storeObject(Object)}: the same object (by identity) always returns the same {@link ObjectRef},
 * and distinct objects that share an identity hash each receive their own unique ref.
 *
 * <p>Dead entries are removed either:
 *
 * <ul>
 *   <li>by a background thread in self-started {@link ObjectLookupStoreCleaner} (async-managed
 *       mode),
 *   <li>manually, under test control by an externally attached {@link ObjectLookupStoreCleaner}
 *       (unmanaged mode),
 *   <li>automatically by each thread calling {@link #storeObject(Object)}, in sync-managed mode.
 * </ul>
 *
 * <h2>Contract</h2>
 *
 * <ul>
 *   <li>Each call to {@link #storeObject(Object)} returns a unique, monotonically-generated {@link
 *       ObjectRef}. Storing the same object (identity) again returns the same ref.
 *   <li>The wrapped object is held in a {@link java.lang.ref.WeakReference}; once the GC clears
 *       that reference the cleaner will eventually remove the map entry.
 *   <li>Identity-hash collisions are handled correctly: distinct objects with the same {@code
 *       System.identityHashCode} each receive their own ref and coexist in the store.
 * </ul>
 */
public final class ConcurrentHashMapObjectLookupStore implements ObjectLookupStore {

  /** Logger instance for logging events and debug information. */
  private final Logger logger = LoggerFactory.getLogger(ConcurrentHashMapObjectLookupStore.class);

  /** The default initial capacity of the underlying {@link ConcurrentHashMap}. */
  static final int DEFAULT_INITIAL_CAPACITY = 10000;

  /** The default load factor for the underlying {@link ConcurrentHashMap}. */
  static final float DEFAULT_LOAD_FACTOR = 0.75f;

  /**
   * Optional cleaner. Non-null when this instance was built by {@link #createAsyncManaged(int,
   * float)}; may be {@code null} in unit tests.
   */
  private ObjectLookupStoreCleaner cleaner;

  /** Statistics tracker for monitoring the state and performance of the store. */
  private final ObjectLookupStoreStats objectLookupStoreStats;

  /**
   * Indicates sync-managed mode, where threads cleanup the ref queue during the call to {@link
   * #storeObject(Object)}.
   */
  private final boolean syncManaged;

  /**
   * Primary map: {@link ObjectRef} to {@link IdentifiableObject}. Provides O(1) lookup for {@link
   * #lookupObject(ObjectRef)} and friends.
   */
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> byRef;

  /**
   * Secondary map: identity hash to bucket of {@link IdentifiableObject}. Used by {@link
   * #storeObject(Object)} for deduplication (same object returns same ref) and correct handling of
   * identity-hash collisions (distinct objects get distinct refs).
   */
  private final ConcurrentHashMap<Integer, List<IdentifiableObject>> byHash =
      new ConcurrentHashMap<>();

  /**
   * Monotonic counter for generating unique {@link ObjectRef} values. Starts at 1 because {@code
   * ref == 0} is treated as "no reference" on the wire (see {@code
   * ObjUnwrappableAdapter.getRef()}).
   */
  private final AtomicInteger refCounter = new AtomicInteger(1);

  /**
   * Hash function used to compute the identity hash for bucket lookup. Defaults to {@link
   * System#identityHashCode}; tests may inject a custom function to force collisions
   * deterministically.
   */
  private final ToIntFunction<Object> identityHashFunction;

  /** Reference queue for cleanup. */
  private final ReferenceQueue<Object> refQueue = new ReferenceQueue<>();

  /** Updates the maximum size recorded for the store based on the current size. */
  private void updateMaxSize() {
    final long maxSizeVal = objectLookupStoreStats.getMaxSize().longValue();
    final long currentSize = size();
    if (currentSize > maxSizeVal) {
      objectLookupStoreStats.getMaxSize().compareAndSet(maxSizeVal, currentSize);
    }
  }

  /**
   * Internal constructor: allocates the maps but does NOT start a cleaner. Use the static factory
   * methods instead.
   *
   * @param initialCapacity initial capacity of the primary map
   * @param loadFactor load factor of the primary map
   * @param syncManaged whether to drain the ref queue synchronously on each store
   * @param objectLookupStoreStats externally provided stats, or {@code null} for a fresh instance
   * @param identityHashFunction hash function for bucket lookup
   */
  private ConcurrentHashMapObjectLookupStore(
      int initialCapacity,
      float loadFactor,
      boolean syncManaged,
      ObjectLookupStoreStats objectLookupStoreStats,
      ToIntFunction<Object> identityHashFunction) {
    byRef = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    this.syncManaged = syncManaged;
    this.objectLookupStoreStats =
        objectLookupStoreStats != null ? objectLookupStoreStats : new ObjectLookupStoreStats();
    this.identityHashFunction = identityHashFunction;
  }

  /**
   * Return a store whose cleaner runs on its own daemon thread and removes cleared entries at a
   * fixed cadence. Suitable for production use.
   *
   * @param initialCapacity the initial capacity of the underlying {@link ConcurrentHashMap}
   * @param loadFactor the load factor of the underlying {@link ConcurrentHashMap}
   */
  public static ConcurrentHashMapObjectLookupStore createAsyncManaged(
      int initialCapacity, float loadFactor) {
    ConcurrentHashMapObjectLookupStore store =
        new ConcurrentHashMapObjectLookupStore(
            initialCapacity, loadFactor, false, null, System::identityHashCode);
    ObjectLookupStoreCleaner c =
        new ObjectLookupStoreBackgroundProcessor(store, store.objectLookupStoreStats);
    c.start();
    store.cleaner = c;
    return store;
  }

  /**
   * Shortcut for {@link #createAsyncManaged(int, float)} using default capacity and load factor.
   */
  public static ConcurrentHashMapObjectLookupStore createAsyncManaged() {
    return createAsyncManaged(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Creates a store with a sync-managed cleanup policy, using the default store capacity and load
   * factor.
   */
  public static ConcurrentHashMapObjectLookupStore createSyncManaged() {
    return new ConcurrentHashMapObjectLookupStore(
        DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true, null, System::identityHashCode);
  }

  /**
   * Creates a store without any automatic background cleaner, using the default identity hash
   * function. Intended for deterministic unit tests; callers must initialize and pass their {@link
   * ObjectLookupStoreStats}, attach their own {@link ObjectLookupStoreCleaner}, starting it or
   * calling {@link ObjectLookupStoreCleaner#runOnce()}.
   *
   * @param stats an externally created ObjectLookupStoreStats instance
   */
  static ConcurrentHashMapObjectLookupStore createUnmanaged(ObjectLookupStoreStats stats) {
    return new ConcurrentHashMapObjectLookupStore(
        DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, false, stats, System::identityHashCode);
  }

  /**
   * Creates an unmanaged store with a custom identity hash function. Intended for unit tests that
   * need to force identity-hash collisions deterministically.
   *
   * @param stats an externally created ObjectLookupStoreStats instance
   * @param identityHashFunction custom hash function for bucket lookup
   */
  static ConcurrentHashMapObjectLookupStore createUnmanaged(
      ObjectLookupStoreStats stats, ToIntFunction<Object> identityHashFunction) {
    return new ConcurrentHashMapObjectLookupStore(
        DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, false, stats, identityHashFunction);
  }

  /**
   * Attaches a cleaner to this store. Idempotent; subsequent calls replace the previous cleaner
   * reference but do NOT stop it.
   *
   * @param c cleaner instance (may be {@code null} to detach)
   */
  void attachCleaner(ObjectLookupStoreCleaner c) {
    this.cleaner = c;
  }

  /**
   * Returns the primary map storing {@link ObjectRef} to {@link IdentifiableObject} associations.
   *
   * @return the primary map
   */
  Map<ObjectRef, IdentifiableObject> getObjects() {
    return byRef;
  }

  /**
   * Returns the secondary map grouping entries by identity hash. Package-private; exposed for test
   * assertions on bucket state.
   *
   * @return the secondary (bucket) map
   */
  Map<Integer, List<IdentifiableObject>> getByHash() {
    return byHash;
  }

  /**
   * Stores {@code object} in the store. If the same object (by identity) is already stored, the
   * existing {@link ObjectRef} is returned. Otherwise a new, unique ref is generated via a
   * monotonic counter.
   *
   * <p>Identity-hash collisions are handled correctly: distinct objects with the same identity hash
   * each receive their own unique ref and coexist in the same bucket.
   *
   * <p>Dead entries in the bucket are opportunistically evicted during the scan.
   *
   * @param object the object to store; must not be {@code null}
   * @return the {@link ObjectRef} associated with the stored object
   * @throws NullPointerException if the provided object is {@code null}
   */
  @Override
  public ObjectRef storeObject(Object object) {
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }

    final int hash = identityHashFunction.applyAsInt(object);
    final ObjectRef[] result = new ObjectRef[1];

    byHash.compute(
        hash,
        (k, bucket) -> {
          if (bucket == null) {
            bucket = new ArrayList<>(2);
          }

          // Scan: identity dedup + opportunistic dead-entry eviction
          Iterator<IdentifiableObject> it = bucket.iterator();
          while (it.hasNext()) {
            IdentifiableObject entry = it.next();
            Object referent = entry.get();
            if (referent == null) {
              byRef.remove(entry.getKey());
              it.remove();
              continue;
            }
            if (referent == object) {
              objectLookupStoreStats.getSuccessfulStoreLookups().incrementAndGet();
              result[0] = entry.getKey();
              return bucket;
            }
          }

          // Not found — create new entry with unique ref
          ObjectRef newRef = ObjectRef.from(refCounter.getAndIncrement());
          IdentifiableObject wrapper = new IdentifiableObject(object, newRef, refQueue, hash);
          bucket.add(wrapper);
          byRef.put(newRef, wrapper);
          result[0] = newRef;
          return bucket;
        });

    // if sync-managed perform queue cleanup
    if (syncManaged) {
      drainRefQueue();
    }

    updateMaxSize();
    return result[0];
  }

  /**
   * Looks up and retrieves the object associated with the given {@link ObjectRef}.
   *
   * @param objectRef the reference of the object to look up; must not be {@code null}
   * @return the associated object if found and not garbage collected; otherwise {@code null}
   * @throws NullPointerException if the provided {@code objectRef} is {@code null}
   */
  @Override
  public Object lookupObject(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in lookupObject w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject storedObject = byRef.get(objectRef);
    Object object = null;
    if (storedObject != null) {
      objectLookupStoreStats.getSuccessfulStoreLookups().getAndIncrement();
      object = storedObject.get();
    }
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ object: {}", object);
    }
    return object;
  }

  /**
   * Clears all stored object references and their associated objects from both the primary and
   * secondary maps, and resets the ref counter.
   */
  @Override
  public void clear() {
    byRef.clear();
    byHash.clear();
    refCounter.set(1);
  }

  /** Returns the reference queue where {@link IdentifiableObject}'s register themselves. */
  ReferenceQueue<Object> getRefQueue() {
    return refQueue;
  }

  /**
   * Returns the current number of object references stored in the store.
   *
   * @return the number of stored object references
   */
  @Override
  public long size() {
    return byRef.mappingCount();
  }

  /**
   * Removes a single {@link IdentifiableObject} entry from both the primary and secondary maps.
   * Called by {@link #drainRefQueue()} and by the background processor when cleaning up
   * garbage-collected entries.
   *
   * @param ref the entry to remove
   */
  void removeEntry(IdentifiableObject ref) {
    byRef.remove(ref.getKey());
    byHash.computeIfPresent(
        ref.getHash(),
        (k, bucket) -> {
          bucket.removeIf(ref::equals);
          return bucket.isEmpty() ? null : bucket;
        });
  }

  /**
   * Removes entries from the lookup store that have been cleared or are no longer valid.
   *
   * <p>This method drains the reference queue and removes each cleared entry from both the primary
   * and secondary maps. It also updates the statistics with the number of entries cleared.
   *
   * @return the number of cleared entries
   */
  int drainRefQueue() {
    int cleared = 0;
    // Drain without blocking.
    for (IdentifiableObject ref; (ref = (IdentifiableObject) refQueue.poll()) != null; ) {
      removeEntry(ref);
      cleared++;
    }

    if (cleared > 0) {
      objectLookupStoreStats.getTotalObjectsCleared().addAndGet(cleared);
      if (logger.isTraceEnabled()) {
        logger.trace("Cleaned up {} refs", cleared);
      }
    }

    return cleared;
  }

  /**
   * Checks whether the store currently contains no object references.
   *
   * @return {@code true} if the store is empty; {@code false} otherwise
   */
  @Override
  public boolean isEmpty() {
    return byRef.isEmpty();
  }

  /** {@inheritDoc} */
  @Override
  public ObjectLookupStoreStats getStats() {
    return objectLookupStoreStats;
  }

  /**
   * Determines whether the store contains a mapping for the specified {@link ObjectRef}.
   *
   * @param objectRef the reference to check for existence in the store; must not be {@code null}
   * @return {@code true} if the store contains the specified reference; {@code false} otherwise
   * @throws NullPointerException if the provided {@code objectRef} is {@code null}
   */
  @Override
  public boolean containsObjectRef(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in containsObjectRef w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final boolean containsObjectRef = byRef.containsKey(objectRef);
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ containsObjectRef: {}", containsObjectRef);
    }
    return containsObjectRef;
  }

  /**
   * Removes the object associated with the specified {@link ObjectRef} from both the primary and
   * secondary maps.
   *
   * @param objectRef the reference of the object to remove; must not be {@code null}
   * @return the removed object if it was present; otherwise {@code null}
   */
  @Override
  public Object remove(@Nonnull ObjectRef objectRef) {
    final IdentifiableObject storedObject = byRef.remove(objectRef);
    if (storedObject != null) {
      byHash.computeIfPresent(
          storedObject.getHash(),
          (k, bucket) -> {
            bucket.removeIf(storedObject::equals);
            return bucket.isEmpty() ? null : bucket;
          });
      return storedObject.get();
    }
    return null;
  }

  /**
   * Removes all objects associated with the provided collection of {@link ObjectRef} instances.
   *
   * @param objectRefs the collection of references to remove from the store; must not be {@code
   *     null}
   */
  @Override
  public void removeAll(Collection<ObjectRef> objectRefs) {
    objectRefs.forEach(this::remove);
  }

  /**
   * Shuts down the cleaner (if present) and clears all internal data. Safe to call multiple times.
   */
  public void close() {
    clear();
    if (cleaner != null) {
      cleaner.stop();
    }
  }
}
