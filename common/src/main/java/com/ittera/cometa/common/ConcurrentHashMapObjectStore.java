package com.ittera.cometa.common;

import com.ittera.cometa.common.lang.ObjectRef;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final int STATS_INTERVAL_SECS = 30;

  private final AtomicLong successfulStoreLookups = new AtomicLong();
  private final AtomicLong totalObjectsCleared = new AtomicLong();
  private final AtomicLong maxSize = new AtomicLong();

  // A map for all objects created by the this peer
  private final ConcurrentHashMap<ObjectRef, IdentifiableObject> objects;

  private class BackgroundProcessor extends Thread {

    private BackgroundProcessor() {
      setName("Object Store GC");
      setPriority(Thread.MIN_PRIORITY);
      setDaemon(true);
      setUncaughtExceptionHandler(
          (t, e) -> logger.error("Uncaught error in background processor", e));
    }

    private void printStats() {
      if (logger.isDebugEnabled()) {
        logger.debug("OBJECTS: max size={}", maxSize);
        logger.debug("OBJECTS: current size={}", size());
        logger.debug("OBJECTS: successful lookups={}", successfulStoreLookups.get());
        logger.debug("OBJECTS: total cleared={}", totalObjectsCleared);
      }
    }

    @Override
    public void run() {
      if (logger.isDebugEnabled()) {
        logger.debug("Starting OBJECTS stats");
      }
      long statsIntervalStart = Instant.now().getEpochSecond();
      while (true) {
        try {
          Thread.sleep(CLEANUP_INTERVAL_SECS * 1000L);
        } catch (InterruptedException e) {
          logger.error("Sleep interrupted, breaking out...", e);
          break;
        }
        // remove cleared entries
        long cleanupStart = Instant.now().toEpochMilli();
        final AtomicInteger clearedCount = new AtomicInteger();
        objects.values().stream()
            .filter(identObject -> identObject.object.get() == null)
            .map(identObject -> new ObjectRef(String.valueOf(identObject.hash)))
            .forEach(
                objectRef -> {
                  objects.remove(objectRef);
                  clearedCount.getAndIncrement();
                });
        totalObjectsCleared.addAndGet(clearedCount.get());
        if (logger.isDebugEnabled()) {
          long cleanupEnd = Instant.now().toEpochMilli();
          logger.debug(
              "Cleaned up {} objects map in {} ms", clearedCount.get(), cleanupEnd - cleanupStart);
        }

        // check if it's time to print stats
        if (logger.isDebugEnabled()) {
          if (Instant.now().getEpochSecond() - statsIntervalStart >= STATS_INTERVAL_SECS) {
            printStats();
            statsIntervalStart = Instant.now().getEpochSecond();
          }
        }
      }
    }
  }

  private void updateMaxSize() {
    final long maxSizeVal = maxSize.longValue();
    final long currentSize = size();
    if (currentSize > maxSizeVal) {
      maxSize.compareAndSet(maxSizeVal, currentSize);
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
      successfulStoreLookups.getAndIncrement();
      object = storedObject.object.get();
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
