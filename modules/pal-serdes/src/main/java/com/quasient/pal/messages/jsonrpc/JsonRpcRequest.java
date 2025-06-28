/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages.jsonrpc;

import com.quasient.pal.serdes.jsonrpc.JsonRpcRequestValidator;
import java.util.Objects;

/**
 * Represents a JSON-RPC request message.
 *
 * <p>This class encapsulates the details of a JSON-RPC request, including the method name,
 * parameters, and identifier.
 */
public class JsonRpcRequest extends JsonRpcMessage {

  /** The name of the method to be invoked. */
  private String method;

  /** The parameters to be passed to the method. */
  private Params params;

  /** Constructs a new {@code JsonRpcRequest} with the default JSON-RPC version. */
  public JsonRpcRequest() {
    setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
  }

  /**
   * Retrieves the method name of this JSON-RPC request.
   *
   * @return the method name.
   */
  public String getMethod() {
    return method;
  }

  /**
   * Sets the method name for this JSON-RPC request.
   *
   * @param method the name of the method to be invoked.
   */
  public void setMethod(String method) {
    this.method = method;
  }

  /**
   * Retrieves the parameters of this JSON-RPC request.
   *
   * @return the parameters object, or {@code null} if none are set.
   */
  public Params getParams() {
    return params;
  }

  /**
   * Sets the parameters for this JSON-RPC request.
   *
   * @param params the parameters to be passed to the method.
   */
  public void setParams(Params params) {
    this.params = params;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcRequest that)) {
      return false;
    }
    return getId().equals(that.getId())
        && getJsonrpc().equals(that.getJsonrpc())
        && Objects.equals(method, that.method)
        && Objects.equals(params, that.params);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(getId(), getJsonrpc(), method, params);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonRpcRequest{"
        + "jsonrpc='"
        + getJsonrpc()
        + '\''
        + ", method='"
        + method
        + '\''
        + ", params="
        + params
        + ", id='"
        + id
        + '\''
        + '}';
  }

  /**
   * Creates a new builder for constructing {@code JsonRpcRequest} instances.
   *
   * @return a new {@code Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing instances of {@link JsonRpcRequest}. */
  public static class Builder {
    private final JsonRpcRequest request = new JsonRpcRequest();

    /**
     * Sets the identifier of the JSON-RPC request using a {@code String}.
     *
     * @param id the identifier to set.
     * @return the current {@code Builder} instance.
     */
    public Builder withId(String id) {
      request.setId(id);
      return this;
    }

    /**
     * Sets the identifier of the JSON-RPC request using a {@code long}.
     *
     * @param id the identifier to set.
     * @return the current {@code Builder} instance.
     */
    public Builder withId(long id) {
      request.setId(id);
      return this;
    }

    /**
     * Sets the identifier of the JSON-RPC request using an {@code int}.
     *
     * @param id the identifier to set.
     * @return the current {@code Builder} instance.
     */
    public Builder withId(int id) {
      request.setId(id);
      return this;
    }

    /**
     * Sets the method name for the JSON-RPC request.
     *
     * @param method the name of the method to be invoked.
     * @return the current {@code Builder} instance.
     */
    public Builder withMethod(String method) {
      request.setMethod(method);
      return this;
    }

    /**
     * Sets the parameters for the JSON-RPC request.
     *
     * @param params the parameters to be passed to the method.
     * @return the current {@code Builder} instance.
     */
    public Builder withParams(Params params) {
      request.setParams(params);
      return this;
    }

    /**
     * Builds and returns the configured {@link JsonRpcRequest} instance.
     *
     * @return the constructed {@code JsonRpcRequest}.
     */
    public JsonRpcRequest build() {
      if (request.getJsonrpc() == null) {
        request.setJsonrpc(JsonRpcMessage.JSON_RPC_VERSION);
      }
      JsonRpcRequestValidator.validate(request);
      return request;
    }
  }
}
