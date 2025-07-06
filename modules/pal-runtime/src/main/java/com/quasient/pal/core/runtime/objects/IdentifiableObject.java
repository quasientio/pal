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

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * A wrapper for objects that provides identity-based hash code and maintains a weak reference,
 * facilitating storage and lookup operations within a map.
 */
class IdentifiableObject {

  /**
   * Holds a weak reference to the encapsulated object, allowing it to be garbage collected when no
   * longer in use.
   */
  private final WeakReference<Object> object;

  /**
   * Stores the identity-based hash code of the encapsulated object, ensuring consistent behavior in
   * hash-based collections.
   */
  private final int hash;

  /**
   * Constructs an IdentifiableObject by encapsulating the provided object.
   *
   * @param object the object to be wrapped; must not be null
   * @throws NullPointerException if the provided object is null
   */
  IdentifiableObject(Object object) {
    this.object = new WeakReference<>(Objects.requireNonNull(object));
    this.hash = System.identityHashCode(object);
  }

  /**
   * Retrieves the weak reference to the encapsulated object.
   *
   * @return a WeakReference containing the encapsulated object
   */
  public WeakReference<Object> getObject() {
    return object;
  }

  /**
   * Returns the identity-based hash code of the encapsulated object.
   *
   * @return the hash code corresponding to the object's identity
   */
  public int getHash() {
    return hash;
  }

  /** {@inheritDoc} */
  @Override
  public final int hashCode() {
    return hash;
  }

  /**
   * {@inheritDoc}
   *
   * <p>IdentifiableObject's equality is based on the identityHashCode of the encapsulated object.
   */
  @Override
  @SuppressWarnings("EqualsUsingHashCode")
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof IdentifiableObject)) {
      return false;
    }
    return other.hashCode() == this.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "IdentifiableObject{" + "object=" + object.get() + ", hash=" + hash + '}';
  }
}
