/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages.types;

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
