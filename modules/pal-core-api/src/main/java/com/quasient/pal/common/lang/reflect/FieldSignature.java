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
