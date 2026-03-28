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
package io.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for the optimized {@link InterceptRequestEntry#matches(String, String)} overload that
 * accepts pre-computed {@code executablePath} and {@code joinedParamTypes} strings instead of
 * computing them on every call.
 *
 * <p>This optimization reduces allocations from N*2 (per registered intercept) to 2 total per
 * intercept check, by pre-computing the "className.executableName" and "param1,param2" strings at
 * the call site and passing them to each entry's matches() method.
 *
 * @see InterceptRequestEntry
 */
public class InterceptRequestEntryOptimizedMatchTest {

  /** Shared message builder for constructing intercept messages. */
  private final MessageBuilder msgBuilder = new MessageBuilder();

  /** Shared peer UUID for test intercept messages. */
  private final UUID peerUuid = UUID.randomUUID();

  /**
   * Creates an {@link InterceptRequestEntry} for a method interception with the given class,
   * method, and parameter types.
   */
  private InterceptRequestEntry buildMethodEntry(
      String className, String methodName, List<String> paramTypes) {
    InterceptMessage im =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            className,
            methodName,
            paramTypes,
            "callback.Class",
            "callbackMethod");
    return new InterceptRequestEntry(im);
  }

  /**
   * Creates an {@link InterceptRequestEntry} for a field interception with the given class and
   * field name.
   */
  private InterceptRequestEntry buildFieldEntry(String className, String fieldName) {
    InterceptMessage im =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            className,
            fieldName,
            FieldOpType.GET,
            "callback.Class",
            "callbackMethod");
    return new InterceptRequestEntry(im);
  }

  /**
   * Verifies that the optimized matches() returns true when the pre-computed executable path
   * exactly matches the entry's pattern.
   */
  @Test
  public void shouldMatchWithPrecomputedExactPath() {
    InterceptRequestEntry entry = buildMethodEntry("com.example.Foo", "bar", List.of());

    assertThat(entry.matches("com.example.Foo.bar", ""), is(true));
  }

  /**
   * Verifies that the optimized matches() returns false when the pre-computed executable path does
   * not match the entry's pattern.
   */
  @Test
  public void shouldNotMatchWithPrecomputedDifferentPath() {
    InterceptRequestEntry entry = buildMethodEntry("com.example.Foo", "bar", List.of());

    assertThat(entry.matches("com.example.Foo.baz", ""), is(false));
  }

  /**
   * Verifies that the optimized matches() correctly handles wildcard patterns using the
   * AntPathMatcher.
   */
  @Test
  public void shouldMatchWithPrecomputedWildcardPattern() {
    InterceptRequestEntry entry = buildMethodEntry("com.example.*", "bar", List.of());

    assertThat(entry.matches("com.example.Foo.bar", ""), is(true));
  }

  /**
   * Verifies that the optimized matches() correctly matches when both executable path and parameter
   * types match.
   */
  @Test
  public void shouldMatchWithPrecomputedParamTypes() {
    InterceptRequestEntry entry =
        buildMethodEntry("com.example.Foo", "bar", List.of("int", "String"));

    assertThat(entry.matches("com.example.Foo.bar", "int,String"), is(true));
  }

  /**
   * Verifies that the optimized matches() returns false when parameter types differ even if the
   * executable path matches.
   */
  @Test
  public void shouldNotMatchWhenParamTypesDiffer() {
    InterceptRequestEntry entry =
        buildMethodEntry("com.example.Foo", "bar", List.of("int", "String"));

    assertThat(entry.matches("com.example.Foo.bar", "int,int"), is(false));
  }

  /**
   * Verifies that the optimized matches() method produces identical results to the original
   * matches() method across a wide range of inputs.
   *
   * <p>This is the most critical test: it ensures behavioral equivalence between the original
   * {@code matches(String className, String execName, String[] paramTypes)} and the optimized
   * {@code matches(String execPath, String joinedParams)} for 100+ different input combinations
   * including wildcards, null params, empty params, and various class/method patterns.
   */
  @Test
  public void shouldProduceIdenticalResultsToOriginalMethod() {
    // Various entry configurations: (className pattern, methodName, paramTypes)
    // isField tracks whether each entry is a field entry (null paramTypes internally)
    InterceptRequestEntry[] entries = {
      // Exact class, exact method, no params
      buildMethodEntry("com.example.Foo", "bar", List.of()),
      // Exact class, exact method, one param
      buildMethodEntry("com.example.Foo", "bar", List.of("int")),
      // Exact class, exact method, multiple params
      buildMethodEntry("com.example.Foo", "bar", List.of("int", "java.lang.String", "boolean")),
      // Wildcard class, exact method, no params
      buildMethodEntry("com.example.*", "bar", List.of()),
      // Double-wildcard, exact method, no params
      buildMethodEntry("com.**", "bar", List.of()),
      // Exact class, wildcard method, no params
      buildMethodEntry("com.example.Foo", "*", List.of()),
      // All wildcards
      buildMethodEntry("**", "*", List.of()),
      // Question mark wildcard
      buildMethodEntry("com.example.Fo?", "bar", List.of()),
      // Field entry (null paramTypes)
      buildFieldEntry("com.example.Foo", "myField"),
      // Deep package path
      buildMethodEntry("com.example.deep.nested.pkg.Foo", "doSomething", List.of("long")),
    };
    boolean[] isField = {false, false, false, false, false, false, false, false, true, false};

    // Various (className, executableName, parameterTypes[]) test inputs
    String[][] classAndMethod = {
      {"com.example.Foo", "bar"},
      {"com.example.Foo", "baz"},
      {"com.example.Bar", "bar"},
      {"com.example.Foo", "BAR"}, // case variation (matcher is case-insensitive)
      {"COM.EXAMPLE.FOO", "BAR"}, // all-upper case variation
      {"com.example.Fox", "bar"}, // for '?' wildcard
      {"com.example.deep.nested.pkg.Foo", "doSomething"},
      {"com.other.Foo", "bar"},
      {"com.example.Foo", "myField"},
      {"java.lang.System", "gc"},
    };

    // Various parameter type arrays (including null for fields)
    String[][] paramTypesInputs = {
      null, // field access
      new String[0], // zero-arg method
      {"int"},
      {"int", "java.lang.String", "boolean"},
      {"int", "int"},
      {"long"},
      {"java.lang.String"},
    };

    int combinations = 0;
    for (int ei = 0; ei < entries.length; ei++) {
      InterceptRequestEntry entry = entries[ei];
      for (String[] cm : classAndMethod) {
        String className = cm[0];
        String execName = cm[1];
        for (String[] paramTypes : paramTypesInputs) {
          // Pre-compute for optimized call
          String execPath = className + "." + execName;
          String joinedParams = paramTypes != null ? String.join(",", paramTypes) : null;

          // The original matches() throws NPE when entry has non-null paramTypes but
          // parameterTypes arg is null (String.join doesn't accept null array).
          // For null paramTypes inputs against method entries, only test the optimized method.
          boolean canCallOriginal = paramTypes != null || isField[ei];

          if (canCallOriginal) {
            boolean originalResult = entry.matches(className, execName, paramTypes);
            boolean optimizedResult = entry.matches(execPath, joinedParams);

            assertThat(
                "Mismatch for entry pattern with input ("
                    + className
                    + "."
                    + execName
                    + ", params="
                    + Arrays.toString(paramTypes)
                    + ")",
                optimizedResult,
                is(originalResult));
          } else {
            // Only test the optimized method (should return false for method entry with null input)
            boolean optimizedResult = entry.matches(execPath, joinedParams);
            assertThat(
                "Method entry should not match null params: " + execPath,
                optimizedResult,
                is(false));
          }

          combinations++;
        }
      }
    }

    // Ensure we tested at least 100 combinations
    assertThat(
        "Expected at least 100 input combinations, got " + combinations,
        combinations >= 100,
        is(true));
  }

  /**
   * Verifies that a method entry with no parameter types specified (empty list) acts as a wildcard,
   * matching any parameter signature via the optimized overload.
   */
  @Test
  public void shouldMatchAnyParamsWhenEntryHasNoParamsSpecified() {
    InterceptRequestEntry entry = buildMethodEntry("com.example.Foo", "bar", List.of());

    // Should match method calls with any parameter types
    assertThat(entry.matches("com.example.Foo.bar", "int,int"), is(true));
    assertThat(entry.matches("com.example.Foo.bar", "java.lang.String"), is(true));
    assertThat(entry.matches("com.example.Foo.bar", "double,java.lang.String,boolean"), is(true));

    // Should still match zero-arg
    assertThat(entry.matches("com.example.Foo.bar", ""), is(true));

    // Should NOT match a different method
    assertThat(entry.matches("com.example.Foo.baz", "int,int"), is(false));
  }

  /**
   * Verifies that a method entry with explicit parameter types only matches that exact signature
   * via the optimized overload (no wildcard).
   */
  @Test
  public void shouldNotWildcardWhenParamsAreExplicitlySpecified() {
    InterceptRequestEntry entry =
        buildMethodEntry("com.example.Foo", "bar", List.of("int", "java.lang.String"));

    assertThat(entry.matches("com.example.Foo.bar", "int,java.lang.String"), is(true));
    assertThat(entry.matches("com.example.Foo.bar", "int,int"), is(false));
    assertThat(entry.matches("com.example.Foo.bar", ""), is(false));
  }

  /**
   * Verifies that both the original and optimized matches() methods handle null parameter types
   * consistently.
   *
   * <p>Null paramTypes in an InterceptRequestEntry indicates a field interception (not a method).
   * When both the entry's paramTypes and the input paramTypes are null, the match should succeed
   * (both represent field access). When only one is null, the match should fail.
   */
  @Test
  public void shouldHandleNullParamTypesInBothMethods() {
    // Field entry has null paramTypes
    InterceptRequestEntry fieldEntry = buildFieldEntry("com.example.Foo", "myField");

    // Method entry has non-null paramTypes (empty string for zero-arg method)
    InterceptRequestEntry methodEntry = buildMethodEntry("com.example.Foo", "myField", List.of());

    // Both null: field entry vs null input -> both should return true
    assertThat(fieldEntry.matches("com.example.Foo", "myField", null), is(true));
    assertThat(fieldEntry.matches("com.example.Foo.myField", null), is(true));

    // Field entry (null paramTypes) vs non-null input -> both should return false
    assertThat(fieldEntry.matches("com.example.Foo", "myField", new String[0]), is(false));
    assertThat(fieldEntry.matches("com.example.Foo.myField", ""), is(false));

    // Method entry (non-null paramTypes) vs null input -> optimized returns false.
    // Note: The original matches(String, String, String[]) throws NPE when entry has non-null
    // paramTypes and parameterTypes arg is null (String.join doesn't accept null array).
    // The optimized method handles this gracefully via Objects.equals.
    assertThat(methodEntry.matches("com.example.Foo.myField", null), is(false));
  }

  /**
   * Verifies that both the original and optimized matches() methods handle empty parameter type
   * arrays consistently.
   *
   * <p>Empty paramTypes (zero-arg method) should produce consistent results between the original
   * method (which receives {@code new String[0]}) and the optimized method (which receives {@code
   * ""} as joinedParamTypes from {@code String.join(",", new String[0])}).
   */
  @Test
  public void shouldHandleEmptyParamTypesArray() {
    InterceptRequestEntry entry = buildMethodEntry("com.example.Foo", "bar", List.of());

    // Original with empty array
    boolean originalResult = entry.matches("com.example.Foo", "bar", new String[0]);
    // Optimized with "" (String.join(",", new String[0]) == "")
    boolean optimizedResult = entry.matches("com.example.Foo.bar", "");

    assertThat(originalResult, is(true));
    assertThat(optimizedResult, is(true));
    assertThat(optimizedResult, is(originalResult));
  }
}
