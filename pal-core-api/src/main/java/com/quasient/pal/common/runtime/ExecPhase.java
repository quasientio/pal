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

package com.quasient.pal.common.runtime;

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
