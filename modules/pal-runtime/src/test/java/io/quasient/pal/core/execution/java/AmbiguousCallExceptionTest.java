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
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link AmbiguousCallException}.
 *
 * <p>Tests all constructors, message generation, and accessor methods.
 */
public class AmbiguousCallExceptionTest {

  // ===== Constructor Tests =====

  /** Tests the 4-parameter constructor for method ambiguity. */
  @Test
  public void constructor_withMethodName_setsAllFields() throws Exception {
    String className = "java.lang.String";
    String methodName = "valueOf";
    List<Class<?>> paramTypes = List.of(Object.class);
    Method m1 = String.class.getMethod("valueOf", Object.class);
    Method m2 = String.class.getMethod("valueOf", char[].class);
    List<Method> executables = List.of(m1, m2);

    AmbiguousCallException ex =
        new AmbiguousCallException(className, methodName, paramTypes, executables);

    assertThat(ex, notNullValue());
    assertThat(ex.getMatchingExecutables(), hasSize(2));
  }

  /** Tests the 3-parameter constructor for constructor ambiguity. */
  @Test
  public void constructor_forConstructor_usesNewAsMethodName() throws Exception {
    String className = "java.lang.String";
    List<Class<?>> paramTypes = List.of(byte[].class);
    Constructor<?> c1 = String.class.getConstructor(byte[].class);
    Constructor<?> c2 = String.class.getConstructor(char[].class);
    List<Constructor<?>> executables = List.of(c1, c2);

    AmbiguousCallException ex = new AmbiguousCallException(className, paramTypes, executables);

    assertThat(ex.getMessage(), containsString("new"));
  }

  // ===== getMessage() Tests =====

  /** Tests that getMessage includes class name. */
  @Test
  public void getMessage_containsClassName() throws Exception {
    String className = "com.example.Calculator";
    String methodName = "add";
    List<Class<?>> paramTypes = List.of(int.class, int.class);
    Method m = String.class.getMethod("valueOf", int.class);

    AmbiguousCallException ex =
        new AmbiguousCallException(className, methodName, paramTypes, List.of(m));

    assertThat(ex.getMessage(), containsString("com.example.Calculator"));
  }

  /** Tests that getMessage includes method name. */
  @Test
  public void getMessage_containsMethodName() throws Exception {
    String className = "com.example.Calculator";
    String methodName = "multiply";
    List<Class<?>> paramTypes = List.of(double.class);
    Method m = String.class.getMethod("valueOf", double.class);

    AmbiguousCallException ex =
        new AmbiguousCallException(className, methodName, paramTypes, List.of(m));

    assertThat(ex.getMessage(), containsString("multiply"));
  }

  /** Tests that getMessage includes "Ambiguous call" text. */
  @Test
  public void getMessage_containsAmbiguousCallText() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);
    AmbiguousCallException ex =
        new AmbiguousCallException("Test", "method", List.of(int.class), List.of(m));

    assertThat(ex.getMessage(), containsString("Ambiguous call"));
  }

  /** Tests that getMessage lists parameter types of matching executables. */
  @Test
  public void getMessage_listsExecutableParameterTypes() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);

    AmbiguousCallException ex =
        new AmbiguousCallException("Test", "valueOf", List.of(int.class), List.of(m));

    assertThat(ex.getMessage(), containsString("int"));
  }

  /** Tests that getMessage includes the parameter types to match. */
  @Test
  public void getMessage_includesParameterTypesToMatch() throws Exception {
    Method m = String.class.getMethod("valueOf", Object.class);
    List<Class<?>> paramTypes = List.of(String.class, Integer.class);

    AmbiguousCallException ex =
        new AmbiguousCallException("Test", "method", paramTypes, List.of(m));

    assertThat(ex.getMessage(), containsString("String"));
    assertThat(ex.getMessage(), containsString("Integer"));
  }

  /** Tests getMessage with multiple matching executables. */
  @Test
  public void getMessage_withMultipleExecutables_listsAll() throws Exception {
    Method m1 = String.class.getMethod("valueOf", int.class);
    Method m2 = String.class.getMethod("valueOf", long.class);
    Method m3 = String.class.getMethod("valueOf", float.class);

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "valueOf", List.of(Number.class), List.of(m1, m2, m3));

    String message = ex.getMessage();
    // Should list all three method signatures
    assertThat(message, containsString("valueOf(int)"));
    assertThat(message, containsString("valueOf(long)"));
    assertThat(message, containsString("valueOf(float)"));
  }

  /** Tests getMessage with no-arg method. */
  @Test
  public void getMessage_withNoArgMethod_showsEmptyParentheses() throws Exception {
    Method m = String.class.getMethod("toString");

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "toString", List.of(), List.of(m));

    assertThat(ex.getMessage(), containsString("toString()"));
  }

  // ===== toString() Tests =====

  /** Tests that toString delegates to getMessage. */
  @Test
  public void toString_delegatesToGetMessage() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);
    AmbiguousCallException ex =
        new AmbiguousCallException("Test", "method", List.of(int.class), List.of(m));

    assertThat(ex.toString(), is(ex.getMessage()));
  }

  // ===== getMatchingExecutables() Tests =====

  /** Tests that getMatchingExecutables returns correct executables. */
  @Test
  public void getMatchingExecutables_returnsAllExecutables() throws Exception {
    Method m1 = String.class.getMethod("valueOf", int.class);
    Method m2 = String.class.getMethod("valueOf", long.class);
    List<Method> executables = List.of(m1, m2);

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "valueOf", List.of(Number.class), executables);

    assertThat(ex.getMatchingExecutables(), hasSize(2));
  }

  /** Tests that getMatchingExecutables returns unmodifiable list. */
  @Test(expected = UnsupportedOperationException.class)
  public void getMatchingExecutables_returnsUnmodifiableList() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);
    AmbiguousCallException ex =
        new AmbiguousCallException("Test", "method", List.of(int.class), List.of(m));

    // This should throw UnsupportedOperationException
    ex.getMatchingExecutables().clear();
  }

  /** Tests that modifications to original list don't affect exception. */
  @Test
  public void constructor_makesDefensiveCopy_ofExecutables() throws Exception {
    Method m1 = String.class.getMethod("valueOf", int.class);
    Method m2 = String.class.getMethod("valueOf", long.class);
    List<Executable> executables = new ArrayList<>(Arrays.asList(m1, m2));

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "valueOf", List.of(Number.class), executables);

    // Modify original list
    executables.clear();

    // Exception should still have 2 executables
    assertThat(ex.getMatchingExecutables(), hasSize(2));
  }

  /** Tests that modifications to original param types list don't affect exception. */
  @Test
  public void constructor_makesDefensiveCopy_ofParameterTypes() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);
    List<Class<?>> paramTypes = new ArrayList<>(Arrays.asList(int.class, long.class));

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "valueOf", paramTypes, List.of(m));

    // Modify original list
    paramTypes.clear();

    // getMessage should still reference original types
    String message = ex.getMessage();
    assertThat(message, containsString("int"));
  }

  // ===== Edge Cases =====

  /** Tests with empty parameter types list. */
  @Test
  public void constructor_withEmptyParameterTypes_works() throws Exception {
    Method m = String.class.getMethod("toString");

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "toString", List.of(), List.of(m));

    assertThat(ex.getMessage(), containsString("toString"));
    assertThat(ex.getMessage(), containsString("[]")); // empty list representation
  }

  /** Tests with single matching executable. */
  @Test
  public void constructor_withSingleExecutable_works() throws Exception {
    Method m = String.class.getMethod("valueOf", int.class);

    AmbiguousCallException ex =
        new AmbiguousCallException("String", "valueOf", List.of(int.class), List.of(m));

    assertThat(ex.getMatchingExecutables(), hasSize(1));
  }
}
