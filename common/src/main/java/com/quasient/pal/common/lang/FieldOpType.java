/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.common.lang;

/** Represents the types of operations that can be performed on a field. */
public enum FieldOpType {

  /** Represents a get field operation. */
  GET((byte) 1),

  /** Represents a put field operation. */
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
