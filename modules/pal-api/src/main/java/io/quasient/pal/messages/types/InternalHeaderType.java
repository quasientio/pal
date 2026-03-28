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

/** Enumerates the internal header types utilized by PAL for message dispatching and handling. */
public enum InternalHeaderType {
  /**
   * Indicates a write-ahead operation prior to message dispatch. The value represents the UUID of
   * the receiving-dispatching peer, not the producer.
   */
  WRITE_AHEAD((byte) 1);

  /** The byte value associated with this internal header type. */
  private final byte idx;

  /**
   * Constructs an InternalHeaderType with the specified byte value.
   *
   * @param idx the byte value representing the internal header type
   */
  InternalHeaderType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to the corresponding InternalHeaderType.
   *
   * @param typeAsByte the byte value representing the internal header type
   * @return the InternalHeaderType corresponding to the provided byte value
   * @throws IllegalArgumentException if the byte value does not correspond to any known header type
   */
  public static InternalHeaderType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> WRITE_AHEAD;
      default -> throw new IllegalArgumentException("Unknown internal header type: " + typeAsByte);
    };
  }

  /**
   * Retrieves the byte value associated with this internal header type.
   *
   * @return the byte value representing this internal header type
   */
  public byte toByte() {
    return idx;
  }
}
