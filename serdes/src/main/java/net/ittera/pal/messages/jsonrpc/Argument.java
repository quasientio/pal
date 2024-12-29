package net.ittera.pal.messages.jsonrpc;

import java.util.Objects;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;

/* This class is deserialized within the custom ParamsDeserializer adapter */
public class Argument {
  public static final Argument NULL = new Argument();

  @Nullable private Object value;
  @Nullable private Integer ref;
  @Nullable private String type;
  @Nullable private String name;

  public Argument() {}

  public Argument(@Nullable Object value, @Nullable String type) {
    this.value = value;
    this.type = type;
  }

  public Argument(@Nullable Integer ref) {
    this.ref = ref;
  }

  @Nullable
  public Object getValue() {
    return value;
  }

  public void setValue(@Nullable Object value) {
    this.value = value;
  }

  @Nullable
  public Integer getRef() {
    return ref;
  }

  public void setRef(@Nullable Integer ref) {
    this.ref = ref;
  }

  public void setRef(@Nullable ObjectRef ref) {
    if (ref == null) {
      this.ref = null;
    } else {
      this.ref = ref.getRef();
    }
  }

  @Nullable
  public String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  @Nullable
  public String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public boolean isNull() {
    return value == null && ref == null;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Argument argument)) {
      return false;
    }
    return Objects.equals(value, argument.value)
        && Objects.equals(ref, argument.ref)
        && Objects.equals(type, argument.type)
        && Objects.equals(name, argument.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, ref, type, name);
  }

  @Override
  public String toString() {
    return "Argument{"
        + "ref="
        + ref
        + ", type='"
        + type
        + '\''
        + ", name="
        + name
        + ", value="
        + value
        + " ("
        + (value != null ? value.getClass().getSimpleName() : "null")
        + ")"
        + '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Argument argument = new Argument();

    public Builder withValue(@Nullable Object value) {
      argument.setValue(value);
      return this;
    }

    public Builder withRef(@Nullable Integer ref) {
      argument.setRef(ref);
      return this;
    }

    public Builder withRef(@Nullable ObjectRef ref) {
      argument.setRef(ref);
      return this;
    }

    public Builder withType(@Nullable String type) {
      argument.setType(type);
      return this;
    }

    public Builder withName(@Nullable String name) {
      argument.setName(name);
      return this;
    }

    public Argument build() {
      return argument;
    }
  }
}
