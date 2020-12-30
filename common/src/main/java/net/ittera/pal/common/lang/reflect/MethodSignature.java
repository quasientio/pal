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
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

@SuppressWarnings("rawtypes")
public final class MethodSignature extends CodeSignature {

  @Nonnull private final Method method;
  @Nonnull private final Class returnType;

  public MethodSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      Class[] exceptionTypes,
      Params params,
      @Nonnull Method method,
      @Nonnull Class returnType) {
    super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, params);
    this.method = Objects.requireNonNull(method);
    this.returnType = Objects.requireNonNull(returnType);
  }

  public MethodSignature(Method method) {
    this(
        method.getDeclaringClass(),
        method.getDeclaringClass().getTypeName(),
        method.getModifiers(),
        method.getName(),
        method.getExceptionTypes(),
        new Params(null, method.getParameterTypes(), method.getParameters()),
        method,
        method.getReturnType());
  }

  @Nonnull
  public Method getMethod() {
    return method;
  }

  @Nonnull
  public Class getReturnType() {
    return returnType;
  }

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
    MethodSignature that = (MethodSignature) o;
    return method.equals(that.method) && returnType.equals(that.returnType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), method, returnType);
  }

  @Override
  public String toString() {
    return "MethodSignature{"
        + "declaringType="
        + this.getDeclaringType()
        + ", declaringTypeName="
        + this.getDeclaringTypeName()
        + ", name="
        + this.getName()
        + ", modifiers="
        + this.getModifiers()
        + ", exceptionTypes="
        + Arrays.toString(getExceptionTypes())
        + ", parameterNames="
        + Arrays.toString(getParameterNames())
        + ", parameterTypes="
        + Arrays.toString(getParameterTypes())
        + ", parameters="
        + Arrays.toString(getParameters())
        + ", method="
        + method
        + ", returnType="
        + returnType
        + '}';
  }
}
