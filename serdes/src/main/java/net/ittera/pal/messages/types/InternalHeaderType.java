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

package net.ittera.pal.messages.types;

/** Enumerates the internal header types utilized by PAL for message dispatching and handling. */
public enum InternalHeaderType {
  /**
   * Indicates a write-ahead operation prior to message dispatch. The value represents the UUID of
   * the receiving-dispatching peer, not the producer.
   */
  WRITE_AHEAD((byte) 1);

  /** The byte value associated with this internal header type. */
  private final byte idx;

  /**
   * Constructs an InternalHeaderType with the specified byte value.
   *
   * @param idx the byte value representing the internal header type
   */
  InternalHeaderType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to the corresponding InternalHeaderType.
   *
   * @param typeAsByte the byte value representing the internal header type
   * @return the InternalHeaderType corresponding to the provided byte value
   * @throws IllegalArgumentException if the byte value does not correspond to any known header type
   */
  public static InternalHeaderType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> WRITE_AHEAD;
      default -> throw new IllegalArgumentException("Unknown internal header type: " + typeAsByte);
    };
  }

  /**
   * Retrieves the byte value associated with this internal header type.
   *
   * @return the byte value representing this internal header type
   */
  public byte toByte() {
    return idx;
  }
}
