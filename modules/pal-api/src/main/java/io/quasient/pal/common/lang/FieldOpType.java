/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
