package com.ittera.cometa.concentrator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * ObjectStore is a ObjectRef -> Object map used for storing references to objects instantiated locally.
 * It is implemented using Guava's BiMap
 * <p>
 * Contract:
 * --=====--
 * Storing an object returns an ObjectRef (String)
 * <p>
 * We wrap objects before putting them in the map, so the BiMap implementation uses our overriden hashCode(),
 * which delegates to the value's System.identityHashCode, and not the normal hashCode.
 * <p>
 * This allows mapping values that are equal() -- This is OK because we don't care about the general Map contract.
 * <p>
 * <b>WARNING</b>: We assume System.identityHashCode will return distinct ints for different objects.
 * This may be the most probable, but is not guaranteed according to the JDK javadocs.
 * <p>
 * TODO: although unlikely, the identityHashCode may break this store. Find alternative.
 * TODO: store objects as WeakReferences -> until then, no objects will get garbage cleaned!
 * TODO: replace trace enter and exit stmts (see issue #5)
 */
public final class ObjectStore {

  private static final Logger logger = LogManager.getLogger(ObjectStore.class);

  //A map for all objects created by the Concentrator.
  private static final BiMap<String, ObjectStore.IdentifiableObject> objectBiMap = HashBiMap.create();
  private static final BiMap<String, ObjectStore.IdentifiableObject> syncdObjectMap = Maps.synchronizedBiMap(objectBiMap);

  //for concurrency
  private static final Object lock = new Object();
  private static final AtomicInteger objectSequence = new AtomicInteger(0);

  //Wrapper class, used both for storing objects in the BiMap, and looking them up.
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

  private ObjectStore() {
    //avoid instantiation
  }

  /**
   * Calling store() twice on the same object throws an IllegalArgumentException.
   *
   * @param object
   * @return
   */
  private static String generateObjectRef(Object object) {
    final Long currentTimeMillis = System.currentTimeMillis();
    final int identHash = System.identityHashCode(object);
    return String.format("%d:%d:%d", objectSequence.incrementAndGet(), currentTimeMillis, identHash);
  }

  public static String storeObject(Object object) {
    logger.traceEntry("with object: {}", object);
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    String objectRef = generateObjectRef(object);
    final IdentifiableObject wrappedObject = new IdentifiableObject(object);
    if (syncdObjectMap.containsValue(wrappedObject)) {
      objectRef = syncdObjectMap.inverse().get(wrappedObject);
      logger.traceExit("with (pre-existing) objectRef: {}", objectRef);
      return objectRef;
    }
    syncdObjectMap.put(objectRef, wrappedObject);
    logger.traceExit("with objectRef: {}", objectRef);
    return objectRef;
  }

  public static Object lookupObject(String objectRef) {
    logger.traceEntry("with objectRef: {}", objectRef);
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject identifiableObject = syncdObjectMap.get(objectRef);
    Object object = null;
    if (identifiableObject != null) {
      object = identifiableObject.object;
    }
    logger.traceExit("with object: {}", object);
    return object;
  }

  public static String lookupObjectRef(Object object) {
    logger.traceEntry("with object: {}", object);
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    final String objectRef = syncdObjectMap.inverse().get(new IdentifiableObject(object));
    logger.traceExit("with objectRef: {}", objectRef);
    return objectRef;
  }

  public static void clear() {
    syncdObjectMap.clear();
  }

  public static int size() {
    return syncdObjectMap.size();
  }

  public static boolean isEmpty() {
    return size() == 0;
  }

  public static boolean containsValue(Object object) {
    logger.traceEntry("with object: {}", object);
    if (object == null) {
      throw new NullPointerException("object cannot be null");
    }
    final boolean containsValue = syncdObjectMap.containsValue(new IdentifiableObject(object));
    logger.traceExit("with containsValue: {}", containsValue);
    return containsValue;
  }

  public static boolean containsObjectRef(String objectRef) {
    logger.traceEntry("with objectRef: {}", objectRef);
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final boolean containsObjectRef = syncdObjectMap.containsKey(objectRef);
    logger.traceExit("with containsObjectRef: {}", containsObjectRef);
    return containsObjectRef;
  }

  public static Object remove(String objectRef) {
    logger.traceEntry("with objectRef: {}", objectRef);
    if (objectRef == null) {
      throw new NullPointerException("objectRef cannot be null");
    }
    final IdentifiableObject identifiableObject = syncdObjectMap.remove(objectRef);
    Object object = null;
    if (identifiableObject != null) {
      object = identifiableObject.object;
    }
    logger.traceExit("with object: {}", object);
    return object;
  }
}
