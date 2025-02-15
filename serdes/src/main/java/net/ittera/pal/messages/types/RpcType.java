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

/**
 * Represents the different types of Remote Procedure Calls (RPC) supported by PAL.
 *
 * <p>Each RPC type is associated with a unique byte identifier used for serialization.
 */
public enum RpcType {

  /** Represents a binary RPC type with byte identifier 1. */
  BIN_RPC((byte) 1),

  /** Represents a JSON RPC type with byte identifier 2. */
  JSON_RPC((byte) 2);

  /** The byte identifier associated with the RPC type. */
  private final byte idx;

  /**
   * Constructs an RpcType with the specified byte identifier.
   *
   * @param idx the byte value representing the RPC type
   */
  RpcType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding RpcType.
   *
   * @param typeAsByte the byte value representing the desired RpcType
   * @return the RpcType corresponding to the provided byte value
   * @throws IllegalArgumentException if the byte value does not correspond to any RpcType
   */
  public static RpcType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> BIN_RPC;
      case 2 -> JSON_RPC;
      default -> throw new IllegalArgumentException("Unknown RPC type: " + typeAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this RpcType.
   *
   * @return the byte value representing the RpcType
   */
  public byte toByte() {
    return idx;
  }
}
