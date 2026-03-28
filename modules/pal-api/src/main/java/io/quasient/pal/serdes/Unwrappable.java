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
