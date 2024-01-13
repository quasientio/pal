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

import java.util.Objects;
import javax.annotation.Nonnull;

/** Base abstract class for interceptable invokable types: method and field ops. */
public abstract class Interceptable {

  public enum InterceptableType {
    METHOD_CALL,
    FIELD_OP;

    public static InterceptableType fromByte(byte typeAsByte) {
      return InterceptableType.values()[typeAsByte - 1];
    }

    public byte toByte() {
      return (byte) (this.ordinal() + 1);
    }
  }

  @Nonnull private final String name;
  @Nonnull private final InterceptableType type;

  protected Interceptable(@Nonnull String name, @Nonnull InterceptableType type) {
    this.name = Objects.requireNonNull(name);
    this.type = Objects.requireNonNull(type);
  }

  public abstract String toSerializedString();

  @Nonnull
  public final String getName() {
    return name;
  }

  @Nonnull
  public final InterceptableType getType() {
    return type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Interceptable that = (Interceptable) o;
    return name.equals(that.name) && type == that.type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}
