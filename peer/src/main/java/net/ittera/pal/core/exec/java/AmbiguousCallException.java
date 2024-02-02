package net.ittera.pal.core.exec.java;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;

public class AmbiguousCallException extends Exception {

  private final String className;
  private final String methodName;
  private final List<Class<?>> parameterTypesToMatch;
  private final List<? extends Executable> matchingExecutables;

  /** Used for method calls */
  public AmbiguousCallException(
      String className,
      String methodName,
      List<Class<?>> parameterTypesToMatch,
      List<? extends Executable> matchingExecutables) {
    this.className = className;
    this.methodName = methodName;
    this.parameterTypesToMatch = parameterTypesToMatch;
    this.matchingExecutables = matchingExecutables;
  }

  /** Used for constructor calls */
  public AmbiguousCallException(
      String className,
      List<Class<?>> parameterTypesToMatch,
      List<? extends Executable> matchingExecutables) {
    this(className, "new", parameterTypesToMatch, matchingExecutables);
  }

  @Override
  public String toString() {
    return getMessage();
  }

  public List<? extends Executable> getMatchingExecutables() {
    return matchingExecutables;
  }

  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Ambiguous call: multiple matches found for \"%s.%s\":%n", className, methodName));
    int idx = 0;
    for (Executable executable : matchingExecutables) {
      Class<?>[] parameterTypes = executable.getParameterTypes();
      sb.append("  ");
      sb.append(methodName);
      sb.append("(");
      sb.append(
          String.join(
              ", ",
              Arrays.asList(parameterTypes).stream().map(p -> p.getName()).toArray(String[]::new)));
      sb.append(")%n");
    }
    sb.append("which can be assigned the given types: ");
    sb.append(parameterTypesToMatch);
    return String.format(sb.toString());
  }
}
