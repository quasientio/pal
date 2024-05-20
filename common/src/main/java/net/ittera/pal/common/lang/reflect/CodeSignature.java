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

@SuppressWarnings("rawtypes")
public abstract class CodeSignature extends Signature {

  @Nonnull private final Class[] exceptionTypes;
  @Nonnull private final String[] parameterNames;
  @Nonnull private final Class[] parameterTypes;
  @Nonnull private final Parameter[] parameters;

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

  public Class[] getExceptionTypes() {
    return Arrays.copyOf(exceptionTypes, exceptionTypes.length);
  }

  public String[] getParameterNames() {
    return Arrays.copyOf(parameterNames, parameterNames.length);
  }

  public Class[] getParameterTypes() {
    return Arrays.copyOf(parameterTypes, parameterTypes.length);
  }

  public Parameter[] getParameters() {
    return Arrays.copyOf(parameters, parameters.length);
  }

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
