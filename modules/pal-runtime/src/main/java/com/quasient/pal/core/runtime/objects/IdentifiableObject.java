/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.objects;

import com.quasient.pal.common.objects.ObjectRef;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * A WeakReference that also remembers the {@link ObjectRef} key and the identity hash of its
 * referent, allowing O(1) removal from the store when the payload is garbage-collected.
 */
final class IdentifiableObject extends WeakReference<Object> {

  /** Map key */
  private final ObjectRef key;

  /** Cached identity hash of referent */
  private final int hash;

  /**
   * Creates a new wrapper to identify an object within the system.
   *
   * @param referent the object being identified
   * @param key the object's {@link ObjectRef}
   * @param queue reference queue in which to register the referent
   */
  IdentifiableObject(Object referent, ObjectRef key, ReferenceQueue<? super Object> queue) {
    super(referent, queue);
    Objects.requireNonNull(referent, "referent cannot be null");
    Objects.requireNonNull(referent, "object ref cannot be null");
    Objects.requireNonNull(referent, "ref queue cannot be null");
    this.key = key;
    this.hash = System.identityHashCode(referent);
  }

  /**
   * Plain getter for key
   *
   * @return the key
   */
  ObjectRef getKey() {
    return key;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return hash;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object other) {
    return (other instanceof IdentifiableObject idObj) && this.hash == idObj.hash;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "IdentifiableObject{key=" + key + ", hash=" + hash + '}';
  }
}
