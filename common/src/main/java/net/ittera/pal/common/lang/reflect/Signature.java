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

/** Represents a signature within the reflection system of PAL. */
@SuppressWarnings("rawtypes")
public abstract class Signature {

  /** The class that declares this signature. */
  @Nonnull private final Class declaringType;

  /** The fully qualified name of the declaring type. */
  @Nonnull private final String declaringTypeName;

  /** The modifiers applied to the signature, such as public, static, etc. */
  private final int modifiers;

  /** The name of the signature. */
  @Nonnull private final String name;

  /**
   * Constructs a new Signature instance with the specified properties.
   *
   * @param declaringType the class that declares this signature; must not be null
   * @param declaringTypeName the fully qualified name of the declaring type; must not be null
   * @param modifiers the integer representing the modifiers of the signature
   * @param name the name of the signature; must not be null
   */
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

  /**
   * Retrieves the class that declares this signature.
   *
   * @return the declaring class
   */
  @Nonnull
  public final Class getDeclaringType() {
    return declaringType;
  }

  /**
   * Retrieves the fully qualified name of the declaring type.
   *
   * @return the name of the declaring type
   */
  @Nonnull
  public final String getDeclaringTypeName() {
    return declaringTypeName;
  }

  /**
   * Retrieves the modifiers applied to this signature.
   *
   * @return an integer representing the modifiers
   */
  public final int getModifiers() {
    return modifiers;
  }

  /**
   * Retrieves the name of this signature.
   *
   * @return the name of the signature
   */
  @Nonnull
  public final String getName() {
    return name;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(declaringType, declaringTypeName, modifiers, name);
  }
}
