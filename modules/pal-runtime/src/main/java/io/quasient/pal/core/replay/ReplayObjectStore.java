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
package io.quasient.pal.core.replay;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bidirectional mapping between WAL object references (int) and live JVM objects created during
 * replay.
 *
 * <p>During deterministic replay, newly created objects must be tracked so that subsequent instance
 * method calls can resolve the correct live target from a WAL ref. This store maintains two maps:
 *
 * <ul>
 *   <li>A {@link ConcurrentHashMap} from WAL ref to live object (forward lookup, thread-safe for
 *       future multi-threaded phases).
 *   <li>An {@link IdentityHashMap} from live object to WAL ref (reverse lookup, identity-based so
 *       that two {@code equals()}-equivalent but distinct objects are tracked separately).
 * </ul>
 *
 * <p>The {@code register} method updates both maps atomically with respect to each other.
 */
public class ReplayObjectStore {

  /** Forward mapping: WAL object ref → live JVM object. Thread-safe for concurrent access. */
  private final Map<Integer, Object> walRefToLiveObject = new ConcurrentHashMap<>();

  /**
   * WAL refs that are registered as phantoms. A phantom ref represents an object whose constructor
   * was stubbed during replay — the object was never actually created, but its ref must be tracked
   * so that subsequent operations on it can be automatically stubbed (phantom cascading).
   *
   * <p>Thread-safe for concurrent access via {@link ConcurrentHashMap#newKeySet()}.
   */
  private final Set<Integer> phantomRefs = ConcurrentHashMap.newKeySet();

  /**
   * Reverse mapping: live JVM object → WAL object ref. Uses identity semantics (not {@code equals})
   * so that distinct objects with the same logical value are tracked independently.
   */
  private final IdentityHashMap<Object, Integer> liveObjectToWalRef = new IdentityHashMap<>();

  /**
   * Registers a bidirectional mapping between a WAL ref and a live object.
   *
   * <p>If the WAL ref was previously mapped to a different object, the old mapping is replaced in
   * both directions.
   *
   * @param walRef the WAL object reference (from the recorded {@code Obj.ref} field)
   * @param liveObject the live JVM object created during replay
   */
  public void register(int walRef, Object liveObject) {
    Object previous = walRefToLiveObject.put(walRef, liveObject);
    if (previous != null) {
      liveObjectToWalRef.remove(previous);
    }
    liveObjectToWalRef.put(liveObject, walRef);
    phantomRefs.remove(walRef);
  }

  /**
   * Resolves a WAL ref to its live object.
   *
   * @param walRef the WAL object reference to resolve
   * @return the live JVM object mapped to {@code walRef}
   * @throws IllegalArgumentException if no object is registered for the given ref
   */
  public Object resolve(int walRef) {
    Object obj = walRefToLiveObject.get(walRef);
    if (obj == null) {
      throw new IllegalArgumentException("No live object registered for WAL ref " + walRef);
    }
    return obj;
  }

  /**
   * Resolves a WAL ref to its live object, or {@code null} if not found.
   *
   * @param walRef the WAL object reference to resolve
   * @return the live JVM object mapped to {@code walRef}, or {@code null} if no mapping exists
   */
  public Object resolveOrNull(int walRef) {
    return walRefToLiveObject.get(walRef);
  }

  /**
   * Returns the WAL ref for a live object, using identity-based lookup.
   *
   * <p>Because this uses an {@link IdentityHashMap}, two objects that are {@code equals()} but not
   * {@code ==} will have different mappings.
   *
   * @param liveObject the live JVM object to look up
   * @return the WAL ref for the object, or {@code 0} if no mapping exists
   */
  public int getWalRef(Object liveObject) {
    Integer ref = liveObjectToWalRef.get(liveObject);
    return ref != null ? ref : 0;
  }

  /**
   * Registers a WAL ref as a phantom. A phantom represents an object whose constructor was stubbed
   * during replay — no live object exists, but the ref is tracked so that subsequent operations
   * targeting this ref can be automatically stubbed (phantom cascading).
   *
   * <p>If a live object is later registered for the same ref via {@link #register(int, Object)},
   * the phantom status is cleared.
   *
   * @param walRef the WAL object reference to mark as phantom
   */
  public void registerPhantom(int walRef) {
    phantomRefs.add(walRef);
  }

  /**
   * Returns whether the given WAL ref is registered as a phantom.
   *
   * @param walRef the WAL object reference to check
   * @return {@code true} if the ref was registered as a phantom and no live object has since
   *     overridden it, {@code false} otherwise
   */
  public boolean isPhantom(int walRef) {
    return phantomRefs.contains(walRef);
  }

  /**
   * Returns the number of registered WAL ref → live object mappings.
   *
   * @return the number of registered mappings
   */
  public int size() {
    return walRefToLiveObject.size();
  }
}
