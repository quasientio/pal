/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents the return value of a JSON-RPC response.
 *
 * <p>This class encapsulates whether the response contains no result (void), the result value if
 * present, and the executable that generated the response. It is used to serialize and deserialize
 * JSON-RPC responses.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "JSON-RPC DTO - mutable by design for serialization")
public class JsonRpcResponseReturnValue {

  /** Indicates whether the JSON-RPC response has no result. */
  @SerializedName("void")
  private Boolean isVoid;

  /** The result value of the JSON-RPC response, if present. */
  @Nullable private ResponseObject value;

  /** The executable that generated this JSON-RPC response. */
  private Executable from;

  /** Constructs an empty JsonRpcResponseReturnValue instance. */
  public JsonRpcResponseReturnValue() {}

  /**
   * Checks if the JSON-RPC response has no result.
   *
   * @return {@code true} if the response is void, {@code false} otherwise.
   */
  public boolean getIsVoid() {
    return Boolean.TRUE.equals(isVoid);
  }

  /**
   * Sets whether the JSON-RPC response has no result.
   *
   * @param isVoid {@code true} to indicate a void response, {@code false} otherwise.
   */
  public void setIsVoid(boolean isVoid) {
    this.isVoid = isVoid;
  }

  /**
   * Retrieves the result value of the JSON-RPC response.
   *
   * @return the response value, or {@code null} if the response is void.
   */
  @Nullable
  public ResponseObject getValue() {
    return value;
  }

  /**
   * Sets the result value of the JSON-RPC response.
   *
   * @param value the response value, or {@code null} if the response is void.
   */
  public void setValue(@Nullable ResponseObject value) {
    this.value = value;
  }

  /**
   * Retrieves the executable that generated this JSON-RPC response.
   *
   * @return the originating executable.
   */
  public Executable getFrom() {
    return from;
  }

  /**
   * Sets the executable that generated this JSON-RPC response.
   *
   * @param from the originating executable.
   */
  public void setFrom(Executable from) {
    this.from = from;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcResponseReturnValue that)) {
      return false;
    }
    return Objects.equals(isVoid, that.isVoid) && Objects.equals(value, that.value);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(isVoid, value);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonRpcResponseReturnValue{" + "isVoid=" + isVoid + ", value=" + value + '}';
  }

  /**
   * Creates a new {@link Builder} for constructing instances of {@link JsonRpcResponseReturnValue}.
   *
   * @return a new Builder instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for {@link JsonRpcResponseReturnValue}. */
  public static class Builder {
    /** The {@link JsonRpcResponseReturnValue} being built. */
    private final JsonRpcResponseReturnValue returnValue = new JsonRpcResponseReturnValue();

    /**
     * Sets the void status for the response being built.
     *
     * @param isVoid {@code true} to indicate a void response, {@code false} otherwise.
     * @return this Builder instance.
     */
    public Builder withIsVoid(boolean isVoid) {
      returnValue.setIsVoid(isVoid);
      return this;
    }

    /**
     * Sets the result value for the response being built.
     *
     * @param value the response value, or {@code null} if the response is void.
     * @return this Builder instance.
     */
    public Builder withValue(@Nullable ResponseObject value) {
      returnValue.setValue(value);
      return this;
    }

    /**
     * Sets the executable that generated the response being built.
     *
     * @param from the originating executable.
     * @return this Builder instance.
     */
    public Builder withFrom(Executable from) {
      returnValue.setFrom(from);
      return this;
    }

    /**
     * Builds and returns the {@link JsonRpcResponseReturnValue} instance.
     *
     * @return the constructed JsonRpcResponseReturnValue.
     */
    public JsonRpcResponseReturnValue build() {
      return returnValue;
    }
  }
}
