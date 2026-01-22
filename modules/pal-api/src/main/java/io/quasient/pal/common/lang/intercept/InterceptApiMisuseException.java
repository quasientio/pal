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
 * Base unchecked exception for interception API misuse scenarios.
 *
 * <p>This exception (and its subclasses) are thrown when code using the interception API attempts
 * operations that violate the API contract. These exceptions indicate programming errors rather
 * than business logic exceptions.
 *
 * <p>Common scenarios include:
 *
 * <ul>
 *   <li>Calling operations that are not supported for the current {@link InterceptType} (e.g.,
 *       {@code getReturnValue()} in a {@link InterceptType#BEFORE} intercept)
 *   <li>Calling operations outside their valid {@link InterceptPhase} (e.g., {@code
 *       getReturnValue()} during the BEFORE phase of an AROUND intercept)
 *   <li>Other violations of the interception lifecycle contract
 * </ul>
 *
 * <p>This exception hierarchy distinguishes API misuse from business exceptions thrown by
 * callbacks, enabling proper exception propagation policies via {@link ExceptionPropagationPolicy}
 * and {@link CheckedExceptionPolicy}.
 *
 * @see InterceptTypeNotSupportedException
 * @see InterceptPhaseViolationException
 * @see InterceptCallback
 * @see InterceptContext
 */
public class InterceptApiMisuseException extends RuntimeException {

  /** The operation that was attempted (e.g., "getReturnValue()"). */
  private final String operation;

  /** The intercept type during which the violation occurred. */
  private final InterceptType interceptType;

  /** The intercept phase during which the violation occurred (may be null). */
  private final InterceptPhase interceptPhase;

  /**
   * Constructs a new InterceptApiMisuseException with the specified message and context.
   *
   * @param message the detail message explaining the API misuse
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type during which the violation occurred
   * @param interceptPhase the intercept phase during which the violation occurred (may be null)
   */
  public InterceptApiMisuseException(
      String message,
      String operation,
      InterceptType interceptType,
      InterceptPhase interceptPhase) {
    super(message);
    this.operation = operation;
    this.interceptType = interceptType;
    this.interceptPhase = interceptPhase;
  }

  /**
   * Constructs a new InterceptApiMisuseException with the specified message, context, and cause.
   *
   * @param message the detail message explaining the API misuse
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type during which the violation occurred
   * @param interceptPhase the intercept phase during which the violation occurred (may be null)
   * @param cause the underlying cause of this exception
   */
  public InterceptApiMisuseException(
      String message,
      String operation,
      InterceptType interceptType,
      InterceptPhase interceptPhase,
      Throwable cause) {
    super(message, cause);
    this.operation = operation;
    this.interceptType = interceptType;
    this.interceptPhase = interceptPhase;
  }

  /**
   * Returns the operation that was attempted when the API misuse occurred.
   *
   * @return the operation name (e.g., "getReturnValue()")
   */
  public String getOperation() {
    return operation;
  }

  /**
   * Returns the intercept type during which the API misuse occurred.
   *
   * @return the intercept type
   */
  public InterceptType getInterceptType() {
    return interceptType;
  }

  /**
   * Returns the intercept phase during which the API misuse occurred.
   *
   * @return the intercept phase, or null if not applicable
   */
  public InterceptPhase getInterceptPhase() {
    return interceptPhase;
  }
}
