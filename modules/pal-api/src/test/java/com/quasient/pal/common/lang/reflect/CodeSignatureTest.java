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
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.Before;
import org.junit.Test;

public class CodeSignatureTest extends SignatureTest {

  static class DummyClass {
    public void increment(int ignoredA, int ignoredB) {}
  }

  protected Class<?>[] exceptionTypes;
  protected String[] parameterNames;
  protected Class<?>[] parameterTypes;
  protected Parameter[] parameters;
  private CodeSignature codeSignature;

  private static class TestCodeSignature extends CodeSignature {
    public TestCodeSignature(
        Class<?> declaringType,
        String declaringTypeName,
        int modifiers,
        String name,
        Class<?>[] exceptionTypes,
        Params params) {
      super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, params);
    }
  }

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    exceptionTypes = new Class[] {IllegalArgumentException.class};
    parameterNames = new String[] {"param1", "param2"};
    parameterTypes = new Class[] {String.class, String.class};
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    parameters = method.getParameters();
    codeSignature =
        new TestCodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters));
  }

  @Test
  public void getExceptionTypes() {
    assertArrayEquals(exceptionTypes, codeSignature.getExceptionTypes());
  }

  @Test
  public void getParameterNames() {
    assertArrayEquals(parameterNames, codeSignature.getParameterNames());
  }

  @Test
  public void getParameterTypes() {
    assertArrayEquals(parameterTypes, codeSignature.getParameterTypes());
  }

  @Test
  public void getParameters() {
    assertArrayEquals(parameters, codeSignature.getParameters());
  }

  @Test
  @Override
  public void equalsContract() throws NoSuchMethodException {
    CodeSignature a =
        new TestCodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters));
    CodeSignature b =
        new TestCodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters));
    CodeSignature c =
        new TestCodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters));
    CodeSignature different =
        new TestCodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name + "x",
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters));

    assertThat(a, is(b));
    assertThat(b, is(c));
    assertThat(a.hashCode(), is(b.hashCode()));
    assertThat(b.hashCode(), is(c.hashCode()));
    assertNotEquals(a, different);
    assertNotEquals(a, null);
    assertNotEquals(a, new Object());
  }
}
