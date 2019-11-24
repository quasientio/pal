package com.ittera.cometa.common;

import com.ittera.cometa.common.lang.ObjectRef;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ObjectRef -> Object map used for storing references to objects instantiated locally. It is
 * implemented using ConcurrentHashMap
 *
 * <pre>
 * Contract:
 * --=====--
 * Storing an object returns an ObjectRef (Plain wrapper around String)
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

  private static final Logger logger = LoggerFactory.getLogger(ConcurrentHashMapObjectStore.class);
  private static final int DEFAULT_INITIAL_CAPACITY = 10000;
  private static final float DEFAULT_LOAD_FACTOR = 0.75f;
  private static final int CLEANUP_INTERVAL_SECS = 10;

  private final AtomicLong successfulStoreLookups = new AtomicLong();

  // A map for all objects created by the this peer
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  private class BackgroundProcessor extends Thread {

    private BackgroundProcessor() {
      setPriority(Thread.MIN_PRIORITY);
      setDaemon(true);
      setUncaughtExceptionHandler(
          (t, e) -> logger.error("Uncaught error in background processor", e));
    }

    private void printStats() {
      if (logger.isDebugEnabled()) {
        logger.debug("OBJECTS: size={}", size());
        logger.debug("OBJECTS: successful lookups on store={}", successfulStoreLookups.get());
        final long cleared =
            objects.values().stream()
                .filter(identifiableObject -> identifiableObject.object.get() == null)
                .count();
        logger.debug("OBJECTS: cleared={}", cleared);
      }
    }

    @Override
    public void run() {
      logger.debug("Starting OBJECTS stats");
      // print stats every 30 secs
      long minuteStart = Instant.now().getEpochSecond();
      while (true) {
        try {
          Thread.sleep(CLEANUP_INTERVAL_SECS * 1000L);
        } catch (InterruptedException e) {
          logger.error("Sleep interrupted, breaking out...", e);
          break;
        }
        // remove cleared entries
        long cleanupStart = Instant.now().toEpochMilli();
        objects.values().stream()
            .filter(identObject -> identObject.object.get() == null)
            .map(identObject -> new ObjectRef(String.valueOf(identObject.hash)))
            .forEach(objects::remove);
        if (logger.isDebugEnabled()) {
          long cleanupEnd = Instant.now().toEpochMilli();
          logger.debug("Cleaned up objects map in {} ms", cleanupEnd - cleanupStart);
        }

        // check if it's time to print stats
        if (logger.isDebugEnabled()) {
          long now = Instant.now().getEpochSecond();
          if (now - minuteStart >= 60) {
            printStats();
            minuteStart = now;
          }
        }
      }
    }
  }

  // Wrapper class, used both for storing objects in the map, and looking them up.
  private static class IdentifiableObject {
    private final WeakReference<Object> object;
    private final int hash;

    IdentifiableObject(Object object) {
      Objects.requireNonNull(object);
      this.object = new WeakReference<>(object);
      this.hash = System.identityHashCode(object);
    }

    public final int hashCode() {
      return hash;
    }

    public boolean equals(Object other) {
      if (!(other instanceof IdentifiableObject)) {
        return false;
      }
      return other.hashCode() == this.hashCode();
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
    new BackgroundProcessor().start();
  }

  private ObjectRef generateObjectRef(Object object) {
    final int identHash = System.identityHashCode(object);
    return new ObjectRef(String.valueOf(identHash));
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
      successfulStoreLookups.getAndIncrement();
      if (logger.isTraceEnabled()) {
        logger.trace("out w/ (pre-existing) objectRef: {}", objectRef);
      }
    } else {
      objects.put(objectRef, new IdentifiableObject(object));
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
    final IdentifiableObject identifiableObject = objects.get(objectRef);
    Object object = null;
    if (identifiableObject != null) {
      object = identifiableObject.object.get();
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
}
