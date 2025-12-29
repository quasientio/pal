/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.runtime;

/**
 * Represents the execution phase within the PAL runtime.
 *
 * <p>Each phase is associated with a unique byte identifier. This enumeration defines the possible
 * phases of execution.
 */
public enum ExecPhase {

  /** Indicates the phase before execution starts. */
  BEFORE((byte) 1),

  /** Indicates the phase after execution has completed. */
  AFTER((byte) 2),

  /** Indicates an undefined execution phase. */
  UNDEFINED((byte) 3);

  /** The byte index associated with this execution phase. */
  private final byte idx;

  /**
   * Constructs an ExecPhase with the specified byte index.
   *
   * @param idx the byte value representing this execution phase
   */
  ExecPhase(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding ExecPhase.
   *
   * @param typeAsByte the byte value to convert
   * @return the corresponding ExecPhase
   * @throws IllegalArgumentException if the byte value does not correspond to any ExecPhase
   */
  public static ExecPhase fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> BEFORE;
      case 2 -> AFTER;
      case 3 -> UNDEFINED;
      default -> throw new IllegalArgumentException("Unknown ExecPhase type: " + typeAsByte);
    };
  }

  /**
   * Returns the byte value associated with this execution phase.
   *
   * @return the byte index of this ExecPhase
   */
  public byte toByte() {
    return idx;
  }
}
