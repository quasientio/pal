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
import java.util.stream.IntStream;

public abstract class CodeSignature extends Signature {

  private final Class[] exceptionTypes;
  private final String[] parameterNames;
  private final Class[] parameterTypes;
  private final Parameter[] parameters;

  CodeSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      Class[] exceptionTypes,
      String[] parameterNames,
      Class[] parameterTypes,
      Parameter[] parameters) {
    super(declaringType, declaringTypeName, modifiers, name);
    this.exceptionTypes = Arrays.copyOf(exceptionTypes, exceptionTypes.length);
    this.parameterTypes = Arrays.copyOf(parameterTypes, parameterTypes.length);
    if (parameterNames == null) {
      this.parameterNames =
          IntStream.range(0, parameterTypes.length).mapToObj(i -> "arg" + i).toArray(String[]::new);
    } else {
      this.parameterNames = Arrays.copyOf(parameterNames, parameterNames.length);
    }
    this.parameters = Arrays.copyOf(parameters, parameters.length);
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
}
