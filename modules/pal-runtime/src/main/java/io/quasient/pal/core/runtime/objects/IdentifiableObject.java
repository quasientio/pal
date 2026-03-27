/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.objects;

import io.quasient.pal.common.objects.ObjectRef;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * A WeakReference that also remembers the {@link ObjectRef} key and the identity hash of its
 * referent, allowing O(1) removal from the store when the payload is garbage-collected.
 *
 * <p>Two instances are considered {@link #equals(Object) equal} if and only if they wrap the
 * <em>same</em> referent (identity comparison via {@code ==}). Once a referent has been cleared by
 * the GC, the instance is only equal to itself.
 */
final class IdentifiableObject extends WeakReference<Object> {

  /** Map key used for lookup in the primary ({@code byRef}) map. */
  private final ObjectRef key;

  /** Cached identity hash of the referent, used as the bucket key in the {@code byHash} map. */
  private final int hash;

  /**
   * Creates a new wrapper with an explicitly provided identity hash.
   *
   * <p>The caller supplies the hash so that the store's injectable hash function (which may differ
   * from {@link System#identityHashCode} in tests) is respected. The stored hash is returned by
   * {@link #getHash()} and used by cleanup code to locate the correct bucket.
   *
   * @param referent the object being identified; must not be {@code null}
   * @param key the object's {@link ObjectRef}; must not be {@code null}
   * @param queue reference queue in which to register the referent; must not be {@code null}
   * @param hash pre-computed identity hash for bucket lookup
   */
  IdentifiableObject(
      Object referent, ObjectRef key, ReferenceQueue<? super Object> queue, int hash) {
    super(referent, queue);
    Objects.requireNonNull(referent, "referent cannot be null");
    Objects.requireNonNull(key, "object ref cannot be null");
    Objects.requireNonNull(queue, "ref queue cannot be null");
    this.key = key;
    this.hash = hash;
  }

  /**
   * Creates a new wrapper using {@link System#identityHashCode} as the identity hash.
   *
   * @param referent the object being identified; must not be {@code null}
   * @param key the object's {@link ObjectRef}; must not be {@code null}
   * @param queue reference queue in which to register the referent; must not be {@code null}
   */
  IdentifiableObject(Object referent, ObjectRef key, ReferenceQueue<? super Object> queue) {
    this(referent, key, queue, System.identityHashCode(referent));
  }

  /**
   * Returns the {@link ObjectRef} map key.
   *
   * @return the key
   */
  ObjectRef getKey() {
    return key;
  }

  /**
   * Returns the cached identity hash used as the bucket key in the {@code byHash} map.
   *
   * @return the identity hash
   */
  int getHash() {
    return hash;
  }

  /**
   * Returns the identity hash of the referent. This value is stable even after the referent has
   * been garbage-collected.
   */
  @Override
  public int hashCode() {
    return hash;
  }

  /**
   * Two instances are equal if and only if they reference the <em>same</em> live object (identity
   * comparison). Once the referent is cleared by the GC, the instance is only equal to itself.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof IdentifiableObject idObj)) return false;
    Object thisRef = this.get();
    Object otherRef = idObj.get();
    if (thisRef != null && otherRef != null) return thisRef == otherRef;
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "IdentifiableObject{key=" + key + ", hash=" + hash + '}';
  }
}
