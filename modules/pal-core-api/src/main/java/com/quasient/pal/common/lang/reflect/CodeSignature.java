/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.reflect;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents the signature of a code element, such as a method or constructor, encapsulating
 * information about its parameters and declared exception types.
 */
@SuppressWarnings("rawtypes")
public abstract class CodeSignature extends Signature {

  /** The types of exceptions that this code element declares to throw. */
  @Nonnull private final Class[] exceptionTypes;

  /** The names of the parameters of this code element. */
  @Nonnull private final String[] parameterNames;

  /** The types of the parameters of this code element. */
  @Nonnull private final Class[] parameterTypes;

  /** The Parameter objects representing the parameters of this code element. */
  @Nonnull private final Parameter[] parameters;

  /**
   * Constructs a new CodeSignature with the specified details.
   *
   * @param declaringType the class that declares this code element
   * @param declaringTypeName the fully qualified name of the declaring class
   * @param modifiers the Java language modifiers for this code element
   * @param name the name of this code element
   * @param exceptionTypes the types of exceptions that this code element declares to throw
   * @param params the parameters of this code element, including types and names
   */
  CodeSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      @Nonnull Class[] exceptionTypes,
      @Nonnull Params params) {
    super(declaringType, declaringTypeName, modifiers, name);
    this.exceptionTypes =
        Arrays.copyOf(Objects.requireNonNull(exceptionTypes), exceptionTypes.length);
    this.parameterTypes =
        Arrays.copyOf(
            Objects.requireNonNull(params.getParameterTypes()), params.getParameterTypes().length);
    this.parameterNames =
        Arrays.copyOf(
            Objects.requireNonNull(params.getParameterNames()), params.getParameterNames().length);
    this.parameters =
        Arrays.copyOf(
            Objects.requireNonNull(params.getParameters()), params.getParameters().length);
  }

  /**
   * Returns an array of exception types that this code element declares to throw.
   *
   * @return a copy of the array of exception types
   */
  public Class[] getExceptionTypes() {
    return Arrays.copyOf(exceptionTypes, exceptionTypes.length);
  }

  /**
   * Returns an array of parameter names of this code element.
   *
   * @return a copy of the array of parameter names
   */
  public String[] getParameterNames() {
    return Arrays.copyOf(parameterNames, parameterNames.length);
  }

  /**
   * Returns an array of parameter types of this code element.
   *
   * @return a copy of the array of parameter types
   */
  public Class[] getParameterTypes() {
    return Arrays.copyOf(parameterTypes, parameterTypes.length);
  }

  /**
   * Returns an array of Parameter objects representing the parameters of this code element.
   *
   * @return a copy of the array of Parameter objects
   */
  public Parameter[] getParameters() {
    return Arrays.copyOf(parameters, parameters.length);
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    CodeSignature that = (CodeSignature) o;
    return Arrays.equals(exceptionTypes, that.exceptionTypes)
        && Arrays.equals(parameterNames, that.parameterNames)
        && Arrays.equals(parameterTypes, that.parameterTypes)
        && Arrays.equals(parameters, that.parameters);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Arrays.hashCode(exceptionTypes);
    result = 31 * result + Arrays.hashCode(parameterNames);
    result = 31 * result + Arrays.hashCode(parameterTypes);
    result = 31 * result + Arrays.hashCode(parameters);
    return result;
  }
}
