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

import com.google.gson.annotations.SerializedName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import javax.annotation.Nullable;

/** Represents an executable element within the system, such as a constructor, method, or field. */
public class Executable {
  /** The name of the class containing the executable. */
  @SerializedName("class")
  private String className;

  /** The name of the method, if this executable represents a method. */
  @SerializedName("method")
  private @Nullable String methodName;

  /** The name of the field, if this executable represents a field. */
  @SerializedName("field")
  private @Nullable String fieldName;

  /** The modifiers associated with the executable, such as access level and other modifiers. */
  private Integer modifiers;

  /** Constructs a new Executable instance with default values. */
  public Executable() {}

  /**
   * Retrieves the name of the class associated with this executable.
   *
   * @return the class name.
   */
  public String getClassName() {
    return className;
  }

  /**
   * Sets the name of the class for this executable.
   *
   * @param className the class name to set; must not be empty.
   */
  public void setClassName(String className) {
    if (className != null && !className.isEmpty()) {
      this.className = className;
    }
  }

  /**
   * Retrieves the name of the method if this executable represents a method.
   *
   * @return the method name, or {@code null} if not applicable.
   */
  public @Nullable String getMethodName() {
    return methodName;
  }

  /**
   * Sets the name of the method for this executable.
   *
   * @param methodName the method name to set; if empty, it will be set to {@code null}.
   */
  public void setMethodName(@Nullable String methodName) {
    if (methodName != null && methodName.isEmpty()) {
      this.methodName = null;
      return;
    }
    this.methodName = methodName;
  }

  /**
   * Retrieves the name of the field if this executable represents a field.
   *
   * @return the field name, or {@code null} if not applicable.
   */
  public @Nullable String getFieldName() {
    return fieldName;
  }

  /**
   * Sets the name of the field for this executable.
   *
   * @param fieldName the field name to set; if empty, it will be set to {@code null}.
   */
  public void setFieldName(@Nullable String fieldName) {
    if (fieldName != null && fieldName.isEmpty()) {
      this.fieldName = null;
      return;
    }
    this.fieldName = fieldName;
  }

  /**
   * Retrieves the modifiers associated with this executable.
   *
   * @return the modifiers as an {@code Integer}.
   */
  public Integer getModifiers() {
    return modifiers;
  }

  /**
   * Sets the modifiers for this executable.
   *
   * @param modifiers the modifiers to set.
   */
  public void setModifiers(Integer modifiers) {
    this.modifiers = modifiers;
  }

  /**
   * Determines whether this executable represents a constructor.
   *
   * @return {@code true} if both method and field names are {@code null}, indicating a constructor.
   */
  public boolean isConstructor() {
    return methodName == null && fieldName == null;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Executable that)) {
      return false;
    }
    return Objects.equals(className, that.className)
        && Objects.equals(methodName, that.methodName)
        && Objects.equals(fieldName, that.fieldName)
        && Objects.equals(modifiers, that.modifiers);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(className, methodName, fieldName, modifiers);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "Executable{"
        + "class='"
        + className
        + '\''
        + ", isConstructor="
        + isConstructor()
        + ", method='"
        + methodName
        + '\''
        + ", field='"
        + fieldName
        + '\''
        + ", modifiers="
        + modifiers
        + '}';
  }

  /**
   * Creates a new {@link Builder} instance for constructing {@link Executable} objects.
   *
   * @return a new Builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for {@link Executable}, providing a fluent API for setting properties. */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Builder pattern - returns the built object intentionally")
  public static class Builder {

    /** Instance of {@link Executable} which will be returned. */
    private final Executable executable = new Executable();

    /**
     * Sets the class name for the {@link Executable} being built.
     *
     * @param className the name of the class.
     * @return the current Builder instance.
     */
    public Builder withClassName(String className) {
      executable.setClassName(className);
      return this;
    }

    /**
     * Sets the method name for the {@link Executable} being built.
     *
     * @param methodName the name of the method, or {@code null} if not applicable.
     * @return the current Builder instance.
     */
    public Builder withMethodName(@Nullable String methodName) {
      executable.setMethodName(methodName);
      return this;
    }

    /**
     * Sets the field name for the {@link Executable} being built.
     *
     * @param fieldName the name of the field, or {@code null} if not applicable.
     * @return the current Builder instance.
     */
    public Builder withFieldName(@Nullable String fieldName) {
      executable.setFieldName(fieldName);
      return this;
    }

    /**
     * Sets the modifiers for the {@link Executable} being built.
     *
     * @param modifiers the modifiers to set.
     * @return the current Builder instance.
     */
    public Builder withModifiers(Integer modifiers) {
      executable.setModifiers(modifiers);
      return this;
    }

    /**
     * Builds the {@link Executable} instance with the configured properties.
     *
     * @return the constructed Executable.
     * @throws IllegalArgumentException if both method and field names are set.
     */
    public Executable build() {
      if (executable.methodName != null && executable.fieldName != null) {
        throw new IllegalArgumentException("Executable cannot have both a method and a field");
      }
      return executable;
    }
  }
}
