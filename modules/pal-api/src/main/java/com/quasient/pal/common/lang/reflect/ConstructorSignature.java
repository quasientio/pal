/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.reflect;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents the signature of a constructor, encapsulating metadata such as parameter types,
 * modifiers, and exceptions.
 */
@SuppressWarnings("rawtypes")
public final class ConstructorSignature extends CodeSignature {

  /** The {@link Constructor} instance that this signature represents. */
  @Nonnull private final Constructor constructor;

  /**
   * Constructs a {@code ConstructorSignature} with detailed metadata.
   *
   * @param declaringType the class that declares the constructor
   * @param declaringTypeName the name of the declaring class
   * @param modifiers the constructor's modifiers
   * @param name the name of the constructor
   * @param exceptionTypes the types of exceptions thrown by the constructor
   * @param params the parameters of the constructor
   * @param constructor the {@link Constructor} instance this signature represents; must not be
   *     {@code null}
   */
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

  /**
   * Constructs a {@code ConstructorSignature} based on the specified {@link Constructor}.
   *
   * @param constructor the {@link Constructor} to create the signature from; must not be {@code
   *     null}
   */
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

  /**
   * Returns the {@link Constructor} instance associated with this signature.
   *
   * @return the {@link Constructor} represented by this signature
   */
  @Nonnull
  public Constructor getConstructor() {
    return constructor;
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
    ConstructorSignature that = (ConstructorSignature) o;
    return constructor.equals(that.constructor);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), constructor);
  }

  /** {@inheritDoc} */
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
