package net.ittera.pal.messages.jsonrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

public class Params {
  private String type;

  @Nullable private String method;

  @Nullable private String field;

  @Nullable private Integer instance;

  private List<Argument> args = new ArrayList<>();

  @Nullable private Argument value;

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  @Nullable
  public Integer getInstance() {
    return instance;
  }

  public void setInstance(@Nullable Integer instance) {
    this.instance = instance;
  }

  public List<Argument> getArgs() {
    return args;
  }

  public void setArgs(List<Argument> args) {
    if (args == null) {
      this.args = new ArrayList<>();
    } else {
      this.args = args;
    }
  }

  public Argument getValue() {
    return value;
  }

  public void setValue(Argument value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Params params)) return false;
    return Objects.equals(type, params.type)
        && Objects.equals(method, params.method)
        && Objects.equals(field, params.field)
        && Objects.equals(instance, params.instance)
        && Objects.equals(args, params.args)
        && Objects.equals(value, params.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, method, field, instance, args, value);
  }

  @Override
  public String toString() {
    return "Params{"
        + "args="
        + args
        + ", field='"
        + field
        + '\''
        + ", instance="
        + instance
        + ", method='"
        + method
        + '\''
        + ", type='"
        + type
        + '\''
        + ", value="
        + value
        + '}';
  }

  public static class Builder {
    private final Params params = new Params();

    public Builder withType(String type) {
      params.setType(type);
      return this;
    }

    public Builder withMethod(String method) {
      params.setMethod(method);
      return this;
    }

    public Builder withField(String field) {
      params.setField(field);
      return this;
    }

    public Builder withInstance(@Nullable Integer instance) {
      params.setInstance(instance);
      return this;
    }

    public Builder addArg(Argument arg) {
      if (params.getArgs() == null) {
        params.setArgs(new ArrayList<>());
      }
      params.getArgs().add(arg);
      return this;
    }

    public Builder withArgs(List<Argument> args) {
      params.setArgs(args);
      return this;
    }

    public Builder withValue(Argument value) {
      params.setValue(value);
      return this;
    }

    public Params build() {
      return params;
    }
  }
}
