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

/**
 * Provides methods for storing and retrieving objects using unique references.
 *
 * <p>This interface defines operations to add objects to the store, retrieve them by their {@link
 * ObjectRef} identifiers, check for their existence, remove them individually or in bulk, and query
 * the store's current size and emptiness status.
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
}
