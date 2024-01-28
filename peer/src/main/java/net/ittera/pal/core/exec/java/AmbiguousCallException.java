package net.ittera.pal.core.exec.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class AmbiguousCallException extends Exception {

  private final String className;
  private String methodName;
  private final List<String> parameterTypesToMatch;
  private List<Method> matchingMethods;
  private List<Constructor<?>> matchingConstructors;

  public AmbiguousCallException(
      String className,
      String methodName,
      List<String> parameterTypesToMatch,
      List<Method> matchingMethods) {
    this.className = className;
    this.methodName = methodName;
    this.parameterTypesToMatch = parameterTypesToMatch;
    this.matchingMethods = matchingMethods;
  }

  public AmbiguousCallException(
      String className,
      List<String> parameterTypesToMatch,
      List<Constructor<?>> matchingConstructors) {
    this.className = className;
    this.parameterTypesToMatch = parameterTypesToMatch;
    this.matchingConstructors = matchingConstructors;
  }

  @Override
  public String toString() {
    return getMessage();
  }

  private String getMethodMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Ambiguous call: multiple methods found for \"%s.%s\":%n", className, methodName));
    int idx = 0;
    for (Method method : matchingMethods) {
      Class[] parameterTypes = method.getParameterTypes();
      sb.append("  ");
      sb.append(methodName);
      sb.append("(");
      Arrays.stream(parameterTypes).forEach(p -> sb.append(p.getName()));
      if (idx++ < parameterTypes.length - 1) {
        sb.append(", ");
      }
      sb.append(")%n");
    }
    sb.append("which can be assigned the given types: ");
    sb.append(parameterTypesToMatch);
    return String.format(sb.toString());
  }

  private String getConstructorMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format("Ambiguous call: multiple constructors found for \"%s\":%n", className));
    int idx = 0;
    for (Constructor<?> constructor : matchingConstructors) {
      Class[] parameterTypes = constructor.getParameterTypes();
      sb.append("  ");
      sb.append(className);
      sb.append("(");
      Arrays.stream(parameterTypes).forEach(p -> sb.append(p.getName()));
      if (idx++ < parameterTypes.length - 1) {
        sb.append(", ");
      }
      sb.append(")%n");
    }
    sb.append("which can be assigned the given types: ");
    sb.append(parameterTypesToMatch);
    return String.format(sb.toString());
  }

  public String getMessage() {
    if (methodName == null) {
      return getConstructorMessage();
    } else {
      return getMethodMessage();
    }
  }
}
