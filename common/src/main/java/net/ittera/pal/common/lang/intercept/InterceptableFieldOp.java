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

package net.ittera.pal.common.lang.intercept;

import static java.lang.String.format;

import java.util.Objects;
import net.ittera.pal.common.lang.FieldOpType;

public class InterceptableFieldOp extends Interceptable {
  private static final String FIELD_SEP = "&&";
  private final FieldOpType fieldOpType;

  public InterceptableFieldOp(String name, FieldOpType fieldOpType) {
    super(name, InterceptableType.FIELD_OP);
    this.fieldOpType = fieldOpType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptableFieldOp that = (InterceptableFieldOp) o;
    return type == that.type && fieldOpType == that.fieldOpType && name.equalsIgnoreCase(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, fieldOpType);
  }

  public FieldOpType getFieldOpType() {
    return fieldOpType;
  }

  @Override
  public String toString() {
    return "InterceptableFieldOp{"
        + "fieldOpType="
        + fieldOpType
        + ", name='"
        + name
        + '\''
        + ", type="
        + type
        + '}';
  }

  @Override
  public String toSerializedString() {
    return format("%s" + FIELD_SEP + "%d", name, fieldOpType.ordinal());
  }

  public static InterceptableFieldOp fromSerializedString(String serialized) {
    final String[] parts = serialized.split(FIELD_SEP);
    final String name = parts[0];
    final FieldOpType type = FieldOpType.values[Integer.parseInt(parts[1])];
    return new InterceptableFieldOp(name, type);
  }
}
