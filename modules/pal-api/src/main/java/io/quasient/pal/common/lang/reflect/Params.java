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
package io.quasient.pal.common.lang.reflect;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Encapsulates information about method parameters, including their names, types, and reflective
 * {@link Parameter} instances.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "Internal DTO - intentionally exposes arrays for performance")
public final class Params {

  /**
   * The names of the parameters. If not provided during construction, names are derived from the
   * {@link Parameter} instances.
   */
  @Nonnull private final String[] parameterNames;

  /** The types of the parameters, corresponding to each parameter. */
  @Nonnull private final Class<?>[] parameterTypes;

  /** The reflective {@link Parameter} instances representing each parameter. */
  @Nonnull private final Parameter[] parameters;

  /**
   * Constructs a {@code Params} instance with the specified parameter names, types, and {@link
   * Parameter} objects.
   *
   * @param parameterNames an array of parameter names; may be {@code null}, in which case names are
   *     extracted from {@code parameters}
   * @param parameterTypes an array of {@code Class} objects representing parameter types; must not
   *     be {@code null}
   * @param parameters an array of {@link Parameter} objects; must not be {@code null}
   * @throws NullPointerException if {@code parameterTypes} or {@code parameters} is {@code null}
   * @throws IllegalArgumentException if the lengths of {@code parameterTypes} and {@code
   *     parameters} differ
   */
  public Params(
      String[] parameterNames,
      @Nonnull Class<?>[] parameterTypes,
      @Nonnull Parameter[] parameters) {
    this.parameterTypes = Objects.requireNonNull(parameterTypes, "parameterTypes cannot be null");
    this.parameters = Objects.requireNonNull(parameters, "parameters cannot be null");
    if (parameterTypes.length != parameters.length) {
      throw new IllegalArgumentException(
          "Length of arrays 'parameters' and 'parameterTypes' is different.");
    }
    this.parameterNames =
        Objects.requireNonNullElseGet(
            parameterNames,
            () -> Arrays.stream(parameters).map(Parameter::getName).toArray(String[]::new));
  }

  /**
   * Returns the names of the parameters.
   *
   * @return an array of parameter names
   */
  public String[] getParameterNames() {
    return parameterNames;
  }

  /**
   * Returns the types of the parameters.
   *
   * @return an array of {@code Class} objects representing parameter types
   */
  @Nonnull
  public Class<?>[] getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Returns the reflective {@link Parameter} instances of the parameters.
   *
   * @return an array of {@link Parameter} objects
   */
  @Nonnull
  public Parameter[] getParameters() {
    return parameters;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Params params = (Params) o;
    return Arrays.equals(parameterNames, params.parameterNames)
        && Arrays.equals(parameterTypes, params.parameterTypes)
        && Arrays.equals(parameters, params.parameters);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = Arrays.hashCode(parameterNames);
    result = 31 * result + Arrays.hashCode(parameterTypes);
    result = 31 * result + Arrays.hashCode(parameters);
    return result;
  }
}
