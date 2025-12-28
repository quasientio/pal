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

import com.quasient.pal.common.objects.ObjectRef;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents an argument in a JSON-RPC request.
 *
 * <p>This class is deserialized using the custom {@link
 * com.quasient.pal.serdes.jsonrpc.ParamsDeserializer ParamsDeserializer} adapter. It encapsulates
 * the value, reference, type, and name of an argument.
 */
public class Argument {
  /** A constant representing a null argument where both value and reference are {@code null}. */
  public static final Argument NULL = new Argument();

  /** The value of the argument. May be {@code null} if the argument is a reference. */
  @Nullable private Object value;

  /** The reference ID of the argument. May be {@code null} if the argument holds a value. */
  @Nullable private Integer ref;

  /** The type of the argument. Represents the data type of the value. */
  @Nullable private String type;

  /** The name of the argument. Can be used to identify the argument. */
  @Nullable private String name;

  /** Constructs a new {@code Argument} with all fields set to {@code null}. */
  public Argument() {}

  /**
   * Constructs a new {@code Argument} with the specified value and type.
   *
   * @param value the value of the argument, may be {@code null}
   * @param type the type of the argument, may be {@code null}
   */
  public Argument(@Nullable Object value, @Nullable String type) {
    this.value = value;
    this.type = type;
  }

  /**
   * Constructs a new {@code Argument} with the specified reference ID.
   *
   * @param ref the reference ID of the argument, may be {@code null}
   */
  public Argument(@Nullable Integer ref) {
    this.ref = ref;
  }

  /**
   * Retrieves the value of the argument.
   *
   * @return the value of the argument, or {@code null} if it is a reference
   */
  @Nullable
  public Object getValue() {
    return value;
  }

  /**
   * Sets the value of the argument.
   *
   * @param value the value to set, may be {@code null}
   */
  public void setValue(@Nullable Object value) {
    this.value = value;
  }

  /**
   * Retrieves the reference ID of the argument.
   *
   * @return the reference ID of the argument, or {@code null} if it holds a value
   */
  @Nullable
  public Integer getRef() {
    return ref;
  }

  /**
   * Sets the reference ID of the argument.
   *
   * @param ref the reference ID to set, may be {@code null}
   */
  public void setRef(@Nullable Integer ref) {
    this.ref = ref;
  }

  /**
   * Sets the reference ID of the argument based on an {@link ObjectRef}.
   *
   * @param ref the {@code ObjectRef} to extract the reference ID from, may be {@code null}
   */
  public void setRef(@Nullable ObjectRef ref) {
    if (ref == null) {
      this.ref = null;
    } else {
      this.ref = ref.getRef();
    }
  }

  /**
   * Retrieves the type of the argument.
   *
   * @return the type of the argument, or {@code null} if not specified
   */
  @Nullable
  public String getType() {
    return type;
  }

  /**
   * Sets the type of the argument.
   *
   * @param type the type to set, may be {@code null}
   */
  public void setType(@Nullable String type) {
    this.type = type;
  }

  /**
   * Retrieves the name of the argument.
   *
   * @return the name of the argument, or {@code null} if not specified
   */
  @Nullable
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the argument.
   *
   * @param name the name to set, may be {@code null}
   */
  public void setName(@Nullable String name) {
    this.name = name;
  }

  /**
   * Checks whether the argument is null, meaning both value and reference are {@code null}.
   *
   * @return {@code true} if both value and reference are {@code null}, {@code false} otherwise
   */
  public boolean isNull() {
    return value == null && ref == null;
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(value, ref, type, name);
  }

  /** {@inheritDoc} */
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

  /**
   * Creates a new {@link Builder} for constructing an {@code Argument}.
   *
   * @return a new {@code Builder} instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing {@link Argument} instances. */
  @SuppressFBWarnings(
      value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
      justification = "JSON-RPC DTO - mutable by design for serialization")
  public static class Builder {
    /** The {@code Argument} instance being built. */
    private final Argument argument = new Argument();

    /**
     * Sets the value of the argument being built.
     *
     * @param value the value to set, may be {@code null}
     * @return the current {@code Builder} instance
     */
    public Builder withValue(@Nullable Object value) {
      argument.setValue(value);
      return this;
    }

    /**
     * Sets the reference ID of the argument being built.
     *
     * @param ref the reference ID to set, may be {@code null}
     * @return the current {@code Builder} instance
     */
    public Builder withRef(@Nullable Integer ref) {
      argument.setRef(ref);
      return this;
    }

    /**
     * Sets the reference ID of the argument being built using an {@link ObjectRef}.
     *
     * @param ref the {@code ObjectRef} to extract the reference ID from, may be {@code null}
     * @return the current {@code Builder} instance
     */
    public Builder withRef(@Nullable ObjectRef ref) {
      argument.setRef(ref);
      return this;
    }

    /**
     * Sets the type of the argument being built.
     *
     * @param type the type to set, may be {@code null}
     * @return the current {@code Builder} instance
     */
    public Builder withType(@Nullable String type) {
      argument.setType(type);
      return this;
    }

    /**
     * Sets the name of the argument being built.
     *
     * @param name the name to set, may be {@code null}
     * @return the current {@code Builder} instance
     */
    public Builder withName(@Nullable String name) {
      argument.setName(name);
      return this;
    }

    /**
     * Builds and returns the {@code Argument} instance.
     *
     * @return the constructed {@code Argument}
     */
    public Argument build() {
      return argument;
    }
  }
}
