package com.quasient.pal.messages.jsonrpc;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents an error in the JSON-RPC protocol. This class encapsulates error information including
 * a code, a message, and optional additional error data in the form of a {@link JsonRpcErrorData}.
 * It is used to communicate errors between a JSON-RPC server and client.
 */
public class JsonRpcError {

  /** The error code indicating the type of error. */
  private int code;

  /** A descriptive message providing more details about the error. */
  private String message;

  /**
   * Additional error data providing further context or information. This field is optional and may
   * be {@code null}.
   */
  @Nullable private JsonRpcErrorData data;

  /** Constructs a new {@code JsonRpcError} with default values. */
  public JsonRpcError() {}

  /**
   * Constructs a new {@code JsonRpcError} with the specified code and message.
   *
   * @param code the error code representing the type of error
   * @param message a descriptive message providing details about the error
   */
  public JsonRpcError(int code, String message) {
    this.code = code;
    this.message = message;
  }

  /**
   * Constructs a new {@code JsonRpcError} with the specified code, message, and additional data.
   *
   * @param code the error code representing the type of error
   * @param message a descriptive message providing details about the error
   * @param data additional error data providing further context or information, may be {@code null}
   */
  public JsonRpcError(int code, String message, @Nullable JsonRpcErrorData data) {
    this.code = code;
    this.message = message;
    this.data = data;
  }

  /**
   * Retrieves the error code.
   *
   * @return the error code indicating the type of error
   */
  public int getCode() {
    return code;
  }

  /**
   * Sets the error code.
   *
   * @param code the error code indicating the type of error
   */
  public void setCode(int code) {
    this.code = code;
  }

  /**
   * Retrieves the error message.
   *
   * @return the descriptive message providing details about the error
   */
  public String getMessage() {
    return message;
  }

  /**
   * Sets the error message.
   *
   * @param message the descriptive message providing details about the error
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Retrieves the additional error data.
   *
   * @return the additional error data providing further context or information, or {@code null} if
   *     not present
   */
  @Nullable
  public JsonRpcErrorData getData() {
    return data;
  }

  /**
   * Sets the additional error data.
   *
   * @param data the additional error data providing further context or information, may be {@code
   *     null}
   */
  public void setData(@Nullable JsonRpcErrorData data) {
    this.data = data;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcError that)) {
      return false;
    }
    return code == that.code
        && Objects.equals(message, that.message)
        && Objects.equals(data, that.data);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(code, message, data);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonRpcError{"
        + "code="
        + code
        + ", message='"
        + message
        + '\''
        + ", data="
        + data
        + '}';
  }

  /**
   * Creates a new {@link Builder} for constructing instances of {@code JsonRpcError}.
   *
   * @return a new {@code Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder for creating instances of {@link JsonRpcError}. Provides a fluent interface for
   * setting error properties.
   */
  public static class Builder {
    private final JsonRpcError error = new JsonRpcError();

    /**
     * Sets the error code for the {@code JsonRpcError}.
     *
     * @param code the error code indicating the type of error
     * @return the current {@code Builder} instance for chaining
     */
    public Builder withCode(int code) {
      error.setCode(code);
      return this;
    }

    /**
     * Sets the error message for the {@code JsonRpcError}.
     *
     * @param message the descriptive message providing details about the error
     * @return the current {@code Builder} instance for chaining
     */
    public Builder withMessage(String message) {
      error.setMessage(message);
      return this;
    }

    /**
     * Sets the additional error data for the {@code JsonRpcError}.
     *
     * @param data the additional error data providing further context or information, may be {@code
     *     null}
     * @return the current {@code Builder} instance for chaining
     */
    public Builder withData(@Nullable JsonRpcErrorData data) {
      error.setData(data);
      return this;
    }

    /**
     * Builds and returns the configured {@code JsonRpcError} instance.
     *
     * @return the constructed {@code JsonRpcError} instance
     */
    public JsonRpcError build() {
      return error;
    }
  }
}
