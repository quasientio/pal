package com.ittera.cometa.common.lang.intercept;

import static java.lang.String.format;

import com.ittera.cometa.common.lang.FieldOpType;
import java.util.Objects;

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
