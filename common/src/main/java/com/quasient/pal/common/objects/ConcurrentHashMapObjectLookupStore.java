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

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a thread-safe mapping between {@link ObjectRef} instances and locally instantiated
 * objects. This store utilizes a {@link ConcurrentHashMap} to ensure concurrent access and
 * modifications.
 *
 * <p>Key Features:
 *
 * <ul>
 *   <li>Stores references to objects using {@link ObjectRef}, which wraps a unique {@link String}
 *       identifier.
 *   <li>Implements custom hashing for object references based on {@link
 *       System#identityHashCode(Object)} to ensure uniqueness.
 *   <li>Maintains statistics through {@link ObjectLookupStoreStats} and manages a background
 *       processing thread.
 *   <li>Provides functionalities to store, lookup, and remove objects efficiently in a concurrent
 *       environment.
 * </ul>
 *
 * <pre>
 * Contract:
 * --=====--
 * storeObject() creates and saves a WeakReference and returns an ObjectRef,
 * which is a plain wrapper around String.
 *
 * We wrap objects before putting them in the map, so that the Map implementation
 * uses our overriden hashCode(), which delegates to the value's System.identityHashCode,
 * and not the normal hashCode.
 *
 * This allows mapping values that are equal() -- This is OK because we don't care about
 * the general Map contract.
 *
 * <b>WARNING</b>: We assume System.identityHashCode will return distinct ints for different objects.
 * This may be the most probable, but is not guaranteed according to the JDK javadocs.
 * </pre>
 *
 * TODO: although unlikely, the identityHashCode may break this store. Find alternative. <br>
 * TODO: replace trace enter and exit stmts (see issue #5) <br>
 */
public final class ConcurrentHashMapObjectLookupStore implements ObjectLookupStore {

  /** Logger instance for logging events and debug information. */
  private final Logger logger = LoggerFactory.getLogger(ConcurrentHashMapObjectLookupStore.class);

  /** The default initial capacity of the underlying {@link ConcurrentHashMap}. */
  static final int DEFAULT_INITIAL_CAPACITY = 10000;

  /** The default load factor for the underlying {@link ConcurrentHashMap}. */
  static final float DEFAULT_LOAD_FACTOR = 0.75f;

  /** Statistics tracker for monitoring the state and performance of the store. */
  private final ObjectLookupStoreStats objectLookupStoreStats = new ObjectLookupStoreStats();

  /**
   * Concurrent map storing the association between {@link ObjectRef} and {@link
   * IdentifiableObject}.
   */
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  /** Updates the maximum size recorded for the store based on the current size. */
  private void updateMaxSize() {
    final long maxSizeVal = objectLookupStoreStats.getMaxSize().longValue();
    final long currentSize = size();
    if (currentSize > maxSizeVal) {
      objectLookupStoreStats.getMaxSize().compareAndSet(maxSizeVal, currentSize);
    }
  }

  /**
   * Constructs a new {@code ConcurrentHashMapObjectLookupStore} with default initial capacity and
   * load factor.
   *
   * <p>Initializes the underlying map and starts the background processor.
   */
  public ConcurrentHashMapObjectLookupStore() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
  }

  /**
   * Constructs a new {@code ConcurrentHashMapObjectLookupStore} with specified initial capacity and
   * load factor.
   *
   * @param initialCapacity the initial capacity of the underlying {@link ConcurrentHashMap}
   * @param loadFactor the load factor of the underlying {@link ConcurrentHashMap}
   */
  public ConcurrentHashMapObjectLookupStore(int initialCapacity, float loadFactor) {
    objects = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    startBackgroundProcessor();
  }

  /** Starts the background processing thread responsible for managing store operations. */
  private void startBackgroundProcessor() {
    ObjectLookupStoreBackgroundProcessor processor =
        new ObjectLookupStoreBackgroundProcessor(this, objectLookupStoreStats);
    processor.start();
  }

  /**
   * Generates a unique {@link ObjectRef} for the given object based on its identity hash code.
   *
   * @param object the object for which to generate a reference
   * @return a new {@link ObjectRef} instance encapsulating the object's identity
   */
  private ObjectRef generateObjectRef(Object object) {
    final int identHash = System.identityHashCode(object);
    return ObjectRef.from(String.valueOf(identHash));
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
   * Stores the provided object in the lookup store and returns its corresponding {@link ObjectRef}.
   *
   * <p>If the object is already present, the existing {@link ObjectRef} is returned. Otherwise, a
   * new reference is created, stored, and returned.
   *
   * @param object the object to store; must not be {@code null}
   * @return the {@link ObjectRef} associated with the stored object
   * @throws NullPointerException if the provided object is {@code null}
   */
  @Override
  public ObjectRef storeObject(Object object) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ object: {}", object);
    }
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    final ObjectRef objectRef = generateObjectRef(object);
    final IdentifiableObject storedObject = objects.get(objectRef);

    if (storedObject != null) {
      objectLookupStoreStats.getSuccessfulStoreLookups().getAndIncrement();
      if (logger.isTraceEnabled()) {
        logger.trace("out w/ (pre-existing) objectRef: {}", objectRef);
      }
    } else {
      objects.put(objectRef, new IdentifiableObject(object));
      updateMaxSize();
      if (logger.isTraceEnabled()) {
        logger.trace("out w/ objectRef: {}", objectRef);
      }
    }
    return objectRef;
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
      object = storedObject.getObject().get();
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
   * Checks whether the store currently contains no object references.
   *
   * @return {@code true} if the store is empty; {@code false} otherwise
   */
  @Override
  public boolean isEmpty() {
    return objects.isEmpty();
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
      return storedObject.getObject().get();
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
}
