/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an interceptable method call within the PAL runtime.
 *
 * <p>This class encapsulates the information about a method invocation that can be intercepted,
 * including the method's name and its parameter types.
 *
 * @see Interceptable
 */
public final class InterceptableMethodCall extends Interceptable {

  /**
   * Holds the list of parameter type names for the method being intercepted.
   *
   * <p>This field is immutable and stores the types of parameters in the order they appear in the
   * method signature. It is used to uniquely identify the method call for interception purposes.
   */
  private final List<String> parameterTypes;

  /**
   * Separator used to concatenate field values in the serialized string representation.
   *
   * <p>This constant is used to delimit the method name and its parameter types when serializing
   * the method call.
   */
  private static final String FIELD_SEP = "&&";

  /**
   * Constructs a new {@code InterceptableMethodCall} instance with the specified name and parameter
   * types.
   *
   * <p>This constructor initializes the interceptable method call with the given method name and
   * its parameter types. If the provided {@code parameterTypes} is {@code null}, it defaults to an
   * empty list.
   *
   * @param name the name of the method to be intercepted; must not be {@code null}
   * @param parameterTypes the list of parameter type names; may be {@code null}, in which case an
   *     empty list is used
   * @throws NullPointerException if {@code name} is {@code null}
   */
  public InterceptableMethodCall(String name, List<String> parameterTypes) {
    super(name, InterceptableType.METHOD_CALL);
    this.parameterTypes = Objects.requireNonNullElse(parameterTypes, Collections.emptyList());
  }

  /**
   * Retrieves the list of parameter type names for the intercepted method call.
   *
   * <p>This method returns a list representing the types of parameters that the method accepts, in
   * the order they are declared in the method signature.
   *
   * @return the list of parameter type names
   */
  public List<String> getParameterTypes() {
    return parameterTypes;
  }

  /**
   * {@inheritDoc}
   *
   * @param o the object to compare with
   * @return {@code true} if the specified object is equal to this method call; {@code false}
   *     otherwise
   */
  @Override
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
    InterceptableMethodCall that = (InterceptableMethodCall) o;
    return Objects.equals(parameterTypes, that.parameterTypes);
  }

  /**
   * {@inheritDoc}
   *
   * @return the hash code value for this method call
   */
  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType(), parameterTypes);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a string representation of the method call, including its name and parameter types.
   * The format is {@code "InterceptableMethodCall{parameterTypes=[...], name='...'}"}.
   *
   * @return a string representation of this method call
   */
  @Override
  public String toString() {
    return "InterceptableMethodCall{"
        + "parameterTypes="
        + parameterTypes
        + ", name='"
        + getName()
        + '\''
        + '}';
  }

  /**
   * {@inheritDoc}
   *
   * <p>Serializes the method call into a string using the format {@code
   * "methodName&&paramType1&&paramType2&&..."}. This representation is used for storing or
   * transmitting the method call data.
   *
   * @return the serialized string representation of this method call
   */
  @Override
  public String toSerializedString() {
    return format("%s" + FIELD_SEP + "%s", getName(), String.join(FIELD_SEP, parameterTypes));
  }

  /**
   * Deserializes a string to create an {@code InterceptableMethodCall} instance.
   *
   * <p>This method parses the serialized string using the predefined separator to extract the
   * method name and its parameter types. The expected format is {@code
   * "methodName&&paramType1&&paramType2&&..."}.
   *
   * @param serialized the serialized string representing a method call; must not be {@code null}
   * @return a new {@code InterceptableMethodCall} instance constructed from the serialized data
   * @throws NullPointerException if {@code serialized} is {@code null}
   */
  public static InterceptableMethodCall fromSerializedString(String serialized) {
    final String[] parts = serialized.split(FIELD_SEP);
    final String name = parts[0];
    final List<String> paramTypes = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length));
    return new InterceptableMethodCall(name, paramTypes);
  }
}
