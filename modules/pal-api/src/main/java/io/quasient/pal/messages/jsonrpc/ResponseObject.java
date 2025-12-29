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
import io.quasient.pal.serdes.Unwrappable;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents a response object in the JSON-RPC system. Encapsulates information such as the type,
 * nullability, value, and object reference.
 */
public class ResponseObject implements Unwrappable {

  /** The type of the response object. May be null if the type is unspecified. */
  @Nullable private String type;

  /** Indicates whether the response is null. Serialized as "null" in JSON. */
  @SerializedName("null")
  private Boolean isNull = Boolean.FALSE;

  /** The value contained in the response. May be null if no value is present. */
  @Nullable private String value;

  /** An object reference identifier. May be null if no reference is specified. */
  @Nullable private Integer ref;

  /** Constructs an empty {@code ResponseObject}. All fields are initialized to null. */
  public ResponseObject() {}

  /**
   * Retrieves the null status of the response.
   *
   * @return {@code true} if the response is null, {@code false} otherwise.
   */
  public boolean getIsNull() {
    return isNull;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isNull() {
    return isNull;
  }

  /**
   * Sets the null status of the response.
   *
   * @param isNull {@code true} if the response should be marked as null, {@code false} otherwise.
   */
  public void setIsNull(boolean isNull) {
    this.isNull = isNull;
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  public Integer getRef() {
    return ref;
  }

  /**
   * Sets the object reference identifier for the response.
   *
   * @param ref the object ref identifier, or {@code null} if no reference is to be set.
   */
  public void setRef(@Nullable Integer ref) {
    this.ref = ref;
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  public String getType() {
    return type;
  }

  /**
   * Sets the type of the response.
   *
   * @param type the type identifier, or {@code null} if the type is unspecified.
   */
  public void setType(@Nullable String type) {
    this.type = type;
  }

  /** {@inheritDoc} */
  @Nullable
  @Override
  public String getValue() {
    return value;
  }

  /**
   * Sets the value contained in the response.
   *
   * @param value the value to set, or {@code null} if no value is present.
   */
  public void setValue(@Nullable String value) {
    this.value = value;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(type, isNull, value, ref);
  }

  /** {@inheritDoc} */
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

  /**
   * Creates a new {@code Builder} instance for constructing a {@code ResponseObject}.
   *
   * @return a new {@code Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing instances of {@code ResponseObject}. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Builder pattern - returns the built object intentionally")
  public static class Builder {

    /** Instance of {@link ResponseObject} which will be returned. */
    private final ResponseObject responseObject = new ResponseObject();

    /**
     * Sets the type for the {@code ResponseObject} being built.
     *
     * @param type the type identifier, or {@code null} if the type is unspecified.
     * @return this builder instance.
     */
    public Builder withType(@Nullable String type) {
      responseObject.setType(type);
      return this;
    }

    /**
     * Sets the null status for the {@code ResponseObject} being built.
     *
     * @param isNull {@code true} if the response should be marked as null, {@code false} otherwise.
     * @return this builder instance.
     */
    public Builder withIsNull(boolean isNull) {
      responseObject.setIsNull(isNull);
      return this;
    }

    /**
     * Sets the value for the {@code ResponseObject} being built.
     *
     * @param value the value to set, or {@code null} if no value is present.
     * @return this builder instance.
     */
    public Builder withValue(@Nullable String value) {
      responseObject.setValue(value);
      return this;
    }

    /**
     * Sets the object reference identifier for the {@code ResponseObject} being built.
     *
     * @param ref the object ref identifier, or {@code null} if no reference is to be set.
     * @return this builder instance.
     */
    public Builder withRef(@Nullable Integer ref) {
      responseObject.setRef(ref);
      return this;
    }

    /**
     * Builds and returns the {@code ResponseObject} instance.
     *
     * @return the constructed {@code ResponseObject}.
     */
    public ResponseObject build() {
      return responseObject;
    }
  }
}
