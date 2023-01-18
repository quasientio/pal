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

package net.ittera.pal.common.objects;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ObjectRef -> Object map used for storing references to objects instantiated locally. It is
 * implemented using ConcurrentHashMap
 *
 * <pre>
 * Contract:
 * --=====--
 * storeObject() creates and saves a WeakReference and returns an ObjectRef (Plain wrapper around String)
 *
 * We wrap objects before putting them in the map, so the Map implementation uses our overriden hashCode(),
 * which delegates to the value's System.identityHashCode, and not the normal hashCode.
 *
 * This allows mapping values that are equal() -- This is OK because we don't care about the general Map contract.
 *
 * <b>WARNING</b>: We assume System.identityHashCode will return distinct ints for different objects.
 * This may be the most probable, but is not guaranteed according to the JDK javadocs.
 * </pre>
 *
 * TODO: although unlikely, the identityHashCode may break this store. Find alternative. <br>
 * TODO: replace trace enter and exit stmts (see issue #5) <br>
 */
public final class ConcurrentHashMapObjectStore implements ObjectStore {

  private final Logger logger = LoggerFactory.getLogger(ConcurrentHashMapObjectStore.class);
  static final int DEFAULT_INITIAL_CAPACITY = 10000;
  static final float DEFAULT_LOAD_FACTOR = 0.75f;

  private final ObjectStoreStats objectStoreStats = new ObjectStoreStats();

  // A map for all objects created by this peer
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  private void updateMaxSize() {
    final long maxSizeVal = objectStoreStats.getMaxSize().longValue();
    final long currentSize = size();
    if (currentSize > maxSizeVal) {
      objectStoreStats.getMaxSize().compareAndSet(maxSizeVal, currentSize);
    }
  }

  public ConcurrentHashMapObjectStore() {
    this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
  }

  public ConcurrentHashMapObjectStore(int initialCapacity, float loadFactor) {
    objects = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    startBackgroundProcessor();
  }

  private void startBackgroundProcessor() {
    new ObjectStoreBackgroundProcessor(this, objectStoreStats).start();
  }

  private ObjectRef generateObjectRef(Object object) {
    final int identHash = System.identityHashCode(object);
    return ObjectRef.from(String.valueOf(identHash));
  }

  Map<ObjectRef, IdentifiableObject> getObjects() {
    return objects;
  }

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
      objectStoreStats.getSuccessfulStoreLookups().getAndIncrement();
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

  @Override
  public Object lookupObject(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject storedObject = objects.get(objectRef);
    Object object = null;
    if (storedObject != null) {
      objectStoreStats.getSuccessfulStoreLookups().getAndIncrement();
      object = storedObject.getObject().get();
    }
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ object: {}", object);
    }
    return object;
  }

  @Override
  public void clear() {
    objects.clear();
  }

  @Override
  public long size() {
    return objects.mappingCount();
  }

  @Override
  public boolean isEmpty() {
    return objects.isEmpty();
  }

  @Override
  public boolean containsObjectRef(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ objectRef: {}", objectRef);
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

  @Override
  public Object remove(@Nonnull ObjectRef objectRef) {
    final IdentifiableObject storedObject = objects.remove(objectRef);
    if (storedObject != null) {
      return storedObject.getObject().get();
    }
    return null;
  }

  @Override
  public void removeAll(Collection<ObjectRef> objectRefs) {
    objectRefs.forEach(objects::remove);
  }
}
