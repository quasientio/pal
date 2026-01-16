/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes;

/**
 * Represents an entity that can be unwrapped to retrieve its underlying value, type, and reference.
 */
public interface Unwrappable {

  /**
   * Checks if the encapsulated value is null.
   *
   * @return {@code true} if the value is null; {@code false} otherwise.
   */
  boolean isNull();

  /**
   * Retrieves the encapsulated value as a string.
   *
   * @return the value associated with this instance, or {@code null} if no value is present.
   */
  String getValue();

  /**
   * Retrieves the type information of the encapsulated value.
   *
   * @return the type associated with this instance, or {@code null} if no type is specified.
   */
  String getType();

  /**
   * Retrieves the reference identifier associated with the encapsulated value.
   *
   * @return the reference identifier, or {@code null} if no reference is set.
   */
  Integer getRef();

  /**
   * Returns a string representation of this Unwrappable instance, including its null status, value,
   * type, and reference.
   *
   * @return a string representation of the Unwrappable.
   */
  default String asString() {
    return "Unwrappable{"
        + "isNull="
        + isNull()
        + ", value='"
        + getValue()
        + '\''
        + ", type='"
        + getType()
        + '\''
        + ", ref="
        + getRef()
        + '}';
  }
}
