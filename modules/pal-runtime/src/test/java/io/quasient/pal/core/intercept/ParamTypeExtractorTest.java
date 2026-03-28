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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for the unified parameter type extraction utility.
 *
 * <p>These tests validate {@code ParamTypeExtractor}, which replaces the multiple {@code
 * Arrays.stream(paramTypes).map(Class::getName).toList()} calls in {@link
 * io.quasient.pal.core.execution.java.BaseExecMessageDispatcher} with a single extraction per
 * dispatch that reuses a thread-local {@code String[]} buffer.
 *
 * <p>The extractor should:
 *
 * <ul>
 *   <li>Convert {@code Class[]} to {@code String[]} of fully-qualified type names
 *   <li>Handle primitives, arrays, and reference types correctly
 *   <li>Handle null and empty inputs gracefully
 *   <li>Reuse the same {@code String[]} buffer via ThreadLocal for same-length inputs
 *   <li>Produce output identical to the existing stream-based extraction
 * </ul>
 *
 * @see io.quasient.pal.core.execution.java.BaseExecMessageDispatcher
 */
public class ParamTypeExtractorTest {

  /**
   * Verifies that basic parameter type extraction works for a mix of primitive, reference, and
   * array types.
   *
   * <p>The extractor must use {@link Class#getName()} for each type, which produces JVM internal
   * names for array types (e.g., {@code "[D"} for {@code double[]}).
   */
  @Test
  public void shouldExtractParamTypesFromClasses() {
    // Given: Class[] containing a primitive, a reference type, and an array type
    Class<?>[] input = {int.class, String.class, double[].class};

    // When: extractParamTypes() is called with this input
    String[] result = ParamTypeExtractor.extractParamTypes(input);

    // Then: Returns String[] {"int", "java.lang.String", "[D"}
    assertNotNull(result);
    assertArrayEquals(new String[] {"int", "java.lang.String", "[D"}, result);
  }

  /**
   * Verifies that an empty {@code Class[]} input produces an empty {@code String[]} output, not
   * null.
   */
  @Test
  public void shouldExtractEmptyParamTypes() {
    // Given: An empty Class[] (zero-length array)
    Class<?>[] input = {};

    // When: extractParamTypes() is called with this input
    String[] result = ParamTypeExtractor.extractParamTypes(input);

    // Then: Returns an empty String[] (length 0)
    assertNotNull(result);
    assertThat(result.length, is(0));
  }

  /**
   * Verifies that null {@code Class[]} input returns null, preserving the null-signals-field
   * semantic used by in-flight dispatch tracking.
   */
  @Test
  public void shouldExtractNullParamTypes() {
    // Given: null Class[] input

    // When: extractParamTypes() is called with null
    String[] result = ParamTypeExtractor.extractParamTypes(null);

    // Then: Returns null (not an empty array)
    assertThat(result, is(nullValue()));
  }

  /**
   * Verifies that the ThreadLocal buffer reuse optimization works: when two consecutive calls use
   * the same-length input, the returned {@code String[]} should be the same object instance.
   *
   * <p>This is the key optimization: avoiding array allocation on every dispatch by reusing a
   * thread-local buffer. The buffer is only re-allocated when the input length changes.
   */
  @Test
  public void shouldReuseArrayViaThreadLocal() {
    // Given: A ThreadLocal String[] buffer is used internally by the extractor

    // When: extractParamTypes() is called twice with same-length Class[] inputs
    //   First call: {int.class, String.class}
    String[] result1 =
        ParamTypeExtractor.extractParamTypes(new Class<?>[] {int.class, String.class});

    //   Second call: {double.class, long.class}
    String[] result2 =
        ParamTypeExtractor.extractParamTypes(new Class<?>[] {double.class, long.class});

    // Then: The returned String[] instance from both calls is the same object
    assertThat(result2, is(sameInstance(result1)));

    // Note: The *contents* will differ (reflecting the second call's types),
    //   but the array container itself is reused.
    assertArrayEquals(new String[] {"double", "long"}, result2);
  }

  /**
   * Verifies that the new loop-based extraction produces output identical to the existing
   * stream-based extraction used in {@code BaseExecMessageDispatcher.getParamTypesFromPjp()}.
   *
   * <p>This is a compatibility test ensuring the refactored path matches the original: {@code
   * Arrays.stream(paramTypes).map(Class::getName).toList()}.
   */
  @Test
  public void shouldMatchExistingExtractParamTypesOutput() {
    // Given: A Class[] input with various types
    Class<?>[] input = {
      String.class, int.class, double[].class, Object.class, byte.class, List.class
    };

    // When: Both the existing stream-based extraction and the new loop-based extraction
    //   are applied to the same input
    List<String> existingResult = Arrays.stream(input).map(Class::getName).toList();
    String[] newResult = ParamTypeExtractor.extractParamTypes(input);

    // Then: The results are identical (same elements in same order)
    assertThat(Arrays.asList(newResult), is(existingResult));
  }
}
