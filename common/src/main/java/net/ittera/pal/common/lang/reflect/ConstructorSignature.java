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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

@SuppressWarnings("rawtypes")
public final class ConstructorSignature extends CodeSignature {

  @Nonnull private final Constructor constructor;

  public ConstructorSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      Class[] exceptionTypes,
      Params params,
      @Nonnull Constructor constructor) {
    super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, params);
    this.constructor = Objects.requireNonNull(constructor);
  }

  public ConstructorSignature(Constructor constructor) {
    this(
        constructor.getDeclaringClass(),
        constructor.getDeclaringClass().getTypeName(),
        constructor.getModifiers(),
        constructor.getName(),
        constructor.getExceptionTypes(),
        new Params(null, constructor.getParameterTypes(), constructor.getParameters()),
        constructor);
  }

  @Nonnull
  public Constructor getConstructor() {
    return constructor;
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
    ConstructorSignature that = (ConstructorSignature) o;
    return constructor.equals(that.constructor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), constructor);
  }

  @Override
  public String toString() {
    return "ConstructorSignature{"
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
        + ", constructor="
        + constructor
        + '}';
  }
}
