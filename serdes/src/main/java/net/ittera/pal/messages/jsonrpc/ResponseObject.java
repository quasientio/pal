package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.Objects;
import net.ittera.pal.serdes.Unwrappable;

public class ResponseObject implements Unwrappable {

  @javax.annotation.Nullable private String type;

  @SerializedName("null")
  private Boolean isNull;

  @javax.annotation.Nullable private String value;

  @javax.annotation.Nullable private Integer ref;

  @SerializedName("array_values")
  @javax.annotation.Nullable
  private ResponseObject[] arrayValues = new ResponseObject[0];

  public ResponseObject() {}

  @javax.annotation.Nullable
  @Override
  public ResponseObject[] getArrayValues() {
    return arrayValues;
  }

  public void setArrayValues(@javax.annotation.Nullable ResponseObject[] arrayValues) {
    this.arrayValues = arrayValues;
  }

  public boolean getIsNull() {
    return isNull;
  }

  @Override
  public boolean isNull() {
    return isNull;
  }

  public void setIsNull(boolean isNull) {
    this.isNull = isNull;
  }

  @javax.annotation.Nullable
  @Override
  public Integer getRef() {
    return ref;
  }

  public void setRef(@javax.annotation.Nullable Integer ref) {
    this.ref = ref;
  }

  @javax.annotation.Nullable
  @Override
  public String getType() {
    return type;
  }

  public void setType(@javax.annotation.Nullable String type) {
    this.type = type;
  }

  @javax.annotation.Nullable
  @Override
  public String getValue() {
    return value;
  }

  public void setValue(@javax.annotation.Nullable String value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ResponseObject that)) {
      return false;
    }
    return Objects.equals(type, that.type)
        && Objects.equals(isNull, that.isNull)
        && Objects.equals(value, that.value)
        && Objects.equals(ref, that.ref)
        && Objects.deepEquals(arrayValues, that.arrayValues);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, isNull, value, ref, Arrays.hashCode(arrayValues));
  }

  @Override
  public String toString() {
    return "ResponseObject{"
        + "arrayValues="
        + Arrays.deepToString(arrayValues)
        + ", type='"
        + type
        + '\''
        + ", isNull="
        + isNull
        + ", value='"
        + value
        + '\''
        + ", ref="
        + ref
        + '}';
  }

  public static class Builder {
    private final ResponseObject responseObject = new ResponseObject();

    public Builder withType(@javax.annotation.Nullable String type) {
      responseObject.setType(type);
      return this;
    }

    public Builder withIsNull(boolean isNull) {
      responseObject.setIsNull(isNull);
      return this;
    }

    public Builder withValue(@javax.annotation.Nullable String value) {
      responseObject.setValue(value);
      return this;
    }

    public Builder withRef(@javax.annotation.Nullable Integer ref) {
      responseObject.setRef(ref);
      return this;
    }

    public Builder withArrayValues(@javax.annotation.Nullable ResponseObject[] arrayValues) {
      responseObject.setArrayValues(arrayValues);
      return this;
    }

    public ResponseObject build() {
      return responseObject;
    }
  }
}
