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
 * Enumerates the various types of session-related control commands within PAL.
 *
 * <p>Each command type is associated with a unique byte identifier.
 */
public enum SessionCommandType {

  /** Internal command to store an object reference within the session. */
  STORE_OBJECT((byte) 1),

  /** Command to delete a specific object reference from the session. */
  DELETE_OBJECT((byte) 2),

  /** Command to delete the entire session. */
  DELETE_SESSION((byte) 3),

  /** Internal command to clear all sessions. */
  CLEAR_SESSIONS((byte) 4);

  /** The unique byte identifier corresponding to the session command type. */
  private final byte idx;

  /**
   * Constructs a {@code SessionCommandType} with the specified byte identifier.
   *
   * @param idx the byte value representing this command type
   */
  SessionCommandType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code SessionCommandType}.
   *
   * @param messageTypeAsByte the byte value representing the command type
   * @return the {@code SessionCommandType} corresponding to the given byte
   * @throws IllegalArgumentException if the byte does not correspond to any known command type
   */
  public static SessionCommandType fromByte(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> STORE_OBJECT;
      case 2 -> DELETE_OBJECT;
      case 3 -> DELETE_SESSION;
      case 4 -> CLEAR_SESSIONS;
      default ->
          throw new IllegalArgumentException("Unknown session command type: " + messageTypeAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this session command type.
   *
   * @return the byte value representing this command type
   */
  public byte toByte() {
    return idx;
  }
}
