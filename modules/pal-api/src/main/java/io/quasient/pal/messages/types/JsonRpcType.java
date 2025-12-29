/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.types;

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
