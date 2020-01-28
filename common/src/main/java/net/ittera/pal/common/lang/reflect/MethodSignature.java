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

import java.lang.reflect.Method;

public class MethodSignature extends CodeSignature {

  private final Method method;
  private final Class returnType;

  public MethodSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      Class[] exceptionTypes,
      String[] parameterNames,
      Class[] parameterTypes,
      Method method,
      Class returnType) {
    super(
        declaringType,
        declaringTypeName,
        modifiers,
        name,
        exceptionTypes,
        parameterNames,
        parameterTypes,
        method.getParameters());
    this.method = method;
    this.returnType = returnType;
  }

  public MethodSignature(Method method) {
    this(
        method.getDeclaringClass(),
        method.getDeclaringClass().getTypeName(),
        method.getModifiers(),
        method.getName(),
        method.getExceptionTypes(),
        null,
        method.getParameterTypes(),
        method,
        method.getReturnType());
  }

  public Method getMethod() {
    return method;
  }

  public Class getReturnType() {
    return returnType;
  }
}
