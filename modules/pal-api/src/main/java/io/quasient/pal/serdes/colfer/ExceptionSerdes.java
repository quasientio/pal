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
package io.quasient.pal.serdes.colfer;

/**
 * Utility class for serializing and deserializing exceptions to/from Colfer format.
 *
 * <p>This class provides methods to convert Java {@link Throwable} instances to Colfer {@link
 * io.quasient.pal.messages.colfer.Throwable} and {@link
 * io.quasient.pal.messages.colfer.RaisedThrowable} representations, and vice versa.
 *
 * <p>The serialization preserves:
 *
 * <ul>
 *   <li>Exception type (class name)
 *   <li>Exception message
 *   <li>Stack trace elements
 *   <li>Exception cause (recursively)
 * </ul>
 *
 * <p>The deserialization attempts to reconstruct the original exception type when possible, falling
 * back to {@link RuntimeException} if the class cannot be loaded.
 */
public final class ExceptionSerdes {

  /** Private constructor to prevent instantiation. */
  private ExceptionSerdes() {}

  /**
   * Serializes a Java Throwable to Colfer RaisedThrowable format.
   *
   * @param throwable the exception to serialize
   * @return the serialized RaisedThrowable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static io.quasient.pal.messages.colfer.RaisedThrowable serializeException(
      Throwable throwable) {
    io.quasient.pal.messages.colfer.RaisedThrowable raised =
        new io.quasient.pal.messages.colfer.RaisedThrowable();

    if (throwable == null) {
      return raised;
    }

    raised.setThrowable(serializeThrowable(throwable));
    return raised;
  }

  /**
   * Serializes a Java Throwable to a Colfer Throwable.
   *
   * @param throwable the Java throwable
   * @return the Colfer throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static io.quasient.pal.messages.colfer.Throwable serializeThrowable(Throwable throwable) {
    if (throwable == null) {
      return null;
    }

    io.quasient.pal.messages.colfer.Throwable colferThrowable =
        new io.quasient.pal.messages.colfer.Throwable();

    colferThrowable.setType(throwable.getClass().getName());
    // getMessage() can return null, but Colfer serialization requires non-null message
    String message = throwable.getMessage();
    colferThrowable.setMessage(message != null ? message : "");

    // Serialize stack trace
    StackTraceElement[] stackTrace = throwable.getStackTrace();
    if (stackTrace.length > 0) {
      String[] stackTraceStrings = new String[stackTrace.length];
      for (int i = 0; i < stackTrace.length; i++) {
        stackTraceStrings[i] = stackTrace[i].toString();
      }
      colferThrowable.setStackTraceElements(stackTraceStrings);
    }

    // Recursively serialize the cause
    Throwable cause = throwable.getCause();
    if (cause != null && cause != throwable) { // Avoid infinite recursion
      colferThrowable.setCause(serializeThrowable(cause));
    }

    return colferThrowable;
  }

  /**
   * Deserializes a Colfer RaisedThrowable to a Java Throwable.
   *
   * @param raised the serialized exception
   * @return the deserialized Throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static Throwable deserializeException(
      io.quasient.pal.messages.colfer.RaisedThrowable raised) {
    if (raised == null) {
      return new RuntimeException("Unknown exception from callback");
    }

    return deserializeThrowable(raised.getThrowable());
  }

  /**
   * Deserializes a Colfer Throwable to a Java Throwable.
   *
   * <p>This method attempts to reconstruct the original exception type using reflection. It tries
   * multiple constructor signatures in order of preference:
   *
   * <ol>
   *   <li>{@code (String message, Throwable cause)} - preferred for full exception chain support
   *   <li>{@code (String message)} - with {@code initCause()} called if cause exists
   *   <li>Falls back to {@link RuntimeException} if the original type cannot be instantiated
   * </ol>
   *
   * @param colferThrowable the Colfer throwable
   * @return the Java throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static Throwable deserializeThrowable(
      io.quasient.pal.messages.colfer.Throwable colferThrowable) {
    if (colferThrowable == null) {
      return new RuntimeException("Unknown exception from callback");
    }

    String type = colferThrowable.getType();
    String message = colferThrowable.getMessage();
    io.quasient.pal.messages.colfer.Throwable colferCause = colferThrowable.getCause();

    // Recursively deserialize the cause
    Throwable cause = (colferCause != null) ? deserializeThrowable(colferCause) : null;

    // Try to reconstruct the original exception type
    Throwable exception = tryInstantiateException(type, message, cause);

    // Reconstruct stack trace if available
    String[] stackTraceElements = colferThrowable.getStackTraceElements();
    if (stackTraceElements != null && stackTraceElements.length > 0) {
      StackTraceElement[] javaStackTrace = new StackTraceElement[stackTraceElements.length];
      for (int i = 0; i < stackTraceElements.length; i++) {
        // Parse stack trace element string (format: "className.methodName(fileName:lineNumber)")
        String element = stackTraceElements[i];
        try {
          // For now, create a minimal stack trace element
          // Full parsing would require more complex string manipulation
          javaStackTrace[i] = new StackTraceElement("", element, null, 0);
        } catch (Exception e) {
          // Skip malformed stack trace elements
          javaStackTrace[i] = new StackTraceElement("", "unknown", null, 0);
        }
      }
      exception.setStackTrace(javaStackTrace);
    }

    return exception;
  }

  /**
   * Attempts to instantiate an exception of the given type with the specified message and cause.
   *
   * @param type the fully qualified class name of the exception
   * @param message the exception message
   * @param cause the exception cause (may be null)
   * @return the instantiated exception, or a RuntimeException if instantiation fails
   */
  private static Throwable tryInstantiateException(String type, String message, Throwable cause) {
    if (type == null || type.isEmpty()) {
      return new RuntimeException(message, cause);
    }

    try {
      // Use context classloader for better compatibility
      Class<?> exceptionClass =
          Class.forName(type, true, Thread.currentThread().getContextClassLoader());

      if (!Throwable.class.isAssignableFrom(exceptionClass)) {
        return new RuntimeException(type + ": " + message, cause);
      }

      // Try constructor with (String, Throwable)
      Throwable result = tryConstructor(exceptionClass, message, cause);
      if (result != null) {
        return result;
      }

      // Try constructor with (String) + initCause
      result = tryStringConstructor(exceptionClass, message, cause);
      if (result != null) {
        return result;
      }

      // Fallback to RuntimeException
      return new RuntimeException(type + ": " + message, cause);

    } catch (ClassNotFoundException e) {
      // Class not available, fallback to RuntimeException
      return new RuntimeException(type + ": " + message, cause);
    }
  }

  /**
   * Tries to instantiate an exception using the (String, Throwable) constructor.
   *
   * @param exceptionClass the exception class
   * @param message the exception message
   * @param cause the exception cause
   * @return the instantiated exception, or null if this constructor doesn't work
   */
  private static Throwable tryConstructor(
      Class<?> exceptionClass, String message, Throwable cause) {
    try {
      return (Throwable)
          exceptionClass.getConstructor(String.class, Throwable.class).newInstance(message, cause);
    } catch (ReflectiveOperationException e) {
      // Constructor not available or instantiation failed
      return null;
    }
  }

  /**
   * Tries to instantiate an exception using the (String) constructor and initCause.
   *
   * @param exceptionClass the exception class
   * @param message the exception message
   * @param cause the exception cause (may be null)
   * @return the instantiated exception, or null if this constructor doesn't work
   */
  private static Throwable tryStringConstructor(
      Class<?> exceptionClass, String message, Throwable cause) {
    try {
      Throwable exception =
          (Throwable) exceptionClass.getConstructor(String.class).newInstance(message);
      if (cause != null) {
        exception.initCause(cause);
      }
      return exception;
    } catch (ReflectiveOperationException e) {
      // Constructor not available or instantiation failed
      return null;
    }
  }
}
