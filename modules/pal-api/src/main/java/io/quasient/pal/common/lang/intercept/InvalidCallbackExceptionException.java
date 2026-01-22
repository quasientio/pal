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

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Thrown when a callback throws a checked exception that is not declared by the intercepted method.
 *
 * <p>This exception is thrown when the checked exception policy is set to REJECT and a callback
 * throws a checked exception that does not match the declared exceptions of the intercepted method.
 * This prevents callbacks from violating the method signature's exception contract.
 *
 * <p>Example scenario:
 *
 * <pre>{@code
 * // Method declares IOException
 * public void readFile(String path) throws IOException { ... }
 *
 * // Callback throws SQLException (not compatible with IOException)
 * public InterceptCallbackResponse callback(InterceptContext ctx) {
 *     throw new SQLException("Database error");  // Not allowed!
 * }
 * // This will result in InvalidCallbackExceptionException being thrown
 * }</pre>
 *
 * @see CheckedExceptionPolicy
 */
public class InvalidCallbackExceptionException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The array of declared exception types from the intercepted method. */
  private final Class<?>[] declaredExceptions;

  /**
   * Constructs a new InvalidCallbackExceptionException with the original exception and declared
   * exception types.
   *
   * @param cause the original checked exception thrown by the callback
   * @param declaredExceptions the array of exception types declared by the intercepted method
   */
  public InvalidCallbackExceptionException(Throwable cause, Class<?>[] declaredExceptions) {
    super(buildMessage(cause, declaredExceptions), cause);
    this.declaredExceptions =
        declaredExceptions != null ? declaredExceptions.clone() : new Class<?>[0];
  }

  /**
   * Returns the array of exception types declared by the intercepted method.
   *
   * @return a copy of the declared exception types array
   */
  public Class<?>[] getDeclaredExceptions() {
    return declaredExceptions.clone();
  }

  /**
   * Builds the exception message describing the mismatch between thrown and declared exceptions.
   *
   * @param cause the original exception thrown
   * @param declaredExceptions the declared exception types
   * @return a formatted error message
   */
  private static String buildMessage(Throwable cause, Class<?>[] declaredExceptions) {
    String thrownType = cause != null ? cause.getClass().getName() : "null";

    String declaredTypes;
    if (declaredExceptions == null || declaredExceptions.length == 0) {
      declaredTypes = "none";
    } else {
      declaredTypes =
          Arrays.stream(declaredExceptions).map(Class::getName).collect(Collectors.joining(", "));
    }

    return String.format(
        "Callback threw checked exception %s which is not compatible with declared exceptions: [%s]",
        thrownType, declaredTypes);
  }
}
