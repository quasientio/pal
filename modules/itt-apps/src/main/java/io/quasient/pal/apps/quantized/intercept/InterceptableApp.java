/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks;
import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture application for interception integration tests.
 *
 * <p>Provides methods and fields for testing all interceptable message types: instance methods,
 * static methods, constructors, instance field operations, and static field operations.
 */
@SuppressWarnings("unused")
@SuppressFBWarnings(
    value = "CT_CONSTRUCTOR_THROW",
    justification = "Test app - constructor exception part of test scenario")
public class InterceptableApp {
  /** Instance field for testing EXEC_GET_FIELD and EXEC_PUT_FIELD interception. */
  volatile Integer counter = 1;

  /** Static field for testing EXEC_GET_STATIC and EXEC_PUT_STATIC interception. */
  static volatile Integer staticCounter = 100;

  /** Default constructor. */
  public InterceptableApp() {}

  /**
   * Parameterized constructor for testing EXEC_CONSTRUCTOR interception.
   *
   * @param initialCounter initial value for the counter field
   */
  public InterceptableApp(Integer initialCounter) {
    this.counter = initialCounter;
  }

  /**
   * Instance method for testing EXEC_INSTANCE_METHOD interception.
   *
   * @param multiple factor to multiply counter by
   */
  public void multiplyBy(Integer multiple) {
    int newValue = counter * multiple;
    counter = newValue;
  }

  /**
   * Instance method that invokes another instance method multiple times.
   *
   * @param n number of times to invoke multiplyBy
   * @param factor factor to pass to multiplyBy
   */
  public void multiplyCounterNTimesBy(Integer n, Integer factor) {
    for (int i = 0; i < n; i++) {
      multiplyBy(factor);
    }
  }

  /**
   * Gets the instance counter value, triggering EXEC_GET_FIELD.
   *
   * @return current counter value
   */
  public Integer getCounter() {
    return counter;
  }

  /**
   * Sets the instance counter value, triggering EXEC_PUT_FIELD.
   *
   * @param value new counter value
   */
  public void setCounter(Integer value) {
    counter = value;
  }

  /**
   * Thread-safe static method that returns its argument unchanged.
   *
   * <p>This method is designed for concurrent callback testing where the expected return value must
   * be deterministic. Unlike {@link #multiplyStaticBy(Integer)}, this method does not mutate shared
   * state and is safe for concurrent invocation.
   *
   * @param value the value to return
   * @return the same value passed in
   */
  public static Integer echoInteger(Integer value) {
    return value;
  }

  /**
   * Wrapper method that calls the static echoInteger method.
   *
   * <p>Used for testing static method interception via call-site weaving. The static method call
   * within this instance method is the intercepted call site.
   *
   * @param value the value to echo
   * @return the echoed value
   */
  public Integer callEchoInteger(Integer value) {
    return echoInteger(value); // <-- Static method call site for interception
  }

  /**
   * Static method for testing EXEC_CLASS_METHOD interception.
   *
   * @param multiple factor to multiply static counter by
   * @return new value of static counter
   */
  public static Integer multiplyStaticBy(Integer multiple) {
    int newValue = staticCounter * multiple;
    staticCounter = newValue;
    return staticCounter;
  }

  /**
   * Static method that increments the static counter.
   *
   * @return new value of static counter after increment
   */
  public static Integer incrementStaticCounter() {
    staticCounter = staticCounter + 1;
    return staticCounter;
  }

  /**
   * Gets the static counter value, triggering EXEC_GET_STATIC.
   *
   * @return current static counter value
   */
  public static Integer getStaticCounter() {
    return staticCounter;
  }

  /**
   * Sets the static counter value, triggering EXEC_PUT_STATIC.
   *
   * @param value new static counter value
   */
  public static void setStaticCounter(Integer value) {
    staticCounter = value;
  }

  /**
   * Factory method that creates an InterceptableApp instance with a parameterized constructor.
   *
   * <p>Used for testing constructor interception via call-site weaving. The constructor call within
   * this method is the intercepted call site.
   *
   * @param initialCounter initial value for the counter field
   * @return new InterceptableApp instance
   */
  public static InterceptableApp createWithCounter(Integer initialCounter) {
    return new InterceptableApp(initialCounter); // <-- Constructor call site for interception
  }

  /**
   * Wrapper method that calls the static multiplyStaticBy method.
   *
   * <p>Used for testing static method interception via call-site weaving. The static method call
   * within this instance method is the intercepted call site.
   *
   * @param multiple factor to multiply static counter by
   * @return new value of static counter
   */
  public Integer callMultiplyStaticBy(Integer multiple) {
    return multiplyStaticBy(multiple); // <-- Static method call site for interception
  }

  /**
   * Wrapper method that calls the static incrementStaticCounter method.
   *
   * <p>Used for testing static method interception via call-site weaving. The static method call
   * within this instance method is the intercepted call site.
   *
   * @return new value of static counter after increment
   */
  public Integer callIncrementStaticCounter() {
    return incrementStaticCounter(); // <-- Static method call site for interception
  }

  /**
   * Factory method that creates N instances using the parameterized constructor.
   *
   * <p>Used for testing multiple constructor intercepts from a single invocation. Each constructor
   * call within this method is an intercepted call site.
   *
   * @param n number of instances to create
   * @param initialCounter base initial value for the counter field
   * @return list of new InterceptableApp instances
   */
  public static List<InterceptableApp> createNInstances(Integer n, Integer initialCounter) {
    List<InterceptableApp> instances = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      instances.add(new InterceptableApp(initialCounter + i)); // <-- Constructor call site
    }
    return instances;
  }

  /**
   * Wrapper method that calls multiplyStaticBy N times.
   *
   * <p>Used for testing multiple static method intercepts from a single invocation. Each static
   * method call within this method is an intercepted call site.
   *
   * @param n number of times to call multiplyStaticBy
   * @param factor factor to multiply static counter by
   * @return final value of static counter after all multiplications
   */
  public Integer callMultiplyStaticByNTimes(Integer n, Integer factor) {
    Integer result = null;
    for (int i = 0; i < n; i++) {
      result = multiplyStaticBy(factor); // <-- Static method call site
    }
    return result;
  }

  /**
   * Wrapper method that calls incrementStaticCounter N times.
   *
   * <p>Used for testing multiple static method intercepts from a single invocation. Each static
   * method call within this method is an intercepted call site.
   *
   * @param n number of times to call incrementStaticCounter
   * @return final value of static counter after all increments
   */
  public Integer callIncrementStaticCounterNTimes(Integer n) {
    Integer result = null;
    for (int i = 0; i < n; i++) {
      result = incrementStaticCounter(); // <-- Static method call site
    }
    return result;
  }

  /**
   * Wrapper method that reads the instance counter field N times.
   *
   * <p>Used for testing multiple instance field get intercepts from a single invocation. Each field
   * read within this method is an intercepted call site.
   *
   * @param n number of times to read the counter field
   * @return list of counter values read
   */
  public List<Integer> getCounterNTimes(Integer n) {
    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      values.add(counter); // <-- Field get call site
    }
    return values;
  }

  /**
   * Wrapper method that writes the instance counter field N times.
   *
   * <p>Used for testing multiple instance field put intercepts from a single invocation. Each field
   * write within this method is an intercepted call site.
   *
   * @param n number of times to write the counter field
   * @param baseValue base value for the counter field
   */
  public void setCounterNTimes(Integer n, Integer baseValue) {
    for (int i = 0; i < n; i++) {
      counter = baseValue + i; // <-- Field put call site
    }
  }

  /**
   * Wrapper method that reads the static counter field N times.
   *
   * <p>Used for testing multiple static field get intercepts from a single invocation. Each field
   * read within this method is an intercepted call site.
   *
   * @param n number of times to read the static counter field
   * @return list of static counter values read
   */
  public static List<Integer> getStaticCounterNTimes(Integer n) {
    List<Integer> values = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      values.add(staticCounter); // <-- Static field get call site
    }
    return values;
  }

  /**
   * Wrapper method that writes the static counter field N times.
   *
   * <p>Used for testing multiple static field put intercepts from a single invocation. Each field
   * write within this method is an intercepted call site.
   *
   * @param n number of times to write the static counter field
   * @param baseValue base value for the static counter field
   */
  public static void setStaticCounterNTimes(Integer n, Integer baseValue) {
    for (int i = 0; i < n; i++) {
      staticCounter = baseValue + i; // <-- Static field put call site
    }
  }

  /**
   * Resets the local intercept callback counters.
   *
   * <p>This method is called via RPC before each local intercept test to ensure callback count
   * assertions start from a known state (count=0).
   */
  public static void resetLocalInterceptCallbacks() {
    LocalInterceptCallbacks.reset();
  }

  // ==================== Arithmetic Methods ====================

  /**
   * Adds two integers.
   *
   * <p>Used for testing argument mutation through the AROUND chain.
   *
   * @param a first operand
   * @param b second operand
   * @return the sum of a and b
   */
  public Integer add(Integer a, Integer b) {
    return a + b;
  }

  /**
   * Returns a constant value (100).
   *
   * <p>Used for testing return value modification through the AROUND chain.
   *
   * @return always returns 100
   */
  public Integer returnHundred() {
    return 100;
  }

  // ==================== Exception Throwing Methods ====================

  /**
   * Method that throws an IllegalArgumentException with the given message.
   *
   * <p>Used for testing exception propagation through the AROUND chain.
   *
   * @param message the exception message
   * @throws IllegalArgumentException always
   */
  public void throwIllegalArgumentException(String message) {
    throw new IllegalArgumentException(message);
  }

  /**
   * Method that throws a RuntimeException with the given message.
   *
   * <p>Used for testing exception propagation through the AROUND chain.
   *
   * @param message the exception message
   * @throws RuntimeException always
   */
  public void throwRuntimeException(String message) {
    throw new RuntimeException(message);
  }

  /**
   * Method that conditionally throws based on parameter.
   *
   * <p>If shouldThrow is true, throws an IllegalArgumentException. Otherwise returns the counter
   * value.
   *
   * @param shouldThrow whether to throw an exception
   * @return the counter value if shouldThrow is false
   * @throws IllegalArgumentException if shouldThrow is true
   */
  public Integer maybeThrow(Boolean shouldThrow) {
    if (shouldThrow) {
      throw new IllegalArgumentException("Intentional exception from maybeThrow");
    }
    return counter;
  }
}
