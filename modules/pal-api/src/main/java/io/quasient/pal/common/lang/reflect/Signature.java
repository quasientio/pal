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
