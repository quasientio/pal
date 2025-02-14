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

/**
 * Represents the signature of a specific method, encapsulating its declaring class, name,
 * modifiers, parameter types, exception types, and return type. Extends {@link CodeSignature} to
 * include method-specific details such as the associated {@link Method} object and its return type.
 */
@SuppressWarnings("rawtypes")
public final class MethodSignature extends CodeSignature {

  /** The {@link Method} instance that defines the method signature. */
  @Nonnull private final Method method;

  /** The {@link Class} object representing the return type of the method. */
  @Nonnull private final Class returnType;

  /**
   * Creates a new {@code MethodSignature} instance with the specified attributes.
   *
   * @param declaringType the class that declares the method
   * @param declaringTypeName the fully qualified name of the declaring class
   * @param modifiers the modifier flags for the method
   * @param name the name of the method
   * @param exceptionTypes the array of exception types thrown by the method
   * @param params the parameters of the method
   * @param method the {@link Method} object representing the method
   * @param returnType the {@link Class} object representing the return type of the method
   * @throws NullPointerException if {@code method} or {@code returnType} is {@code null}
   */
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

  /**
   * Constructs a {@code MethodSignature} based on the provided {@link Method} instance.
   *
   * @param method the {@link Method} object to create the signature from
   * @throws NullPointerException if {@code method} is {@code null}
   */
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

  /**
   * Returns the {@link Method} instance associated with this signature.
   *
   * @return the {@link Method} object representing the method signature
   */
  @Nonnull
  public Method getMethod() {
    return method;
  }

  /**
   * Returns the return type of the method.
   *
   * @return the {@link Class} representing the method's return type
   */
  @Nonnull
  public Class getReturnType() {
    return returnType;
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
    if (!super.equals(o)) {
      return false;
    }
    MethodSignature that = (MethodSignature) o;
    return method.equals(that.method) && returnType.equals(that.returnType);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), method, returnType);
  }

  /** {@inheritDoc} */
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
