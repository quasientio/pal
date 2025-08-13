/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.objects;

import com.quasient.pal.common.objects.ObjectRef;
import java.lang.ref.ReferenceQueue;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe implementation backed by a {@link ConcurrentHashMap} and a {@link
 * java.lang.ref.ReferenceQueue}. Dead entries are removed either:
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
 *   <li>Each call to {@link #storeObject(Object)} returns an {@link ObjectRef} derived from {@code
 *       System.identityHashCode(obj)}; collisions are possible but extremely rare.
 *   <li>The wrapped object is held in a {@link java.lang.ref.WeakReference}; once the GC clears
 *       that reference the cleaner will eventually remove the map entry.
 *   <li>{@code equals}/{@code hashCode} deliberately break the normal Map contract so that distinct
 *       but {@code .equals()} objects can coexist.
 * </ul>
 *
 * <b>WARNING</b>: {@code System.identityHashCode} is not guaranteed unique; if two live objects
 * share the same code, the earlier entry will be replaced.
 */
public final class ConcurrentHashMapObjectLookupStore implements AutoCloseable, ObjectLookupStore {

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
   * Concurrent map storing the association between {@link ObjectRef} and {@link
   * IdentifiableObject}.
   */
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  /** Reference queue for cleanup */
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
   * Internal constructor: allocates the map but does NOT start a cleaner. Use the static factory
   * methods instead.
   *
   * @param initialCapacity initial capacity of object map
   * @param loadFactor load factor of object map
   */
  private ConcurrentHashMapObjectLookupStore(
      int initialCapacity,
      float loadFactor,
      boolean syncManaged,
      ObjectLookupStoreStats objectLookupStoreStats) {
    objects = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    this.syncManaged = syncManaged;
    this.objectLookupStoreStats =
        objectLookupStoreStats != null ? objectLookupStoreStats : new ObjectLookupStoreStats();
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
        new ConcurrentHashMapObjectLookupStore(initialCapacity, loadFactor, false, null);
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
        DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, true, null);
  }

  /**
   * Creates a store without any automatic background cleaner. Intended for deterministic unit
   * tests; callers must initialize and pass their {@link ObjectLookupStoreStats}, attach their own
   * {@link ObjectLookupStoreCleaner}, starting it or calling {@link
   * ObjectLookupStoreCleaner#runOnce()}.
   *
   * @param stats an externally created ObjectLookupStoreStats instance
   */
  static ConcurrentHashMapObjectLookupStore createUnmanaged(ObjectLookupStoreStats stats) {
    return new ConcurrentHashMapObjectLookupStore(
        DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR, false, stats);
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
   * Generates a unique {@link ObjectRef} for the given object based on its identity hash code.
   *
   * @param object the object for which to generate a reference
   * @return a new {@link ObjectRef} instance encapsulating the object's identity
   */
  private ObjectRef generateObjectRef(Object object) {
    return ObjectRef.from(System.identityHashCode(object));
  }

  /**
   * Retrieves the underlying map storing object references and their corresponding identifiable
   * objects.
   *
   * @return the map of {@link ObjectRef} to {@link IdentifiableObject}
   */
  Map<ObjectRef, IdentifiableObject> getObjects() {
    return objects;
  }

  /**
   * Stores {@code object} in the map. If an entry with the same identity hash already exists and
   * its referent is still alive, the existing {@link ObjectRef} is returned; otherwise a new
   * wrapper is created.
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

    final ObjectRef key = generateObjectRef(object);
    IdentifiableObject existing = objects.get(key);

    if (existing != null && existing.get() != null) { // still alive
      objectLookupStoreStats.getSuccessfulStoreLookups().incrementAndGet();
      return key;
    }

    IdentifiableObject wrapper = new IdentifiableObject(object, key, refQueue);
    objects.put(key, wrapper);

    // if sync-managed perform queue cleanup
    if (syncManaged) {
      drainRefQueue();
    }

    updateMaxSize();
    return key;
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
    final IdentifiableObject storedObject = objects.get(objectRef);
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

  /** Clears all stored object references and their associated objects from the store. */
  @Override
  public void clear() {
    objects.clear();
  }

  /** Returns the reference queue where {@link IdentifiableObject}'s register themselves */
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
    return objects.mappingCount();
  }

  /**
   * Removes entries from the lookup store that have been cleared or are no longer valid.
   *
   * <p>This method scans the lookup store for entries whose associated objects have been cleared
   * and removes them. It also updates the statistics with the number of entries cleared.
   *
   * @return the number of cleared entries
   */
  int drainRefQueue() {

    int cleared = 0;
    // Drain without blocking.
    for (IdentifiableObject ref; (ref = (IdentifiableObject) refQueue.poll()) != null; ) {
      objects.remove(ref.getKey());
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
    return objects.isEmpty();
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
    final boolean containsObjectRef = objects.containsKey(objectRef);
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ containsObjectRef: {}", containsObjectRef);
    }
    return containsObjectRef;
  }

  /**
   * Removes the object associated with the specified {@link ObjectRef} from the store.
   *
   * @param objectRef the reference of the object to remove; must not be {@code null}
   * @return the removed object if it was present; otherwise {@code null}
   */
  @Override
  public Object remove(@Nonnull ObjectRef objectRef) {
    final IdentifiableObject storedObject = objects.remove(objectRef);
    if (storedObject != null) {
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
    objectRefs.forEach(objects::remove);
  }

  /**
   * Shuts down the cleaner (if present) and clears all internal data. Safe to call multiple times.
   */
  @Override
  public void close() {
    clear();
    if (cleaner != null) {
      cleaner.stop();
    }
  }
}
