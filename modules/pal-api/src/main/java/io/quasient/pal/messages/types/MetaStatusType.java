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
