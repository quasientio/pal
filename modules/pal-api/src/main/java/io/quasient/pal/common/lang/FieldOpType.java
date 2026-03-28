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
package io.quasient.pal.common.lang;

/**
 * Types of field access operations captured by PAL.
 *
 * <p>When PAL intercepts field access, it records whether the operation was a read (GET) or write
 * (PUT). This enum is used in field access messages to distinguish between these operations.
 */
public enum FieldOpType {

  /** Read operation: retrieving the current value of a field. */
  GET((byte) 1),

  /** Write operation: assigning a new value to a field. */
  PUT((byte) 2);

  /** The byte value associated with the field operation type. */
  private final byte idx;

  /**
   * Constructs a FieldOpType with the specified byte value.
   *
   * @param idx the byte value representing the field operation type
   */
  FieldOpType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts the specified byte value to its corresponding FieldOpType enum constant.
   *
   * @param typeAsByte the byte value representing the field operation type
   * @return the corresponding FieldOpType enum constant
   * @throws IllegalArgumentException if the byte value does not correspond to any FieldOpType
   */
  public static FieldOpType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> GET;
      case 2 -> PUT;
      default -> throw new IllegalArgumentException("Unknown field operation type: " + typeAsByte);
    };
  }

  /**
   * Returns the byte value associated with this field operation type.
   *
   * @return the byte value representing this FieldOpType
   */
  public byte toByte() {
    return idx;
  }
}
