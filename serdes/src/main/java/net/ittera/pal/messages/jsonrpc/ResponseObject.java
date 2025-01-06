package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import javax.annotation.Nullable;
import net.ittera.pal.serdes.Unwrappable;

public class ResponseObject implements Unwrappable {

  @Nullable private String type;

  @SerializedName("null")
  private Boolean isNull;

  @Nullable private String value;

  @Nullable private Integer ref;

  public ResponseObject() {}

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

  @Nullable
  @Override
  public Integer getRef() {
    return ref;
  }

  public void setRef(@Nullable Integer ref) {
    this.ref = ref;
  }

  @Nullable
  @Override
  public String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  @Nullable
  @Override
  public String getValue() {
    return value;
  }

  public void setValue(@Nullable String value) {
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
        && Objects.equals(ref, that.ref);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, isNull, value, ref);
  }

  @Override
  public String toString() {
    return "ResponseObject{"
        + "type='"
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

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ResponseObject responseObject = new ResponseObject();

    public Builder withType(@Nullable String type) {
      responseObject.setType(type);
      return this;
    }

    public Builder withIsNull(boolean isNull) {
      responseObject.setIsNull(isNull);
      return this;
    }

    public Builder withValue(@Nullable String value) {
      responseObject.setValue(value);
      return this;
    }

    public Builder withRef(@Nullable Integer ref) {
      responseObject.setRef(ref);
      return this;
    }

    public ResponseObject build() {
      return responseObject;
    }
  }
}
