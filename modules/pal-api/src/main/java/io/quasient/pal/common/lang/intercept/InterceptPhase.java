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
package io.quasient.pal.common.lang.intercept;

/**
 * Represents the execution phase of an intercept callback within the PAL runtime.
 *
 * <p>For {@link InterceptType#AROUND} intercepts, callbacks are invoked twice: once in the {@link
 * #BEFORE} phase (where the callback can modify arguments and decide whether to proceed with
 * execution), and once in the {@link #AFTER} phase (where the callback can access and optionally
 * override the return value).
 *
 * <p>For {@link InterceptType#BEFORE} intercepts, only the {@link #BEFORE} phase applies.
 *
 * <p>For {@link InterceptType#AFTER} intercepts, only the {@link #AFTER} phase applies.
 *
 * @see InterceptType
 * @see InterceptCallback
 */
public enum InterceptPhase {

  /**
   * The BEFORE phase occurs before the intercepted method is executed.
   *
   * <p>During this phase, callbacks can:
   *
   * <ul>
   *   <li>Inspect method arguments
   *   <li>Modify method arguments (limited to simple types)
   *   <li>For AROUND intercepts: decide whether to proceed with method execution
   *   <li>Throw exceptions to prevent method execution
   * </ul>
   */
  BEFORE((byte) 1),

  /**
   * The AFTER phase occurs after the intercepted method has executed.
   *
   * <p>During this phase, callbacks can:
   *
   * <ul>
   *   <li>Access the return value or thrown exception
   *   <li>Override the return value
   *   <li>Replace a thrown exception with a different exception
   *   <li>Replace a thrown exception with a normal return value
   *   <li>Replace a normal return value with a thrown exception
   * </ul>
   */
  AFTER((byte) 2);

  /** The byte identifier associated with this intercept phase. */
  private final byte idx;

  /**
   * Constructs an {@code InterceptPhase} with the specified byte identifier.
   *
   * @param idx the byte value representing this intercept phase
   */
  InterceptPhase(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code InterceptPhase}.
   *
   * @param phaseAsByte the byte value representing an intercept phase
   * @return the {@code InterceptPhase} corresponding to the provided byte
   * @throws IllegalArgumentException if the byte value does not match any defined {@code
   *     InterceptPhase}
   * @see #toByte()
   */
  public static InterceptPhase fromByte(byte phaseAsByte) {
    return switch (phaseAsByte) {
      case 1 -> BEFORE;
      case 2 -> AFTER;
      default -> throw new IllegalArgumentException("Unknown intercept phase: " + phaseAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this {@code InterceptPhase}.
   *
   * @return the byte value representing this intercept phase
   * @see #fromByte(byte)
   */
  public byte toByte() {
    return idx;
  }
}
