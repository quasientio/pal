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
 * Exception thrown when an operation is invoked during the wrong {@link InterceptPhase}.
 *
 * <p>This exception is primarily relevant for {@link InterceptType#AROUND} intercepts, which have
 * two distinct phases:
 *
 * <ul>
 *   <li>{@link InterceptPhase#BEFORE}: Occurs before the intercepted method executes. Only argument
 *       inspection/modification and {@code proceed()} are valid.
 *   <li>{@link InterceptPhase#AFTER}: Occurs after the intercepted method completes. Only result
 *       inspection/modification (via {@code getReturnValue()}) is valid; {@code proceed()} is not
 *       allowed.
 * </ul>
 *
 * <p>For example, calling {@code getReturnValue()} during the BEFORE phase of an AROUND intercept
 * will throw this exception, because the method has not yet executed and there is no return value
 * to retrieve.
 *
 * @see InterceptApiMisuseException
 * @see InterceptTypeNotSupportedException
 * @see InterceptPhase
 */
public class InterceptPhaseViolationException extends InterceptApiMisuseException {

  /** The phase during which the operation was attempted. */
  private final InterceptPhase currentPhase;

  /** The phase required for the operation to be valid. */
  private final InterceptPhase requiredPhase;

  /**
   * Constructs a new InterceptPhaseViolationException for the specified operation and phases.
   *
   * <p>The exception message will be formatted as: "{operation} cannot be called during
   * {currentPhase} phase; it requires {requiredPhase} phase"
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param currentPhase the phase during which the operation was attempted
   * @param requiredPhase the phase required for the operation to be valid
   */
  public InterceptPhaseViolationException(
      String operation, InterceptPhase currentPhase, InterceptPhase requiredPhase) {
    super(
        String.format(
            "%s cannot be called during %s phase; it requires %s phase",
            operation, currentPhase, requiredPhase),
        operation,
        null,
        currentPhase);
    this.currentPhase = currentPhase;
    this.requiredPhase = requiredPhase;
  }

  /**
   * Constructs a new InterceptPhaseViolationException with the specified operation, phases, and
   * underlying cause.
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param currentPhase the phase during which the operation was attempted
   * @param requiredPhase the phase required for the operation to be valid
   * @param cause the underlying cause of this exception
   */
  public InterceptPhaseViolationException(
      String operation,
      InterceptPhase currentPhase,
      InterceptPhase requiredPhase,
      Throwable cause) {
    super(
        String.format(
            "%s cannot be called during %s phase; it requires %s phase",
            operation, currentPhase, requiredPhase),
        operation,
        null,
        currentPhase,
        cause);
    this.currentPhase = currentPhase;
    this.requiredPhase = requiredPhase;
  }

  /**
   * Returns the phase during which the invalid operation was attempted.
   *
   * @return the current phase
   */
  public InterceptPhase getCurrentPhase() {
    return currentPhase;
  }

  /**
   * Returns the phase required for the operation to be valid.
   *
   * @return the required phase
   */
  public InterceptPhase getRequiredPhase() {
    return requiredPhase;
  }
}
