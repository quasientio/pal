package com.ittera.cometa.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.util.concurrent.AbstractIdleService;

/**
 * BiMapObjectService is a ObjectRef -> Object map used for storing references to objects instantiated locally.
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
public final class BiMapObjectService extends AbstractIdleService implements ObjectService {

	private static final Logger logger = LoggerFactory.getLogger(BiMapObjectService.class);

	//A map for all objects created by the Concentrator.
	private static final BiMap<String, BiMapObjectService.IdentifiableObject> objectBiMap = HashBiMap.create();
	private static final BiMap<String, BiMapObjectService.IdentifiableObject> syncdObjectMap =
		Maps.synchronizedBiMap(objectBiMap);

	//for concurrency
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

	@Override
	protected void startUp() throws Exception {
		//TODO initialize internal queues, etc.

	}

	@Override
	protected void shutDown() throws Exception {
		//TODO call the executor's shutdown() or shutdownNow()

	}

	private String generateObjectRef(Object object) {
		final Long currentTimeMillis = System.currentTimeMillis();
		final int identHash = System.identityHashCode(object);
		return String.format("%d:%d:%d", objectSequence.incrementAndGet(), currentTimeMillis, identHash);
	}

	public String storeObject(Object object) {
		logger.trace("in w/ object: {}", object);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (object == null) {
			throw new NullPointerException("object cannot be null");
		}
		String objectRef = generateObjectRef(object);
		final IdentifiableObject wrappedObject = new IdentifiableObject(object);
		synchronized (syncdObjectMap) {
			if (syncdObjectMap.containsValue(wrappedObject)) {
				objectRef = syncdObjectMap.inverse().get(wrappedObject);
				logger.trace("out w/ (pre-existing) objectRef: {}", objectRef);
				return objectRef;
			} else {
				syncdObjectMap.put(objectRef, wrappedObject);
				logger.trace("out w/ objectRef: {}", objectRef);
				return objectRef;
			}
		}
	}

	public Object lookupObject(String objectRef) {
		logger.trace("in w/ objectRef: {}", objectRef);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (objectRef == null) {
			throw new NullPointerException("objectRef cannot be null");
		}
		final IdentifiableObject identifiableObject = syncdObjectMap.get(objectRef);
		Object object = null;
		if (identifiableObject != null) {
			object = identifiableObject.object;
		}
		logger.trace("out w/ object: {}", object);
		return object;
	}

	public String lookupObjectRef(Object object) {
		logger.trace("in w/ object: {}", object);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (object == null) {
			throw new NullPointerException("object cannot be null");
		}
		final String objectRef = syncdObjectMap.inverse().get(new IdentifiableObject(object));
		logger.trace("out w/ objectRef: {}", objectRef);
		return objectRef;
	}

	public void clear() {
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		syncdObjectMap.clear();
	}

	public int size() {
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		return syncdObjectMap.size();
	}

	public boolean isEmpty() {
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		return size() == 0;
	}

	public boolean containsValue(Object object) {
		logger.trace("in w/ object: {}", object);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (object == null) {
			throw new NullPointerException("object cannot be null");
		}
		final boolean containsValue = syncdObjectMap.containsValue(new IdentifiableObject(object));
		logger.trace("out w/ containsValue: {}", containsValue);
		return containsValue;
	}

	public boolean containsObjectRef(String objectRef) {
		logger.trace("in w/ objectRef: {}", objectRef);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (objectRef == null) {
			throw new NullPointerException("objectRef cannot be null");
		}
		final boolean containsObjectRef = syncdObjectMap.containsKey(objectRef);
		logger.trace("out w/ containsObjectRef: {}", containsObjectRef);
		return containsObjectRef;
	}

	public Object remove(String objectRef) {
		logger.trace("in w/ objectRef: {}", objectRef);
		if (!isRunning()) {
			throw new IllegalStateException("Service not running");
		}
		if (objectRef == null) {
			throw new NullPointerException("objectRef cannot be null");
		}
		final IdentifiableObject identifiableObject = syncdObjectMap.remove(objectRef);
		Object object = null;
		if (identifiableObject != null) {
			object = identifiableObject.object;
		}
		logger.trace("out w/ object: {}", object);
		return object;
	}
}
