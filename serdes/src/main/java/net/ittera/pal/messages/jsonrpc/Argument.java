package net.ittera.pal.messages.jsonrpc;

import java.util.Objects;

/* This class is deserialized within the custom ParamsDeserializer adapter */
public class Argument {
  public static final Argument NULL = new Argument();

  @javax.annotation.Nullable private Object value;

  @javax.annotation.Nullable private Integer ref;

  @javax.annotation.Nullable private String type;

  public Argument() {}

  public Argument(Object value, @javax.annotation.Nullable String type) {
    this.value = value;
    this.type = type;
  }

  public Argument(Integer ref) {
    this.ref = ref;
  }

  @javax.annotation.Nullable
  public Object getValue() {
    return value;
  }

  public void setValue(@javax.annotation.Nullable Object value) {
    this.value = value;
  }

  @javax.annotation.Nullable
  public Integer getRef() {
    return ref;
  }

  public void setRef(@javax.annotation.Nullable Integer ref) {
    this.ref = ref;
  }

  @javax.annotation.Nullable
  public String getType() {
    return type;
  }

  public void setType(@javax.annotation.Nullable String type) {
    this.type = type;
  }

  public boolean isNull() {
    return value == null && ref == null && type == null;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Argument argument)) {
      return false;
    }
    return Objects.equals(value, argument.value)
        && Objects.equals(ref, argument.ref)
        && Objects.equals(type, argument.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, ref, type);
  }

  @Override
  public String toString() {
    return "Argument{"
        + "ref="
        + ref
        + ", type='"
        + type
        + '\''
        + ", value="
        + value
        + " ("
        + (value != null ? value.getClass().getSimpleName() : "null")
        + ")"
        + '}';
  }

  public static class Builder {
    private final Argument argument = new Argument();

    public Builder withValue(@javax.annotation.Nullable Object value) {
      argument.setValue(value);
      return this;
    }

    public Builder withRef(@javax.annotation.Nullable Integer ref) {
      argument.setRef(ref);
      return this;
    }

    public Builder withType(@javax.annotation.Nullable String type) {
      argument.setType(type);
      return this;
    }

    public Argument build() {
      return argument;
    }
  }
}
