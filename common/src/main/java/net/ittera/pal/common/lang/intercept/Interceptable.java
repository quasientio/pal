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

/**
 * Base abstract class for interceptable invokable types, such as method calls and field operations.
 *
 * <p>This class serves as the foundational element for defining entities that can be intercepted
 * during runtime.
 *
 * <p>Subclasses must implement the {@link #toSerializedString()} method to provide a serialized
 * representation of the interceptable entity.
 *
 * @see InterceptableType
 */
public abstract class Interceptable {

  /**
   * Enumeration of interceptable types that define the nature of invokable operations.
   *
   * <p>This enum distinguishes between different kinds of interceptable entities, such as method
   * calls and field operations, each associated with a unique byte identifier.
   */
  public enum InterceptableType {
    /** Represents a method call interceptable with a unique identifier. */
    METHOD_CALL((byte) 1),

    /* Represents a field operation interceptable with a unique identifier. */
    FIELD_OP((byte) 2);

    /* The unique byte identifier associated with the interceptable type. */
    private final byte idx;

    /**
     * Constructs an {@code InterceptableType} with the specified byte identifier.
     *
     * @param idx the byte identifier for this interceptable type
     */
    InterceptableType(byte idx) {
      this.idx = idx;
    }

    /**
     * Retrieves the {@code InterceptableType} corresponding to the provided byte value.
     *
     * @param typeAsByte the byte value representing an interceptable type
     * @return the matching {@code InterceptableType}
     * @throws IllegalArgumentException if no matching type is found for the given byte
     */
    public static InterceptableType fromByte(byte typeAsByte) {
      return switch (typeAsByte) {
        case 1 -> METHOD_CALL;
        case 2 -> FIELD_OP;
        default -> throw new IllegalArgumentException("Unknown interceptable type: " + typeAsByte);
      };
    }

    /**
     * Returns the byte identifier associated with this interceptable type.
     *
     * @return the byte value of this interceptable type
     */
    public byte toByte() {
      return idx;
    }
  }

  /** The name identifying this interceptable entity. This field is immutable and cannot be null. */
  @Nonnull private final String name;

  /**
   * The type of this interceptable entity, as defined by {@link InterceptableType}. This field is
   * immutable and cannot be null.
   */
  @Nonnull private final InterceptableType type;

  /**
   * Constructs an {@code Interceptable} with the specified name and type.
   *
   * @param name the name of the interceptable entity; must not be null
   * @param type the type of the interceptable entity; must not be null
   * @throws NullPointerException if {@code name} or {@code type} is null
   */
  protected Interceptable(@Nonnull String name, @Nonnull InterceptableType type) {
    this.name = Objects.requireNonNull(name);
    this.type = Objects.requireNonNull(type);
  }

  /**
   * Serializes this interceptable entity into its string representation.
   *
   * @return the serialized string representation of this interceptable
   */
  public abstract String toSerializedString();

  /**
   * Retrieves the name of this interceptable entity.
   *
   * @return the name of the interceptable
   */
  @Nonnull
  public final String getName() {
    return name;
  }

  /**
   * Retrieves the type of this interceptable entity.
   *
   * @return the {@link InterceptableType} of the interceptable
   */
  @Nonnull
  public final InterceptableType getType() {
    return type;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Checks for equality based on {@code name} and {@code type} fields.
   *
   * @param o the object to compare with
   * @return {@code true} if the specified object is equal to this interceptable, {@code false}
   *     otherwise
   */
  @Override
  @SuppressWarnings("EqualsGetClass")
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

  /**
   * {@inheritDoc}
   *
   * <p>Computes the hash code based on the {@code name} and {@code type} fields.
   *
   * @return the hash code value for this interceptable
   */
  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}
