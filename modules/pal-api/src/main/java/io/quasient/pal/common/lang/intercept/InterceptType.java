/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

/**
 * Represents the types of interception join points available within the PAL runtime.
 *
 * <p>Each {@code InterceptType} defines a specific phase in the interception process, determining
 * when an interceptor should be invoked relative to the target method's execution.
 */
public enum InterceptType {

  /** Interception that occurs before the target method is executed. */
  BEFORE((byte) 1),

  /** Interception that occurs after the target method has executed. */
  AFTER((byte) 2),

  /**
   * Interception that wraps around the target method execution, allowing for behavior <em>instead
   * of</em> the method invocation.
   */
  AROUND((byte) 3),

  /**
   * Asynchronous interception that occurs before the target method is executed. Intended for use in
   * non-blocking execution contexts.
   */
  BEFORE_ASYNC((byte) 4),

  /**
   * Asynchronous interception that occurs after the target method has successfully executed.
   * Suitable for scenarios requiring non-blocking post-execution actions.
   */
  AFTER_ASYNC((byte) 5);

  /** The byte identifier associated with this interception type. */
  private final byte idx;

  /**
   * Constructs an {@code InterceptType} with the specified byte identifier.
   *
   * @param idx the byte value representing this interception type
   */
  InterceptType(byte idx) {
    this.idx = idx;
  }

  /**
   * Converts a byte value to its corresponding {@code InterceptType}.
   *
   * @param typeAsByte the byte value representing an interception type
   * @return the {@code InterceptType} corresponding to the provided byte
   * @throws IllegalArgumentException if the byte value does not match any defined {@code
   *     InterceptType}
   * @see #toByte()
   */
  public static InterceptType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> BEFORE;
      case 2 -> AFTER;
      case 3 -> AROUND;
      case 4 -> BEFORE_ASYNC;
      case 5 -> AFTER_ASYNC;
      default -> throw new IllegalArgumentException("Unknown intercept type: " + typeAsByte);
    };
  }

  /**
   * Retrieves the byte identifier associated with this {@code InterceptType}.
   *
   * @return the byte value representing this interception type
   * @see #fromByte(byte)
   */
  public byte toByte() {
    return idx;
  }
}
