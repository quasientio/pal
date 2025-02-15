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
 * Represents the various control status types used within the Pal runtime. Each status type is
 * associated with a unique byte identifier.
 */
public enum ControlStatusType {
  OK((byte) 1),
  ERROR((byte) 2),
  UNAUTHORIZED((byte) 3),
  UNSUPPORTED((byte) 4),
  NO_SUCH_SESSION((byte) 5),
  NO_SUCH_OBJECT((byte) 6);

  /** The byte identifier associated with the control status type. */
  private final byte id;

  /**
   * Constructs a ControlStatusType with the specified byte identifier.
   *
   * @param id the byte value representing the control status type
   */
  ControlStatusType(byte id) {
    this.id = id;
  }

  /**
   * Returns the ControlStatusType corresponding to the given byte identifier.
   *
   * @param messageTypeAsByte the byte value representing the control status type
   * @return the corresponding ControlStatusType
   * @throws IllegalArgumentException if the byte does not correspond to any ControlStatusType
   */
  public static ControlStatusType fromId(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> OK;
      case 2 -> ERROR;
      case 3 -> UNAUTHORIZED;
      case 4 -> UNSUPPORTED;
      case 5 -> NO_SUCH_SESSION;
      case 6 -> NO_SUCH_OBJECT;
      default ->
          throw new IllegalArgumentException("Unknown control status type: " + messageTypeAsByte);
    };
  }

  /**
   * Returns the byte identifier associated with this ControlStatusType.
   *
   * @return the byte value representing this control status type
   */
  public byte toId() {
    return id;
  }
}
