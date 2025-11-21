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
 * Enumerates the supported message format types within the PAL runtime.
 *
 * <p>The currently supported formats are: JSON and Binary (using colfer-serialization)
 *
 * <p>Each format type is associated with a unique byte identifier used for serialization and
 * deserialization of messages.
 */
public enum MessageFormatType {
  /** Represents the JSON message format. */
  JSON((byte) 1),

  /** Represents the binary message format. */
  BINARY((byte) 2);

  /** The byte identifier associated with the message format type. */
  private final byte idx;

  /**
   * Constructs a {@code MessageFormatType} with the specified byte identifier.
   *
   * @param idx the byte value representing the message format type
   */
  MessageFormatType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code MessageFormatType}.
   *
   * @param formatAsByte the byte value representing the message format
   * @return the corresponding {@code MessageFormatType}
   * @throws IllegalArgumentException if the byte value does not match any defined format type
   */
  public static MessageFormatType fromByte(byte formatAsByte) {
    return switch (formatAsByte) {
      case 1 -> JSON;
      case 2 -> BINARY;
      default -> throw new IllegalArgumentException("Unknown message format: " + formatAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this message format type.
   *
   * @return the byte value representing this message format type
   */
  public byte toByte() {
    return idx;
  }
}
