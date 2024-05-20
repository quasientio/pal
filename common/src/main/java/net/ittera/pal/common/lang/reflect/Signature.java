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

import java.util.Objects;
import javax.annotation.Nonnull;

@SuppressWarnings("rawtypes")
public abstract class Signature {

  @Nonnull private final Class declaringType;
  @Nonnull private final String declaringTypeName;
  private final int modifiers;
  @Nonnull private final String name;

  Signature(
      @Nonnull Class declaringType,
      @Nonnull String declaringTypeName,
      int modifiers,
      @Nonnull String name) {
    this.declaringType = Objects.requireNonNull(declaringType);
    this.declaringTypeName = Objects.requireNonNull(declaringTypeName);
    this.modifiers = modifiers;
    this.name = Objects.requireNonNull(name);
  }

  @Nonnull
  public final Class getDeclaringType() {
    return declaringType;
  }

  @Nonnull
  public final String getDeclaringTypeName() {
    return declaringTypeName;
  }

  public final int getModifiers() {
    return modifiers;
  }

  @Nonnull
  public final String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Signature signature = (Signature) o;
    return modifiers == signature.modifiers
        && declaringType.equals(signature.declaringType)
        && declaringTypeName.equals(signature.declaringTypeName)
        && name.equals(signature.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(declaringType, declaringTypeName, modifiers, name);
  }
}
