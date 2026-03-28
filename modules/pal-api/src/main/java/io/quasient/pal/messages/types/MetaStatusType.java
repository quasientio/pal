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
 * Represents the status or response types for meta messages.
 *
 * <p>Each status type is associated with a unique byte identifier.
 */
public enum MetaStatusType {
  /** Indicates a successful operation. */
  OK((byte) 1),

  /** Indicates that an error has occurred. */
  ERROR((byte) 2),

  /** Indicates that the operation was unauthorized. */
  UNAUTHORIZED((byte) 3),

  /** Indicates that the requested operation is unsupported. */
  UNSUPPORTED((byte) 4);

  /** Lookup table for mapping byte identifiers to {@link MetaStatusType} instances. */
  private static final MetaStatusType[] LOOKUP = new MetaStatusType[256];

  static {
    for (MetaStatusType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  /** The byte identifier associated with this status type. */
  private final byte id;

  /**
   * Constructs a {@code MetaStatusType} with the specified byte identifier.
   *
   * @param id the byte identifier representing this status type
   */
  MetaStatusType(byte id) {
    this.id = id;
  }

  /**
   * Retrieves the byte identifier of this status type.
   *
   * @return the byte identifier associated with this status type
   */
  public byte getId() {
    return id;
  }

  /**
   * Returns the {@code MetaStatusType} corresponding to the specified byte identifier.
   *
   * @param id the byte identifier for which to retrieve the status type
   * @return the matching {@code MetaStatusType}, or {@code null} if no matching type is found
   */
  public static MetaStatusType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }
}
