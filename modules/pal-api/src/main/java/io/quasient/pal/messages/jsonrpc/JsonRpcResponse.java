/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.messages.jsonrpc;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a JSON-RPC response message.
 *
 * <p>This class encapsulates the result or error of a JSON-RPC request. It extends {@link
 * JsonRpcMessage} and provides methods to access and modify the response's contents, including the
 * result and error.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "JSON-RPC DTO - mutable by design for serialization")
public class JsonRpcResponse extends JsonRpcMessage {

  /**
   * The result returned by the JSON-RPC method, if the request was successful. Can be {@code null}
   * if an error occurred.
   */
  @Nullable private JsonRpcResponseReturnValue result;

  /**
   * The error object returned by the JSON-RPC method, if the request failed. Can be {@code null} if
   * the request was successful.
   */
  @Nullable private JsonRpcError error;

  /** Constructs a new {@code JsonRpcResponse} with the default JSON-RPC version. */
  public JsonRpcResponse() {
    setJsonrpc(JSON_RPC_VERSION);
  }

  /**
   * Retrieves the result of the JSON-RPC response.
   *
   * @return the result of the response, or {@code null} if an error occurred
   */
  @Nullable
  public JsonRpcResponseReturnValue getResult() {
    return result;
  }

  /**
   * Sets the result of the JSON-RPC response.
   *
   * @param result the result to set; can be {@code null} if an error occurred
   */
  public void setResult(@Nullable JsonRpcResponseReturnValue result) {
    this.result = result;
  }

  /**
   * Retrieves the error of the JSON-RPC response.
   *
   * @return the error of the response, or {@code null} if the request was successful
   */
  @Nullable
  public JsonRpcError getError() {
    return error;
  }

  /**
   * Sets the error of the JSON-RPC response.
   *
   * @param error the error to set; can be {@code null} if the request was successful
   */
  public void setError(@Nullable JsonRpcError error) {
    this.error = error;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcResponse that)) {
      return false;
    }
    return Objects.equals(result, that.result) && Objects.equals(error, that.error);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(result, error);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonRpcResponse{"
        + "jsonrpc='"
        + getJsonrpc()
        + '\''
        + ", result="
        + result
        + ", error="
        + error
        + ", id='"
        + id
        + '\''
        + '}';
  }

  /**
   * Creates a new {@code Builder} instance for constructing {@code JsonRpcResponse} objects.
   *
   * @return a new {@code Builder}
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing {@code JsonRpcResponse} instances. */
  public static class Builder {
    /** The {@code JsonRpcResponse} instance being built. */
    private final JsonRpcResponse response = new JsonRpcResponse();

    /**
     * Sets the identifier for the JSON-RPC response.
     *
     * @param id the identifier as a {@code String}
     * @return this builder instance
     */
    public Builder withId(String id) {
      response.setId(id);
      return this;
    }

    /**
     * Sets the identifier for the JSON-RPC response.
     *
     * @param id the identifier as a {@code Long}
     * @return this builder instance
     */
    public Builder withId(Long id) {
      response.setId(id);
      return this;
    }

    /**
     * Sets the identifier for the JSON-RPC response.
     *
     * @param id the identifier as an {@code int}
     * @return this builder instance
     */
    public Builder withId(int id) {
      response.setId(id);
      return this;
    }

    /**
     * Sets the result of the JSON-RPC response.
     *
     * @param result the result to set; can be {@code null} if an error occurred
     * @return this builder instance
     */
    public Builder withResult(@Nullable JsonRpcResponseReturnValue result) {
      response.setResult(result);
      return this;
    }

    /**
     * Sets the error of the JSON-RPC response.
     *
     * @param error the error to set; can be {@code null} if the request was successful
     * @return this builder instance
     */
    public Builder withError(@Nullable JsonRpcError error) {
      response.setError(error);
      return this;
    }

    /**
     * Builds and returns the {@code JsonRpcResponse} instance.
     *
     * <p>If the JSON-RPC version is not set, it defaults to {@link
     * JsonRpcMessage#JSON_RPC_VERSION}.
     *
     * @return the constructed {@code JsonRpcResponse}
     */
    public JsonRpcResponse build() {
      if (response.getJsonrpc() == null) {
        response.setJsonrpc(JSON_RPC_VERSION);
      }
      return response;
    }
  }
}
