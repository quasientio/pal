/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.Before;
import org.junit.Test;

public class ParamsTest {

  static class DummyClass {
    public void increment(int ignoredA, int ignoredB) {}
  }

  private String[] parameterNames;
  private Class<?>[] parameterTypes;
  private Parameter[] parameters;
  private Params params;

  @Before
  public void setUp() throws Exception {
    parameterNames = new String[] {"param1", "param2"};
    parameterTypes = new Class[] {String.class, String.class};
    Method method =
        CodeSignatureTest.DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    parameters = method.getParameters();
    params = new Params(parameterNames, parameterTypes, parameters);
  }

  @Test
  public void params_differentLenArrays_illegalArgumentException() throws NoSuchMethodException {
    String[] parameterNames = new String[] {"param1", "param2"};

    // parameterTypes should be of len=2
    Class<?>[] parameterTypes = new Class[] {String.class};
    Method method =
        CodeSignatureTest.DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    Parameter[] parameters = method.getParameters();
    try {
      new Params(parameterNames, parameterTypes, parameters);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException ignored) {
      // all good
    }
  }

  @Test
  public void params_noParamNames_argX() {
    Params paramsNoNames = new Params(null, parameterTypes, parameters);
    assertThat(paramsNoNames.getParameterNames(), is(new String[] {"arg0", "arg1"}));
  }

  @Test
  public void getParameterNames() {
    assertThat(params.getParameterNames(), is(parameterNames));
  }

  @Test
  public void getParameterTypes() {
    assertThat(params.getParameterTypes(), is(parameterTypes));
  }

  @Test
  public void getParameters() {
    assertThat(params.getParameters(), is(parameters));
  }

  @Test
  public void equalsContract() throws NoSuchMethodException {
    Params a = new Params(parameterNames, parameterTypes, parameters);
    Params b = new Params(parameterNames, parameterTypes, parameters);
    Params c = new Params(parameterNames, parameterTypes, parameters);
    Params different = new Params(new String[] {"x", "y"}, parameterTypes, parameters);

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }
}
