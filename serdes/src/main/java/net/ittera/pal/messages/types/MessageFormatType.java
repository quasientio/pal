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
