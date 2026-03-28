/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.lang.reflect;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Field;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents the signature of a specific field within a class. This class encapsulates reflection
 * information about the field, including its type and declaring class.
 */
@SuppressWarnings("rawtypes")
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "Wrapper for java.lang.reflect.Field - intentionally exposes the wrapped object")
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
