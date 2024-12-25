package net.ittera.pal.core.rpc.exec.java;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.List;

public class AmbiguousCallException extends Exception {

  private final String className;
  private final String methodName;
  private final List<Class<?>> parameterTypesToMatch;
  private final List<? extends Executable> matchingExecutables;

  /**
   * Used for method calls.
   *
   * @param className the class name of the object that the method is called on
   * @param methodName the name of the method that is called
   * @param parameterTypesToMatch the types of the parameters that the method is called with
   * @param matchingExecutables the methods that match the given parameters
   */
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

  /**
   * Used for constructor calls.
   *
   * @param className the class name of the constructor that is called
   * @param parameterTypesToMatch the types of the parameters that the constructor is called with
   * @param matchingExecutables the constructors that match the given parameters
   */
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

  @SuppressWarnings("unused")
  public List<? extends Executable> getMatchingExecutables() {
    return matchingExecutables;
  }

  @Override
  public String getMessage() {
    StringBuilder sb = new StringBuilder();
    sb.append(
        String.format(
            "Ambiguous call: multiple matches found for \"%s.%s\":%n", className, methodName));
    for (Executable executable : matchingExecutables) {
      Class<?>[] parameterTypes = executable.getParameterTypes();
      sb.append("  ");
      sb.append(methodName);
      sb.append("(");
      sb.append(
          String.join(
              ", ", Arrays.stream(parameterTypes).map(Class::getName).toArray(String[]::new)));
      sb.append(")%n");
    }
    sb.append("which can be assigned the given types: ");
    sb.append(parameterTypesToMatch);
    return String.format(sb.toString());
  }
}
