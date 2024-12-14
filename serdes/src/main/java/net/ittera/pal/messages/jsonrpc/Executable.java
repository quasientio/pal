package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import javax.annotation.Nullable;

public class Executable {
  @SerializedName("class")
  private String className;

  @SerializedName("method")
  private @Nullable String methodName;

  @SerializedName("field")
  private @Nullable String fieldName;

  private Integer modifiers;

  public Executable() {}

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    if (!className.isEmpty()) {
      this.className = className;
    }
  }

  public @Nullable String getMethodName() {
    return methodName;
  }

  public void setMethodName(@Nullable String methodName) {
    if (methodName != null && methodName.isEmpty()) {
      this.methodName = null;
      return;
    }
    this.methodName = methodName;
  }

  public @Nullable String getFieldName() {
    return fieldName;
  }

  public void setFieldName(@Nullable String fieldName) {
    if (fieldName != null && fieldName.isEmpty()) {
      this.fieldName = null;
      return;
    }
    this.fieldName = fieldName;
  }

  public Integer getModifiers() {
    return modifiers;
  }

  public void setModifiers(Integer modifiers) {
    this.modifiers = modifiers;
  }

  public boolean isConstructor() {
    return methodName == null && fieldName == null;
  }

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

  @Override
  public int hashCode() {
    return Objects.hash(className, methodName, fieldName, modifiers);
  }

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

  public static class Builder {
    private final Executable executable = new Executable();

    public Builder withClassName(String className) {
      executable.setClassName(className);
      return this;
    }

    public Builder withMethodName(@Nullable String methodName) {
      executable.setMethodName(methodName);
      return this;
    }

    public Builder withFieldName(@Nullable String fieldName) {
      executable.setFieldName(fieldName);
      return this;
    }

    public Builder withModifiers(Integer modifiers) {
      executable.setModifiers(modifiers);
      return this;
    }

    public Executable build() {
      if (executable.methodName != null && executable.fieldName != null) {
        throw new IllegalArgumentException("Executable cannot have both a method and a field");
      }
      return executable;
    }
  }
}
