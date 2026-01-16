/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.types;

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
