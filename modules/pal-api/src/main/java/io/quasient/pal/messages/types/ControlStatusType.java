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
 * Represents the various control status types used within the Pal runtime. Each status type is
 * associated with a unique byte identifier.
 */
public enum ControlStatusType {
  /** Informs the peer that the command was invoked successfully. */
  OK((byte) 1),

  /** Informs the peer that the command invocation caused an error. */
  ERROR((byte) 2),

  /** Informs the peer that it is unauthorized to invoke such command. */
  UNAUTHORIZED((byte) 3),

  /** Informs the peer that the command is unsupported. */
  UNSUPPORTED((byte) 4),

  /** Informs the peer that no such session exists. */
  NO_SUCH_SESSION((byte) 5),

  /** Informs the peer that no such object exists. */
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
