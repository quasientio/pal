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

import java.lang.reflect.Field;
import java.util.Objects;
import javax.annotation.Nonnull;

@SuppressWarnings("rawtypes")
public final class FieldSignature extends Signature {

  @Nonnull private final Field field;
  @Nonnull private final Class fieldType;

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

  public FieldSignature(Field field) {
    this(
        field.getDeclaringClass(),
        field.getDeclaringClass().getTypeName(),
        field.getModifiers(),
        field.getName(),
        field,
        field.getType());
  }

  @Nonnull
  public Field getField() {
    return field;
  }

  @Nonnull
  public Class getFieldType() {
    return fieldType;
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
    FieldSignature that = (FieldSignature) o;
    return field.equals(that.field) && fieldType.equals(that.fieldType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), field, fieldType);
  }

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
