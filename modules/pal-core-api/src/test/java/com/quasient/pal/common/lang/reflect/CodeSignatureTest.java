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

import static org.junit.Assert.assertArrayEquals;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import nl.jqno.equalsverifier.EqualsVerifier;
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
        new CodeSignature(
            declaringType,
            declaringTypeName,
            modifiers,
            name,
            exceptionTypes,
            new Params(parameterNames, parameterTypes, parameters)) {};
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
    // EqualsVerifier requires prefabValues with actual instances of Parameter.
    // Since we can't instantiate Parameter, we use our DummyClass and reflection to get them.
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    EqualsVerifier.forClass(CodeSignature.class)
        .withPrefabValues(Parameter.class, method.getParameters()[0], method.getParameters()[1])
        .usingGetClass()
        .verify();
  }
}
