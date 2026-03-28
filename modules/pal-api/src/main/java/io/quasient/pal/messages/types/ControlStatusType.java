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
