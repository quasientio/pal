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
package io.quasient.pal.messages.types;

/**
 * Represents the different types of Remote Procedure Calls (RPC) supported by PAL.
 *
 * <p>Each RPC type is associated with a unique byte identifier used for serialization.
 */
public enum RpcType {

  /** Represents the binary ZMQ RPC type with byte identifier 1. */
  ZMQ_RPC((byte) 1),

  /** Represents the JSON RPC type with byte identifier 2. */
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
      case 1 -> ZMQ_RPC;
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
