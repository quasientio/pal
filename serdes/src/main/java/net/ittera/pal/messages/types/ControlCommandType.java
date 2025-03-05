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

import java.util.Locale;

/**
 * Enumerates the types of control commands that can be processed within the Pal runtime. Each
 * command type is associated with a unique identifier used for serialization and communication.
 */
public enum ControlCommandType {

  /** Represents a command to delete a specific object reference from a session. */
  DELETE_OBJECT((byte) 1),

  /**
   * Represents a command to delete an existing session, with all its associated object references.
   */
  DELETE_SESSION((byte) 2),

  /** Represents a command to initiate garbage collection. */
  GC((byte) 3),

  /** Represents a PING command, which will be responded with status OK. */
  PING((byte) 4);

  /** The unique identifier associated with the control command type. */
  private final byte id;

  /**
   * Constructs a ControlCommandType with the specified identifier.
   *
   * @param id the byte value representing the control command type
   */
  ControlCommandType(byte id) {
    this.id = id;
  }

  /**
   * Retrieves the ControlCommandType corresponding to the given identifier.
   *
   * @param messageTypeAsByte the byte identifier of the control command type
   * @return the matching ControlCommandType
   * @throws IllegalArgumentException if no matching ControlCommandType is found
   */
  public static ControlCommandType fromId(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> DELETE_OBJECT;
      case 2 -> DELETE_SESSION;
      case 3 -> GC;
      case 4 -> PING;
      default ->
          throw new IllegalArgumentException("Unknown control command type: " + messageTypeAsByte);
    };
  }

  /**
   * Returns the unique identifier of this control command type.
   *
   * @return the byte identifier of the control command type
   */
  public byte getId() {
    return id;
  }

  /**
   * Provides the JSON representation of this control command type.
   *
   * @return the lowercase name of the control command type suitable for JSON
   */
  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * Retrieves the ControlCommandType corresponding to the given JSON name.
   *
   * @param jsonName the JSON string representation of the control command type
   * @return the matching ControlCommandType, or {@code null} if no match is found
   */
  public static ControlCommandType fromJsonName(String jsonName) {
    for (ControlCommandType type : values()) {
      if (type.getJsonName().equalsIgnoreCase(jsonName)) {
        return type;
      }
    }
    return null;
  }
}
