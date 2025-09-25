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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;

public class ParameterAdaptationUtilsEdgeCasesTest {

  static class CtorTarget {
    final int v;

    CtorTarget(int v) {
      this.v = v;
    }
  }

  private static Method method(String name, Class<?>... p) throws Exception {
    return Methods.class.getMethod(name, p);
  }

  static class Methods {
    public static void acceptsListOfLongs(java.util.List<Long> a) {}

    public static void acceptsMapOfSI(java.util.Map<String, Integer> m) {}

    public static void acceptsPrims(int i, long l) {}

    public static void acceptsString(String s) {}
  }

  @Test
  public void mismatchArgLength_throwsIAE() throws Exception {
    Method m = method("acceptsPrims", int.class, long.class);
    MessageArgument[] raw = {new MessageArgument(1.0, false)}; // only one arg
    assertThrows(
        IllegalArgumentException.class,
        () -> ParameterAdaptationUtils.adaptParametersForMethod(m, raw));
  }

  @Test
  public void adaptParametersForConstructor_numericToInt_ok() throws Exception {
    Constructor<CtorTarget> ctor = CtorTarget.class.getDeclaredConstructor(int.class);
    Object[] adapted =
        ParameterAdaptationUtils.adaptParametersForConstructor(
            ctor, new MessageArgument[] {new MessageArgument(42.0, false)});
    assertThat(adapted.length, is(1));
    assertThat(adapted[0], instanceOf(Integer.class));
    assertThat(((Integer) adapted[0]).intValue(), is(42));
  }

  @Test
  public void adaptNumeric_fractionToInt_throws() throws Exception {
    Method m = method("acceptsPrims", int.class, long.class);
    MessageArgument[] raw = {new MessageArgument(3.14, false), new MessageArgument(1.0, false)};
    assertThrows(
        RuntimeException.class, () -> ParameterAdaptationUtils.adaptParametersForMethod(m, raw));
  }

  @Test
  public void adaptCollection_wrongRawType_throws() throws Exception {
    Method m = method("acceptsListOfLongs", List.class);
    MessageArgument[] raw = {new MessageArgument("not-a-collection", false)};
    assertThrows(
        IllegalArgumentException.class,
        () -> ParameterAdaptationUtils.adaptParametersForMethod(m, raw));
  }

  @Test
  public void adaptMap_wrongRawType_throws() throws Exception {
    Method m = method("acceptsMapOfSI", java.util.Map.class);
    MessageArgument[] raw = {new MessageArgument(123, false)};
    assertThrows(
        IllegalArgumentException.class,
        () -> ParameterAdaptationUtils.adaptParametersForMethod(m, raw));
  }

  @Test
  public void adaptToClass_nonNumericPassThrough_ok() throws Exception {
    Method m = method("acceptsString", String.class);
    MessageArgument[] raw = {new MessageArgument("hello", false)};
    Object[] adapted = ParameterAdaptationUtils.adaptParametersForMethod(m, raw);
    assertThat(adapted[0], is("hello"));
  }
}
