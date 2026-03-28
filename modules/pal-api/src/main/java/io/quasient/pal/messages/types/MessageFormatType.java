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
