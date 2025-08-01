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
import java.util.Collection;

/**
 * A concurrent, in-memory directory that maps arbitrary objects to opaque {@link ObjectRef}
 * handles.
 *
 * <p>The contract is intentionally minimal:
 *
 * <ul>
 *   <li>The implementation chooses how the reference is generated (e.g. {@code
 *       System.identityHashCode}).
 *   <li>The store may evict entries transparently once the payload object becomes unreachable.
 *   <li>All operations are thread-safe and non-blocking.
 * </ul>
 *
 * Implementations SHOULD expose operational metrics via {@link #getStats()} so callers can observe
 * peak size, successful lookup count, and garbage-collection clean-ups.
 */
public interface ObjectLookupStore {

  /**
   * Stores the specified object and returns a unique reference to it.
   *
   * @param object the object to be stored; must not be null
   * @return a unique {@link ObjectRef} representing the stored object
   */
  ObjectRef storeObject(Object object);

  /**
   * Retrieves the object associated with the given reference.
   *
   * @param objectRef the reference of the object to retrieve; must not be null
   * @return the object associated with {@code objectRef}, or {@code null} if no such object exists
   */
  Object lookupObject(ObjectRef objectRef);

  /**
   * Checks whether the store contains an object associated with the specified reference.
   *
   * @param objectRef the reference to check; must not be null
   * @return {@code true} if an object with the given reference exists in the store, {@code false}
   *     otherwise
   */
  boolean containsObjectRef(ObjectRef objectRef);

  /**
   * Removes the object associated with the specified reference from the store.
   *
   * @param objectRef the reference of the object to remove; must not be null
   * @return the removed object, or {@code null} if no object was associated with {@code objectRef}
   */
  Object remove(ObjectRef objectRef);

  /**
   * Removes all objects associated with the provided collection of references from the store.
   *
   * @param objectRefs a collection of references identifying the objects to remove; must not be
   *     null
   */
  void removeAll(Collection<ObjectRef> objectRefs);

  /** Removes all objects from the store, leaving it empty. */
  void clear();

  /**
   * Returns the number of objects currently stored.
   *
   * @return the size of the store
   */
  long size();

  /**
   * Checks if the store is empty.
   *
   * @return {@code true} if there are no objects in the store, {@code false} otherwise
   */
  boolean isEmpty();

  /**
   * Returns a live view of the store’s internal statistics.
   *
   * @return an always-non-null, thread-safe {@link ObjectLookupStoreStats} instance
   */
  ObjectLookupStoreStats getStats();
}
