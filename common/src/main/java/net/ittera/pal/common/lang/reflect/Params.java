/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.lang.reflect;

import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Encapsulates information about method parameters, including their names, types, and reflective
 * {@link Parameter} instances.
 */
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
