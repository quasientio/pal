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

public final class Params {

  @Nonnull private final String[] parameterNames;
  @Nonnull private final Class<?>[] parameterTypes;
  @Nonnull private final Parameter[] parameters;

  public Params(
      String[] parameterNames,
      @Nonnull Class<?>[] parameterTypes,
      @Nonnull Parameter[] parameters) {
    this.parameterTypes = Objects.requireNonNull(parameterTypes);
    this.parameters = Objects.requireNonNull(parameters);
    if (parameterTypes.length != parameters.length) {
      throw new IllegalArgumentException(
          "Length of arrays 'parameters' and 'parameterTypes' is different.");
    }
    this.parameterNames =
        Objects.requireNonNullElseGet(
            parameterNames,
            () -> Arrays.stream(parameters).map(Parameter::getName).toArray(String[]::new));
  }

  public String[] getParameterNames() {
    return parameterNames;
  }

  @Nonnull
  public Class<?>[] getParameterTypes() {
    return parameterTypes;
  }

  @Nonnull
  public Parameter[] getParameters() {
    return parameters;
  }

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

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(parameterNames);
    result = 31 * result + Arrays.hashCode(parameterTypes);
    result = 31 * result + Arrays.hashCode(parameters);
    return result;
  }
}
