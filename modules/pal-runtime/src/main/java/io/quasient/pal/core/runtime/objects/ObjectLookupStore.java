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
import java.util.Collection;
import javax.annotation.Nonnull;

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
  Object remove(@Nonnull ObjectRef objectRef);

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
