/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;

/**
 * Thrown to indicate that a reflective call to a method or constructor is ambiguous because
 * multiple candidates match the provided parameters. This exception encapsulates details about the
 * target class, the identifier of the call (method name or "new" for constructor calls), the list
 * of parameter types used in the call, and the executable candidates that contributed to the
 * ambiguity.
 */
public class AmbiguousCallException extends Exception {

  /** The name of the target class where the ambiguous call was attempted. */
  private final String className;

  /** The name of the called method, or "new" when a constructor is invoked. */
  private final String methodName;

  /**
   * The list of parameter types provided for the call. These types are used to determine matching
   * executable candidates.
   */
  private final List<Class<?>> parameterTypesToMatch;

  /**
   * The list of executable members (methods or constructors) that match the provided parameter
   * types, leading to the ambiguity.
   */
  private final List<? extends Executable> matchingExecutables;

  /**
   * Constructs an AmbiguousCallException for a method call when multiple executable methods match
   * the provided parameter types.
   *
   * @param className the name of the class on which the method was invoked
   * @param methodName the name of the method that was called
   * @param parameterTypesToMatch the list of parameter types used in the call; must not be null
   * @param matchingExecutables the list of method executables that match the given parameters,
   *     causing ambiguity
   */
  public AmbiguousCallException(
      String className,
      String methodName,
      List<Class<?>> parameterTypesToMatch,
      List<? extends Executable> matchingExecutables) {
    this.className = className;
    this.methodName = methodName;
    this.parameterTypesToMatch = parameterTypesToMatch;
    this.matchingExecutables = matchingExecutables;
  }

  /**
   * Constructs an AmbiguousCallException for a constructor call when multiple constructors match
   * the provided parameter types.
   *
   * <p>This constructor automatically sets the method identifier to "new" to indicate object
   * instantiation.
   *
   * @param className the name of the class whose constructor was invoked
   * @param parameterTypesToMatch the list of parameter types used in the constructor call; must not
   *     be null
   * @param matchingExecutables the list of constructor executables that match the given parameters,
   *     resulting in ambiguity
   */
  public AmbiguousCallException(
      String className,
      List<Class<?>> parameterTypesToMatch,
      List<? extends Executable> matchingExecutables) {
    this(className, "new", parameterTypesToMatch, matchingExecutables);
  }

  /**
   * Returns the exception message describing the ambiguous call. This method delegates to {@link
   * #getMessage()}.
   *
   * @return a string representation of the exception message
   */
  @Override
  public String toString() {
    return getMessage();
  }

  /**
   * Returns the list of executable members (methods or constructors) that were found to match the
   * provided parameters and caused the ambiguity.
   *
   * @return a list of matching executable members
   */
  @SuppressWarnings("unused")
  public List<? extends Executable> getMatchingExecutables() {
    return matchingExecutables;
  }

  /**
   * Constructs and returns a detailed message describing the ambiguous call. The message includes
   * the target class, the identifier of the method or constructor, a list of candidate signatures,
   * and the parameter types supplied for the call.
   *
   * @return the detailed exception message
   */
  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Ambiguous call: multiple matches found for \"%s.%s\":%n", className, methodName));
    for (Executable executable : matchingExecutables) {
      Class<?>[] parameterTypes = executable.getParameterTypes();
      sb.append("  ");
      sb.append(methodName);
      sb.append("(");
      sb.append(
          String.join(
              ", ", Arrays.stream(parameterTypes).map(Class::getName).toArray(String[]::new)));
      sb.append(")%n");
    }
    sb.append("which can be assigned the given types: ");
    sb.append(parameterTypesToMatch);
    return String.format(sb.toString());
  }
}
