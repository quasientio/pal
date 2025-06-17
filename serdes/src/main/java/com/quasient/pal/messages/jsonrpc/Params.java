package com.quasient.pal.messages.jsonrpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents the parameters for a JSON-RPC request.
 *
 * <p>This class encapsulates the various parameters that can be included in a JSON-RPC message,
 * such as the type of request, method to be invoked, field, instance identifier, arguments, and an
 * optional value. It supports building instances through the {@link Builder} class.
 */
public class Params {
  /** Specifies the type of JSON-RPC request. */
  private String type;

  /** Optional method name to be invoked in the JSON-RPC request. */
  @Nullable private String method;

  /** Optional field name associated with the JSON-RPC request. */
  @Nullable private String field;

  /** Optional instance identifier for the JSON-RPC request. */
  @Nullable private Integer instance;

  /** List of arguments to be passed with the JSON-RPC request. */
  private List<Argument> args = new ArrayList<>();

  /** Optional value associated with the JSON-RPC request. */
  @Nullable private Argument value;

  /**
   * Retrieves the type of the JSON-RPC request.
   *
   * @return the request type
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the type of the JSON-RPC request.
   *
   * @param type the request type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Retrieves the method name of the JSON-RPC request.
   *
   * @return the method name, or {@code null} if not set
   */
  @Nullable
  public String getMethod() {
    return method;
  }

  /**
   * Sets the method name of the JSON-RPC request.
   *
   * @param method the method name, or {@code null} to unset
   */
  public void setMethod(@Nullable String method) {
    this.method = method;
  }

  /**
   * Retrieves the field name associated with the JSON-RPC request.
   *
   * @return the field name, or {@code null} if not set
   */
  @Nullable
  public String getField() {
    return field;
  }

  /**
   * Sets the field name associated with the JSON-RPC request.
   *
   * @param field the field name, or {@code null} to unset
   */
  public void setField(@Nullable String field) {
    this.field = field;
  }

  /**
   * Retrieves the instance identifier for the JSON-RPC request.
   *
   * @return the instance identifier, or {@code null} if not set
   */
  @Nullable
  public Integer getInstance() {
    return instance;
  }

  /**
   * Sets the instance identifier for the JSON-RPC request.
   *
   * @param instance the instance identifier, or {@code null} to unset
   */
  public void setInstance(@Nullable Integer instance) {
    this.instance = instance;
  }

  /**
   * Retrieves the list of arguments for the JSON-RPC request.
   *
   * @return the list of arguments
   */
  public List<Argument> getArgs() {
    return args;
  }

  /**
   * Sets the list of arguments for the JSON-RPC request.
   *
   * @param args the list of arguments; if {@code null}, an empty list is assigned
   */
  public void setArgs(List<Argument> args) {
    this.args = Objects.requireNonNullElseGet(args, ArrayList::new);
  }

  /**
   * Retrieves the value associated with the JSON-RPC request.
   *
   * @return the value, or {@code null} if not set
   */
  @Nullable
  public Argument getValue() {
    return value;
  }

  /**
   * Sets the value associated with the JSON-RPC request.
   *
   * @param value the value to set, or {@code null} to unset
   */
  public void setValue(@Nullable Argument value) {
    this.value = value;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Params params)) {
      return false;
    }
    return Objects.equals(type, params.type)
        && Objects.equals(method, params.method)
        && Objects.equals(field, params.field)
        && Objects.equals(instance, params.instance)
        && Objects.equals(args, params.args)
        && Objects.equals(value, params.value);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(type, method, field, instance, args, value);
  }

  /** {@inheritDoc} */
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

  /**
   * Creates a new {@link Builder} instance for constructing {@link Params} objects.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder class for constructing {@link Params} instances.
   *
   * <p>Provides a fluent API for setting various parameters and building the final {@link Params}
   * object.
   */
  public static class Builder {
    private final Params params = new Params();

    /**
     * Sets the type of the JSON-RPC request.
     *
     * @param type the request type
     * @return the current builder instance
     */
    public Builder withType(String type) {
      params.setType(type);
      return this;
    }

    /**
     * Sets the method name of the JSON-RPC request.
     *
     * @param method the method name
     * @return the current builder instance
     */
    public Builder withMethod(String method) {
      params.setMethod(method);
      return this;
    }

    /**
     * Sets the field name associated with the JSON-RPC request.
     *
     * @param field the field name
     * @return the current builder instance
     */
    public Builder withField(String field) {
      params.setField(field);
      return this;
    }

    /**
     * Sets the instance identifier for the JSON-RPC request.
     *
     * @param instance the instance identifier, or {@code null} to unset
     * @return the current builder instance
     */
    public Builder withInstance(@Nullable Integer instance) {
      params.setInstance(instance);
      return this;
    }

    /**
     * Adds an argument to the JSON-RPC request.
     *
     * @param arg the argument to add
     * @return the current builder instance
     */
    public Builder addArg(Argument arg) {
      if (params.getArgs() == null) {
        params.setArgs(new ArrayList<>());
      }
      params.getArgs().add(arg);
      return this;
    }

    /**
     * Sets the list of arguments for the JSON-RPC request.
     *
     * @param args the list of arguments
     * @return the current builder instance
     */
    public Builder withArgs(List<Argument> args) {
      params.setArgs(args);
      return this;
    }

    /**
     * Sets the value associated with the JSON-RPC request.
     *
     * @param value the value to set, or {@code null} to unset
     * @return the current builder instance
     */
    public Builder withValue(Argument value) {
      params.setValue(value);
      return this;
    }

    /**
     * Builds and returns the configured {@link Params} instance.
     *
     * @return the constructed Params object
     */
    public Params build() {
      return params;
    }
  }
}
