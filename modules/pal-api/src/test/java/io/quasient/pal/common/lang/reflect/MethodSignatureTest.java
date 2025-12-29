/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class MethodSignatureTest {
  static class DummyClass1 {
    public void oooo(int ignoredOo) {}
  }

  static class DummyClass2 {
    public void oooo(int ignoredOo) {}
  }

  static class DummyClassForMethodSignatureTest {
    public void nop(String ignoredStr) {}
  }

  private Method method;
  private Class<?> returnType;
  private MethodSignature methodSignature;

  @Before
  public void setUp() throws Exception {
    method = DummyClassForMethodSignatureTest.class.getDeclaredMethod("nop", String.class);
    returnType = method.getReturnType();
    methodSignature = new MethodSignature(method);
  }

  @Test
  public void getMethod() {
    assertEquals(method, methodSignature.getMethod());
  }

  @Test
  public void getReturnType() {
    assertEquals(returnType, methodSignature.getReturnType());
  }

  @Test
  public void equalsAndHashCode() throws NoSuchMethodException {
    Method method1 = MethodSignatureTest.DummyClass1.class.getDeclaredMethod("oooo", int.class);
    MethodSignature signature1 = new MethodSignature(method1);
    MethodSignature signature11 = new MethodSignature(method1);

    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    Method method2 = MethodSignatureTest.DummyClass2.class.getDeclaredMethod("oooo", int.class);
    MethodSignature signature2 = new MethodSignature(method2);
    MethodSignature signature22 = new MethodSignature(method2);

    assertNotSame(signature2, signature22);
    assertEquals(signature2, signature22);
    assertEquals(signature2.hashCode(), signature22.hashCode());

    assertNotEquals(method1, method2);
    assertNotEquals(signature1, signature2);
    assertNotEquals(signature1.hashCode(), signature2.hashCode());

    assertNotEquals(signature1, null);

    // same method, different return type
    MethodSignature sigWithWrongReturnType =
        new MethodSignature(
            method1.getDeclaringClass(),
            method1.getDeclaringClass().getTypeName(),
            method1.getModifiers(),
            method1.getName(),
            method1.getExceptionTypes(),
            new Params(null, method1.getParameterTypes(), method1.getParameters()),
            method1,
            boolean.class);
    assertNotEquals(sigWithWrongReturnType, signature1);

    // same return type, different method
    MethodSignature method1ReturningLong =
        new MethodSignature(
            method1.getDeclaringClass(),
            method1.getDeclaringClass().getTypeName(),
            method1.getModifiers(),
            method1.getName(),
            method1.getExceptionTypes(),
            new Params(null, method1.getParameterTypes(), method1.getParameters()),
            method1,
            Long.class);

    MethodSignature method2ReturningLong =
        new MethodSignature(
            method1.getDeclaringClass(),
            method1.getDeclaringClass().getTypeName(),
            method1.getModifiers(),
            method1.getName(),
            method1.getExceptionTypes(),
            new Params(null, method1.getParameterTypes(), method1.getParameters()),
            method2,
            Long.class);
    assertNotEquals(method1ReturningLong, method2ReturningLong);

    // different return type, different method
    MethodSignature method1ReturningList =
        new MethodSignature(
            method1.getDeclaringClass(),
            method1.getDeclaringClass().getTypeName(),
            method1.getModifiers(),
            method1.getName(),
            method1.getExceptionTypes(),
            new Params(null, method1.getParameterTypes(), method1.getParameters()),
            method1,
            List.class);

    MethodSignature method2ReturningSet =
        new MethodSignature(
            method1.getDeclaringClass(),
            method1.getDeclaringClass().getTypeName(),
            method1.getModifiers(),
            method1.getName(),
            method1.getExceptionTypes(),
            new Params(null, method1.getParameterTypes(), method1.getParameters()),
            method2,
            Set.class);
    assertNotEquals(method1ReturningList, method2ReturningSet);
  }

  @Test
  public void testToString() {
    assertEquals(
        methodSignature.toString(),
        "MethodSignature{"
            + "declaringType="
            + methodSignature.getDeclaringType()
            + ", declaringTypeName="
            + methodSignature.getDeclaringTypeName()
            + ", name="
            + methodSignature.getName()
            + ", modifiers="
            + methodSignature.getModifiers()
            + ", exceptionTypes="
            + Arrays.toString(methodSignature.getExceptionTypes())
            + ", parameterNames="
            + Arrays.toString(methodSignature.getParameterNames())
            + ", parameterTypes="
            + Arrays.toString(methodSignature.getParameterTypes())
            + ", parameters="
            + Arrays.toString(methodSignature.getParameters())
            + ", method="
            + methodSignature.getMethod()
            + ", returnType="
            + methodSignature.getReturnType()
            + '}');
  }
}
