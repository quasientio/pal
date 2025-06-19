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

package com.quasient.pal.common.lang.reflect;

import java.lang.reflect.Field;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents the signature of a specific field within a class. This class encapsulates reflection
 * information about the field, including its type and declaring class.
 */
@SuppressWarnings("rawtypes")
public final class FieldSignature extends Signature {

  /** The reflection {@link Field} object representing the field's metadata. */
  @Nonnull private final Field field;

  /** The type of the field represented by this signature. */
  @Nonnull private final Class fieldType;

  /**
   * Constructs a new {@code FieldSignature} with the specified parameters.
   *
   * @param declaringType the class that declares the field
   * @param declaringTypeName the name of the declaring class
   * @param modifiers the field's Java language modifiers
   * @param name the name of the field
   * @param field the reflection {@link Field} object representing the field
   * @param fieldType the type of the field
   * @throws NullPointerException if {@code field} or {@code fieldType} is {@code null}
   */
  public FieldSignature(
      Class declaringType,
      String declaringTypeName,
      int modifiers,
      String name,
      @Nonnull Field field,
      @Nonnull Class fieldType) {
    super(declaringType, declaringTypeName, modifiers, name);
    this.field = Objects.requireNonNull(field);
    this.fieldType = Objects.requireNonNull(fieldType);
  }

  /**
   * Constructs a new {@code FieldSignature} for the specified {@link Field}.
   *
   * @param field the reflection {@link Field} object to create a signature for
   * @throws NullPointerException if {@code field} is {@code null}
   */
  public FieldSignature(Field field) {
    this(
        field.getDeclaringClass(),
        field.getDeclaringClass().getTypeName(),
        field.getModifiers(),
        field.getName(),
        field,
        field.getType());
  }

  /**
   * Returns the reflection {@link Field} object associated with this signature.
   *
   * @return the {@link Field} object representing the field's metadata
   */
  @Nonnull
  public Field getField() {
    return field;
  }

  /**
   * Returns the type of the field represented by this signature.
   *
   * @return the {@link Class} object representing the field's type
   */
  @Nonnull
  public Class getFieldType() {
    return fieldType;
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
    FieldSignature that = (FieldSignature) o;
    return field.equals(that.field) && fieldType.equals(that.fieldType);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), field, fieldType);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "FieldSignature{"
        + "declaringType="
        + this.getDeclaringType()
        + ", declaringTypeName="
        + this.getDeclaringTypeName()
        + ", name="
        + this.getName()
        + ", modifiers="
        + this.getModifiers()
        + ", field="
        + field
        + ", fieldType="
        + fieldType
        + '}';
  }
}
