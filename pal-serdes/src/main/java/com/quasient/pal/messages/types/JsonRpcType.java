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

package com.quasient.pal.messages.types;

/**
 * Represents the type of JSON-RPC messages, specifying whether the message is a request or a
 * response.
 *
 * <p>Each type is associated with a unique byte identifier used for serialization.
 */
public enum JsonRpcType {

  /** Represents a JSON-RPC request message type. */
  REQUEST((byte) 1),

  /** Represents a JSON-RPC response message type. */
  RESPONSE((byte) 2);

  /** The numerical index associated with the JSON-RPC type. */
  private final byte idx;

  /**
   * Constructs a JsonRpcType with the specified index.
   *
   * @param idx the byte value representing the JSON-RPC type
   */
  JsonRpcType(byte idx) {
    this.idx = idx;
  }

  /**
   * Returns the corresponding JsonRpcType for the given byte value.
   *
   * @param typeAsByte the byte value representing the JSON-RPC type
   * @return the JsonRpcType corresponding to the provided byte value
   * @throws IllegalArgumentException if the byte value does not correspond to a known JsonRpcType
   */
  public static JsonRpcType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> REQUEST;
      case 2 -> RESPONSE;
      default -> throw new IllegalArgumentException("Unknown JSON-RPC type: " + typeAsByte);
    };
  }

  /**
   * Returns the byte value associated with this JsonRpcType.
   *
   * @return the byte value representing this JsonRpcType
   */
  public byte toByte() {
    return idx;
  }
}
