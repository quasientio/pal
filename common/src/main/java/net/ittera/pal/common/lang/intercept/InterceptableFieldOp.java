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
import javax.annotation.Nonnull;
import net.ittera.pal.common.lang.FieldOpType;

/**
 * Represents an interceptable field operation within the PAL runtime. This class encapsulates the
 * type of operation performed on a field and its name, allowing for interception and manipulation
 * of field operations.
 *
 * <p>Instances of this class are immutable and can be used to track or modify field operations with
 * specific types defined in {@link FieldOpType}.
 *
 * @see Interceptable
 * @see FieldOpType
 */
public final class InterceptableFieldOp extends Interceptable {

  /**
   * The separator used to serialize and deserialize field operation strings. This constant is used
   * to delimit the field name and operation type in the serialized representation.
   */
  private static final String FIELD_SEP = "&&";

  /**
   * The type of operation performed on the field. This field is non-null and defines the nature of
   * the field operation, such as read or write.
   */
  @Nonnull private final FieldOpType fieldOpType;

  /**
   * Constructs a new {@code InterceptableFieldOp} with the specified name and field operation type.
   *
   * <p>This constructor initializes the interceptable field operation with a given name and
   * operation type. It ensures that the provided {@code fieldOpType} is not null.
   *
   * @param name the name of the field operation; must not be null
   * @param fieldOpType the type of field operation; must not be null
   * @throws NullPointerException if {@code fieldOpType} is null
   */
  public InterceptableFieldOp(String name, @Nonnull FieldOpType fieldOpType) {
    super(name, InterceptableType.FIELD_OP);
    this.fieldOpType = Objects.requireNonNull(fieldOpType, "fieldOpType must not be null");
  }

  /**
   * {@inheritDoc}
   *
   * @param o the reference object with which to compare
   * @return {@code true} if this object is the same as the {@code o} argument; {@code false}
   *     otherwise
   */
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
    InterceptableFieldOp that = (InterceptableFieldOp) o;
    return fieldOpType == that.fieldOpType;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a hash code value for the object. The hash code is based on the field name,
   * interceptable type, and field operation type.
   *
   * @return a hash code value for this object
   */
  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType(), fieldOpType);
  }

  /**
   * Retrieves the type of operation performed on the field.
   *
   * @return the {@code FieldOpType} representing the field operation
   */
  @Nonnull
  public FieldOpType getFieldOpType() {
    return fieldOpType;
  }

  /**
   * Returns a string representation of the {@code InterceptableFieldOp}. The string includes the
   * field operation type, name, and interceptable type.
   *
   * <p>{@inheritDoc}
   *
   * @return a string representation of the object
   */
  @Override
  public String toString() {
    return "InterceptableFieldOp{"
        + "fieldOpType="
        + fieldOpType
        + ", name='"
        + getName()
        + '\''
        + ", type="
        + getType()
        + '}';
  }

  /**
   * Serializes the {@code InterceptableFieldOp} into a string format. The serialized string
   * consists of the field name and the operation type's byte representation, separated by {@code
   * FIELD_SEP}.
   *
   * @return the serialized string representation of the field operation
   */
  @Override
  public String toSerializedString() {
    return format("%s" + FIELD_SEP + "%d", getName(), fieldOpType.toByte());
  }

  /**
   * Deserializes a string into an {@code InterceptableFieldOp} instance. The serialized string must
   * be in the format produced by {@link #toSerializedString()}, containing the field name and
   * operation type separated by {@code FIELD_SEP}.
   *
   * @param serialized the serialized string to deserialize
   * @return a new {@code InterceptableFieldOp} instance based on the serialized data
   * @throws IllegalArgumentException if the serialized string is not in the expected format
   * @throws NullPointerException if the serialized string is null
   * @see #toSerializedString()
   */
  public static InterceptableFieldOp fromSerializedString(String serialized) {
    Objects.requireNonNull(serialized, "serialized string must not be null");
    @SuppressWarnings("StringSplitter")
    final String[] parts = serialized.split(FIELD_SEP);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid serialized format for InterceptableFieldOp");
    }
    final String name = parts[0];
    final FieldOpType type = FieldOpType.fromByte(Byte.parseByte(parts[1]));
    return new InterceptableFieldOp(name, type);
  }
}
