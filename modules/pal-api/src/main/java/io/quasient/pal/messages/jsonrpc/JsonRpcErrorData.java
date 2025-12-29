/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents error information in a JSON-RPC response.
 *
 * <p>This class encapsulates details about an error that occurred during the processing of a
 * JSON-RPC request, including the message, type of the throwable, stack trace, cause, associated
 * request ID, and the origin of the error. It supports nested error causes and can be
 * serialized/deserialized using Gson.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "JSON-RPC DTO - mutable by design for serialization")
public class JsonRpcErrorData {

  /** Constructs a new instance of {@code JsonRpcErrorData} with default values. */
  public JsonRpcErrorData() {}

  /** The type of the throwable that caused the error. */
  @SerializedName("throwable_type")
  @Nullable
  private String throwableType;

  /** The error message describing what went wrong. */
  private String message;

  /** The stack trace elements of the throwable as an array of strings. */
  @SerializedName("stack_trace")
  @Nullable
  private String[] stackTrace;

  /** The underlying cause of this error, if any. */
  @Nullable private JsonRpcErrorData cause;

  /** The ID of the JSON-RPC request associated with this error. */
  @Nullable private String requestId;

  /** The origin of the error, indicating where it was generated. */
  private Executable from;

  /**
   * Retrieves the underlying cause of this error.
   *
   * @return the cause of this error, or {@code null} if there is none.
   */
  @Nullable
  public JsonRpcErrorData getCause() {
    return cause;
  }

  /**
   * Sets the underlying cause of this error.
   *
   * @param cause the cause to set, or {@code null} if there is no underlying cause.
   */
  public void setCause(@Nullable JsonRpcErrorData cause) {
    this.cause = cause;
  }

  /**
   * Retrieves the error message.
   *
   * @return the error message.
   */
  public String getMessage() {
    return message;
  }

  /**
   * Sets the error message.
   *
   * @param message the error message to set.
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Retrieves the stack trace of the throwable.
   *
   * @return an array of stack trace elements, or {@code null} if not available.
   */
  public String[] getStackTrace() {
    return stackTrace;
  }

  /**
   * Sets the stack trace of the throwable.
   *
   * @param stackTrace an array of stack trace elements, or {@code null} if not available.
   */
  public void setStackTrace(@Nullable String[] stackTrace) {
    this.stackTrace = stackTrace;
  }

  /**
   * Retrieves the type of the throwable.
   *
   * @return the throwable type, or {@code null} if not specified.
   */
  @Nullable
  public String getThrowableType() {
    return throwableType;
  }

  /**
   * Sets the type of the throwable.
   *
   * @param throwableType the throwable type to set, or {@code null} if not specified.
   */
  public void setThrowableType(@Nullable String throwableType) {
    this.throwableType = throwableType;
  }

  /**
   * Retrieves the request ID associated with this error.
   *
   * @return the request ID, or {@code null} if not associated with any request.
   */
  @Nullable
  public String getRequestId() {
    return requestId;
  }

  /**
   * Sets the request ID associated with this error.
   *
   * @param requestId the request ID to set, or {@code null} if not associated with any request.
   */
  public void setRequestId(@Nullable String requestId) {
    this.requestId = requestId;
  }

  /**
   * Retrieves the origin of this error.
   *
   * @return the origin of the error.
   */
  public Executable getFrom() {
    return from;
  }

  /**
   * Sets the origin of this error.
   *
   * @param from the executable where the error originated.
   */
  public void setFrom(Executable from) {
    this.from = from;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRpcErrorData that)) {
      return false;
    }
    return Objects.equals(throwableType, that.throwableType)
        && Objects.equals(message, that.message)
        && Objects.equals(requestId, that.requestId)
        && Objects.deepEquals(stackTrace, that.stackTrace)
        && Objects.equals(cause, that.cause);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(throwableType, message, requestId, Arrays.hashCode(stackTrace), cause);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "JsonRpcErrorData{"
        + "cause="
        + cause
        + ", throwableType='"
        + throwableType
        + '\''
        + ", message='"
        + message
        + '\''
        + ", from="
        + from
        + ", requestId="
        + requestId
        + ", stackTrace="
        + Arrays.deepToString(stackTrace)
        + '}';
  }

  /**
   * Creates a new {@link Builder} instance for constructing {@code JsonRpcErrorData} objects.
   *
   * @return a new {@code Builder}.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** A builder for constructing {@link JsonRpcErrorData} instances. */
  public static class Builder {

    /** Instance of {@link JsonRpcErrorData} which will be returned. */
    private final JsonRpcErrorData errorData = new JsonRpcErrorData();

    /**
     * Sets the type of the throwable.
     *
     * @param throwableType the throwable type.
     * @return the current builder instance.
     */
    public Builder withThrowableType(String throwableType) {
      errorData.setThrowableType(throwableType);
      return this;
    }

    /**
     * Sets the error message.
     *
     * @param message the error message.
     * @return the current builder instance.
     */
    public Builder withMessage(String message) {
      errorData.setMessage(message);
      return this;
    }

    /**
     * Sets the origin of the error.
     *
     * @param from the executable where the error originated.
     * @return the current builder instance.
     */
    public Builder withFrom(Executable from) {
      errorData.setFrom(from);
      return this;
    }

    /**
     * Sets the request ID associated with the error.
     *
     * @param requestId the request ID.
     * @return the current builder instance.
     */
    public Builder withRequestId(String requestId) {
      errorData.setRequestId(requestId);
      return this;
    }

    /**
     * Sets the stack trace of the throwable.
     *
     * @param stackTrace the stack trace elements.
     * @return the current builder instance.
     */
    public Builder withStackTrace(String[] stackTrace) {
      errorData.setStackTrace(stackTrace);
      return this;
    }

    /**
     * Sets the underlying cause of the error.
     *
     * @param cause the cause of the error, or {@code null} if there is none.
     * @return the current builder instance.
     */
    public Builder withCause(@Nullable JsonRpcErrorData cause) {
      errorData.setCause(cause);
      return this;
    }

    /**
     * Builds and returns the {@code JsonRpcErrorData} instance.
     *
     * @return the constructed {@code JsonRpcErrorData}.
     */
    public JsonRpcErrorData build() {
      return errorData;
    }
  }
}
