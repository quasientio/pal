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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("rawtypes")
public class ConstructorSignatureTest {

  static class DummyClass1 {}

  static class DummyClass2 {}

  static class DummyClassForConstructorSignatureTest {}

  private Constructor constructor;
  private ConstructorSignature constructorSignature;

  @Before
  public void setUp() throws Exception {
    constructor = DummyClassForConstructorSignatureTest.class.getDeclaredConstructor();
    constructorSignature = new ConstructorSignature(constructor);
  }

  @Test
  public void getConstructor() {
    assertEquals(constructor, constructorSignature.getConstructor());
  }

  @Test
  public void equalsAndHashCode() throws Exception {
    Constructor constructor1 = DummyClass1.class.getDeclaredConstructor();
    ConstructorSignature signature1 = new ConstructorSignature(constructor1);
    ConstructorSignature signature11 = new ConstructorSignature(constructor1);

    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    Constructor constructor2 = DummyClass2.class.getDeclaredConstructor();
    ConstructorSignature signature2 = new ConstructorSignature(constructor2);
    ConstructorSignature signature22 = new ConstructorSignature(constructor2);

    assertNotSame(signature2, signature22);
    assertEquals(signature2, signature22);
    assertEquals(signature2.hashCode(), signature22.hashCode());

    assertNotEquals(constructor1, constructor2);
    assertNotEquals(signature1, signature2);
    assertNotEquals(signature1.hashCode(), signature2.hashCode());

    assertNotEquals(signature1, null);
  }

  @Test
  public void testToString() {
    assertEquals(
        constructorSignature.toString(),
        "ConstructorSignature{"
            + "declaringType="
            + constructorSignature.getDeclaringType()
            + ", declaringTypeName="
            + constructorSignature.getDeclaringTypeName()
            + ", name="
            + constructorSignature.getName()
            + ", modifiers="
            + constructorSignature.getModifiers()
            + ", exceptionTypes="
            + Arrays.toString(constructorSignature.getExceptionTypes())
            + ", parameterNames="
            + Arrays.toString(constructorSignature.getParameterNames())
            + ", parameterTypes="
            + Arrays.toString(constructorSignature.getParameterTypes())
            + ", parameters="
            + Arrays.toString(constructorSignature.getParameters())
            + ", constructor="
            + constructorSignature.getConstructor()
            + '}');
  }
}
