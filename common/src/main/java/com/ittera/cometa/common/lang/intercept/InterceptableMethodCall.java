package com.ittera.cometa.common.lang.intercept;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class InterceptableMethodCall extends Interceptable {

  private final List<String> parameterTypes;
  private static final String FIELD_SEP = "&&";

  public InterceptableMethodCall(String name, List<String> parameterTypes) {
    super(name, InterceptableType.METHOD_CALL);
    if (parameterTypes == null) {
      this.parameterTypes = Collections.emptyList();
    } else {
      this.parameterTypes = parameterTypes;
    }
  }

  public List<String> getParameterTypes() {
    return parameterTypes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptableMethodCall that = (InterceptableMethodCall) o;
    return name.equalsIgnoreCase(that.name) && Objects.equals(parameterTypes, that.parameterTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, parameterTypes);
  }

  @Override
  public String toString() {
    return "InterceptableMethodCall{"
        + "parameterTypes="
        + parameterTypes
        + ", name='"
        + name
        + '\''
        + '}';
  }

  @Override
  public String toSerializedString() {
    return format("%s" + FIELD_SEP + "%s", name, String.join(FIELD_SEP, parameterTypes));
  }

  public static InterceptableMethodCall fromSerializedString(String serialized) {
    final String[] parts = serialized.split(FIELD_SEP);
    final String name = parts[0];
    final List<String> paramTypes = new ArrayList<>(Arrays.asList(parts).subList(1, parts.length));
    return new InterceptableMethodCall(name, paramTypes);
  }
}
