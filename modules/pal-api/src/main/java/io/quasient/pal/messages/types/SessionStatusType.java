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
 * Represents the various status/response types for a session command within the PAL runtime.
 *
 * <p>Each status type is associated with a unique byte identifier used for serialization.
 */
public enum SessionStatusType {

  /** Indicates that the session command was executed without any issues. */
  OK((byte) 1),

  /** Signifies that an error has occurred when executing the session command. */
  ERROR((byte) 2),

  /** Indicates that the session command is not supported. */
  UNSUPPORTED_SESSION_CMD((byte) 3),

  /** Denotes that the specified session does not exist. */
  NO_SUCH_SESSION((byte) 4),

  /** Specifies that the referenced object within the session does not exist. */
  NO_SUCH_OBJECT((byte) 5);

  /** The byte identifier associated with this session status type. */
  private final byte idx;

  /**
   * Constructs a {@code SessionStatusType} with the specified byte identifier.
   *
   * @param idx the byte value representing this status type
   */
  SessionStatusType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts the given byte value to its corresponding {@code SessionStatusType}.
   *
   * @param typeAsByte the byte value representing a session status type
   * @return the matching {@code SessionStatusType}
   * @throws IllegalArgumentException if the byte value does not correspond to any known status type
   */
  public static SessionStatusType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> OK;
      case 2 -> ERROR;
      case 3 -> UNSUPPORTED_SESSION_CMD;
      case 4 -> NO_SUCH_SESSION;
      case 5 -> NO_SUCH_OBJECT;
      default -> throw new IllegalArgumentException("Unknown session status type: " + typeAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this session status type.
   *
   * @return the byte value representing this status type
   */
  public byte toByte() {
    return idx;
  }
}
