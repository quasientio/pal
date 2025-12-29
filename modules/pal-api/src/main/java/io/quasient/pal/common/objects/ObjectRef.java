/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.objects;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a unique identifier reference within the PAL runtime. This class encapsulates an
 * integer-based reference and provides functionality to create, compare, and represent reference
 * values. Instances of ObjectRef are immutable and thread-safe.
 */
public final class ObjectRef {

  /** The unique reference identifier as an integer. */
  private final int ref;

  /**
   * Constructs an ObjectRef instance by parsing the provided string reference.
   *
   * @param ref the string representation of the reference identifier
   */
  private ObjectRef(String ref) {
    this(Integer.parseInt(ref));
  }

  /**
   * Constructs an ObjectRef instance with the specified integer reference.
   *
   * @param ref the integer value of the reference identifier
   */
  private ObjectRef(Integer ref) {
    this.ref = ref;
  }

  /**
   * Retrieves the integer value of this ObjectRef.
   *
   * @return the integer reference identifier
   */
  public int getRef() {
    return ref;
  }

  /**
   * Creates a new ObjectRef from the given string reference.
   *
   * @param ref the string representation of the reference identifier
   * @return a new ObjectRef instance corresponding to the provided string
   */
  public static ObjectRef from(String ref) {
    return new ObjectRef(ref);
  }

  /**
   * Creates a new ObjectRef from the given integer reference.
   *
   * @param ref the integer value of the reference identifier
   * @return a new ObjectRef instance corresponding to the provided integer
   */
  public static ObjectRef from(int ref) {
    return new ObjectRef(ref);
  }

  /**
   * Generates a new ObjectRef with a randomly generated reference identifier. The reference is a
   * positive integer between 0 and 99,999.
   *
   * <p><bold>NOTE</bold>: Asides from the range limitation, this method provides no uniqueness
   * guarantees.
   *
   * @return a new ObjectRef instance with a random reference
   */
  public static ObjectRef randomRef() {
    return new ObjectRef(String.valueOf(ThreadLocalRandom.current().nextInt(100000)));
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ObjectRef objectRef = (ObjectRef) o;
    return ref == objectRef.ref;
  }

  /**
   * Returns the string representation of the reference identifier.
   *
   * @return the string form of the reference identifier
   */
  public String asString() {
    return String.valueOf(ref);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(ref);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "objectRef: {" + ref + '}';
  }
}
