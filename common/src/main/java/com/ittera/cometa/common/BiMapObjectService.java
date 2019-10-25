package com.ittera.cometa.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.ittera.cometa.common.lang.ObjectRef;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BiMapObjectService is a ObjectRef -> Object map used for storing references to objects
 * instantiated locally. It is implemented using Guava's BiMap
 *
 * <pre>
 * Contract:
 * --=====--
 * Storing an object returns an ObjectRef (Plain wrapper around String)
 *
 * We wrap objects before putting them in the map, so the BiMap implementation uses our overriden hashCode(),
 * which delegates to the value's System.identityHashCode, and not the normal hashCode.
 *
 * This allows mapping values that are equal() -- This is OK because we don't care about the general Map contract.
 *
 * <b>WARNING</b>: We assume System.identityHashCode will return distinct ints for different objects.
 * This may be the most probable, but is not guaranteed according to the JDK javadocs.
 * </pre>
 *
 * TODO: although unlikely, the identityHashCode may break this store. Find alternative. <br>
 * TODO: store objects as WeakReferences -> until then, no objects will get garbage cleaned! <br>
 * TODO: replace trace enter and exit stmts (see issue #5) <br>
 */
public final class BiMapObjectService implements ObjectService {

  private static final Logger logger = LoggerFactory.getLogger(BiMapObjectService.class);

  // A map for all objects created by the this peer
  private static final BiMap<ObjectRef, BiMapObjectService.IdentifiableObject> objectBiMap =
      HashBiMap.create();
  private static final BiMap<ObjectRef, BiMapObjectService.IdentifiableObject> syncdObjectMap =
      Maps.synchronizedBiMap(objectBiMap);

  // for concurrency
  private static final AtomicInteger objectSequence = new AtomicInteger(0);

  // Wrapper class, used both for storing objects in the BiMap, and looking them up.
  private static class IdentifiableObject {
    private final Object object;

    IdentifiableObject(Object object) {
      this.object = object;
    }

    public final int hashCode() {
      return System.identityHashCode(object);
    }

    public boolean equals(Object other) {
      if (!(other instanceof IdentifiableObject)) {
        return false;
      }
      IdentifiableObject otherIObj = (IdentifiableObject) other;
      if (otherIObj.object == object) {
        return true;
      }
      return other.hashCode() == this.hashCode();
    }
  }

  private ObjectRef generateObjectRef(Object object) {
    final Long currentTimeMillis = System.currentTimeMillis();
    final int identHash = System.identityHashCode(object);
    return new ObjectRef(
        String.format("%d:%d:%d", objectSequence.incrementAndGet(), currentTimeMillis, identHash));
  }

  @Override
  public ObjectRef storeObject(Object object) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ object: {}", object);
    }
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    ObjectRef objectRef = generateObjectRef(object);
    final IdentifiableObject wrappedObject = new IdentifiableObject(object);
    synchronized (syncdObjectMap) {
      if (syncdObjectMap.containsValue(wrappedObject)) {
        objectRef = syncdObjectMap.inverse().get(wrappedObject);
        if (logger.isTraceEnabled()) {
          logger.trace("out w/ (pre-existing) objectRef: {}", objectRef);
        }
        return objectRef;
      } else {
        syncdObjectMap.put(objectRef, wrappedObject);
        if (logger.isTraceEnabled()) {
          logger.trace("out w/ objectRef: {}", objectRef);
        }
        return objectRef;
      }
    }
  }

  @Override
  public Object lookupObject(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject identifiableObject = syncdObjectMap.get(objectRef);
    Object object = null;
    if (identifiableObject != null) {
      object = identifiableObject.object;
    }
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ object: {}", object);
    }
    return object;
  }

  @Override
  public ObjectRef lookupObjectRef(Object object) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ object: {}", object);
    }
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    final ObjectRef objectRef = syncdObjectMap.inverse().get(new IdentifiableObject(object));
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ objectRef: {}", objectRef);
    }
    return objectRef;
  }

  @Override
  public void clear() {
    syncdObjectMap.clear();
  }

  @Override
  public int size() {
    return syncdObjectMap.size();
  }

  @Override
  public boolean isEmpty() {
    return size() == 0;
  }

  @Override
  public boolean containsValue(Object object) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ object: {}", object);
    }
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    final boolean containsValue = syncdObjectMap.containsValue(new IdentifiableObject(object));
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ containsValue: {}", containsValue);
    }
    return containsValue;
  }

  @Override
  public boolean containsObjectRef(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final boolean containsObjectRef = syncdObjectMap.containsKey(objectRef);
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ containsObjectRef: {}", containsObjectRef);
    }
    return containsObjectRef;
  }

  @Override
  public Object remove(ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ objectRef: {}", objectRef);
    }
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject identifiableObject = syncdObjectMap.remove(objectRef);
    Object object = null;
    if (identifiableObject != null) {
      object = identifiableObject.object;
    }
    if (logger.isTraceEnabled()) {
      logger.trace("out w/ object: {}", object);
    }
    return object;
  }
}
