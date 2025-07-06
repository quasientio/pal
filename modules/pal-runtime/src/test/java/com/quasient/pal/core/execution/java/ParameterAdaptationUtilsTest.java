/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ParameterAdaptationUtilsTest {

  // Helper method to create a Method instance for testing
  private static Method getMethod(String methodName, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return TestMethods.class.getMethod(methodName, parameterTypes);
  }

  /** Test class with methods having various parameter types for testing adaptation. */
  @SuppressWarnings("unused")
  public static class TestMethods {

    public static void processList(List<Long> numbers) {}

    public static void processMap(Map<String, Integer> map) {}

    public static void processPrimitive(boolean flag, long count) {}

    public static void processMixed(List<Double> doubles, boolean flag) {}
  }

  @Test
  public void testListAdaptationWithNumericConversion_passedByReference() throws Exception {
    // Method: processList(List<Long>)
    Method method = getMethod("processList", List.class);

    // Raw input: ArrayList<Double>
    Object doubleList = new ArrayList<>(Arrays.asList(10.0, 20.0, 30.0));
    MessageArgument messageArg = new MessageArgument(doubleList, true);

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method, new MessageArgument[] {messageArg});

    // Assert that the argument is not adapted, so still a List<Double>
    assertThat(adaptedArgs[0], instanceOf(List.class));
    List<?> adaptedList = (List<?>) adaptedArgs[0];
    assertThat(adaptedList, everyItem(instanceOf(Double.class)));
    assertThat(adaptedList, contains(10.0, 20.0, 30.0));
    // assert it's the same instance
    assertThat(adaptedList, is(sameInstance(doubleList)));
  }

  @Test
  public void testListAdaptationWithNumericConversion_passedByValue() throws Exception {
    // Method: processList(List<Long>)
    Method method = getMethod("processList", List.class);

    // Raw input: ArrayList<Double>
    Object doubleList = new ArrayList<>(Arrays.asList(10.0, 20.0, 30.0));
    MessageArgument messageArg = new MessageArgument(doubleList, false);

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method, new MessageArgument[] {messageArg});

    // Assert that the adapted argument is a List<Long>
    assertThat(adaptedArgs[0], instanceOf(List.class));
    List<?> adaptedList = (List<?>) adaptedArgs[0];
    assertThat(adaptedList, everyItem(instanceOf(Long.class)));

    // Assert correctness of values
    assertThat(adaptedList, contains(10L, 20L, 30L));
  }

  @Test
  public void testMapAdaptationWithNumericConversion_passedByReference() throws Exception {
    // Method: processMap(Map<String, Integer>)
    Method method = getMethod("processMap", Map.class);

    // Raw input: Map<String, Double>
    Map<String, Double> map = new HashMap<>();
    map.put("key1", 42.0);
    map.put("key2", 100.0);
    MessageArgument arg = new MessageArgument(map, true);

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(method, new MessageArgument[] {arg});

    // Assert that the argument is not adapted, so still a Map<String, Double>
    assertThat(adaptedArgs[0], instanceOf(Map.class));
    Map<?, ?> adaptedMap = (Map<?, ?>) adaptedArgs[0];
    assertThat(adaptedMap.keySet(), everyItem(instanceOf(String.class)));
    assertThat(adaptedMap.values(), everyItem(instanceOf(Double.class)));
    assertThat(adaptedMap, hasEntry("key1", 42.0));
    assertThat(adaptedMap, hasEntry("key2", 100.0));
    // assert it's the same instance
    assertThat(adaptedMap, is(sameInstance(map)));
  }

  @Test
  public void testMapAdaptationWithNumericConversion_passedByValue() throws Exception {
    // Method: processMap(Map<String, Integer>)
    Method method = getMethod("processMap", Map.class);

    // Raw input: Map<String, Double>
    Map<String, Double> map = new HashMap<>();
    map.put("key1", 42.0);
    map.put("key2", 100.0);
    MessageArgument arg = new MessageArgument(map, false);

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(method, new MessageArgument[] {arg});

    // Assert that the adapted argument is a Map<String, Integer>
    assertThat(adaptedArgs[0], instanceOf(Map.class));
    Map<?, ?> adaptedMap = (Map<?, ?>) adaptedArgs[0];
    assertThat(adaptedMap.keySet(), everyItem(instanceOf(String.class)));
    assertThat(adaptedMap.values(), everyItem(instanceOf(Integer.class)));

    // Assert correctness of values
    assertThat(adaptedMap, hasEntry("key1", 42));
    assertThat(adaptedMap, hasEntry("key2", 100));
  }

  @Test
  public void testPrimitiveAdaptationSkipsUnnecessaryWork() throws Exception {
    // Method: processPrimitive(boolean flag, long count)
    Method method = getMethod("processPrimitive", boolean.class, long.class);

    // Raw input: Boolean and Long (compatible types)
    Object[] rawArgs = {Boolean.TRUE, 42L};

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method,
            new MessageArgument[] {
              new MessageArgument(rawArgs[0], false), new MessageArgument(rawArgs[1], false),
            });

    // Assert that no unnecessary adaptation occurred
    assertThat(adaptedArgs[0], is(rawArgs[0]));
    assertThat(adaptedArgs[1], is(rawArgs[1]));
  }

  @Test
  public void testListPreservesReferenceWhenCompatible() throws Exception {
    // Method: processList(List<Long>)
    Method method = getMethod("processList", List.class);

    // Raw input: ArrayList<Long> (already compatible)
    List<Long> rawArg = new ArrayList<>(Arrays.asList(10L, 20L, 30L));

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method, new MessageArgument[] {new MessageArgument(rawArg, false)});

    // Assert that the original list is returned (no new instance created)
    assertThat(adaptedArgs[0], is(sameInstance(rawArg)));
  }

  @Test
  public void testThrowsOnIncompatibleType() throws Exception {
    // Method: processList(List<Long>)
    Method method = getMethod("processList", List.class);

    // Raw input: incompatible type (String instead of Long)
    Object rawArg = new ArrayList<>(Arrays.asList("a", "b", "c"));

    // Assert that an exception is thrown
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ParameterAdaptationUtils.adaptParametersForMethod(
                method, new MessageArgument[] {new MessageArgument(rawArg, false)}));
  }

  @Test
  public void testHandlesMixedTypesCorrectly() throws Exception {
    // Method: processMixed(List<Double>, boolean)
    Method method = getMethod("processMixed", List.class, boolean.class);

    // Raw input: ArrayList<Double> and Boolean
    Object[] rawArgs = {new ArrayList<>(Arrays.asList(1.0, 2.5, 3.5)), Boolean.FALSE};

    Object[] adaptedArgs =
        ParameterAdaptationUtils.adaptParametersForMethod(
            method,
            new MessageArgument[] {
              new MessageArgument(rawArgs[0], false), new MessageArgument(rawArgs[1], false)
            });

    // Assert that the List<Double> is unchanged (compatible type)
    assertThat(adaptedArgs[0], is(rawArgs[0]));

    // Assert that the boolean parameter is unchanged
    assertThat(adaptedArgs[1], is(Boolean.FALSE));
  }
}
