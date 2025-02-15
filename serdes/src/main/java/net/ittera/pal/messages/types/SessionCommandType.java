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
