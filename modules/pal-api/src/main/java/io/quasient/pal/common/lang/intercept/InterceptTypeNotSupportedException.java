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
 * Exception thrown when an operation is invoked that is not supported for the current {@link
 * InterceptType}.
 *
 * <p>Different intercept types support different operations on {@link InterceptContext}. For
 * example:
 *
 * <ul>
 *   <li>{@link InterceptType#BEFORE} intercepts cannot call {@code getReturnValue()} because the
 *       method has not yet executed
 *   <li>{@link InterceptType#AFTER} intercepts cannot call {@code proceed()} because the method has
 *       already executed
 *   <li>{@link InterceptType#AROUND} intercepts support both operations but in different phases
 * </ul>
 *
 * <p>This exception indicates a programming error where the callback code attempts an operation
 * that is fundamentally incompatible with the intercept type.
 *
 * @see InterceptApiMisuseException
 * @see InterceptPhaseViolationException
 * @see InterceptType
 */
public class InterceptTypeNotSupportedException extends InterceptApiMisuseException {

  /**
   * Constructs a new InterceptTypeNotSupportedException for the specified operation and intercept
   * type.
   *
   * <p>The exception message will be formatted as: "{operation} is not supported for {intercept
   * type} intercepts"
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type for which the operation is not supported
   */
  public InterceptTypeNotSupportedException(String operation, InterceptType interceptType) {
    super(
        String.format("%s is not supported for %s intercepts", operation, interceptType),
        operation,
        interceptType,
        null);
  }

  /**
   * Constructs a new InterceptTypeNotSupportedException with the specified operation, intercept
   * type, and underlying cause.
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type for which the operation is not supported
   * @param cause the underlying cause of this exception
   */
  public InterceptTypeNotSupportedException(
      String operation, InterceptType interceptType, Throwable cause) {
    super(
        String.format("%s is not supported for %s intercepts", operation, interceptType),
        operation,
        interceptType,
        null,
        cause);
  }
}
