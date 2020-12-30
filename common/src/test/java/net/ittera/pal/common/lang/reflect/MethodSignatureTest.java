/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.lang.reflect;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings("rawtypes")
public class MethodSignatureTest {
  static class DummyClass1 {
    public void OooO(int oO) {}
  }

  static class DummyClass2 {
    public void OooO(int oO) {}
  }

  static class DummyClassForMethodSignatureTest {
    public void nop(String str) {}
  }

  private Method method;
  private Class returnType;
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
    Method method1 = MethodSignatureTest.DummyClass1.class.getDeclaredMethod("OooO", int.class);
    MethodSignature signature1 = new MethodSignature(method1);
    MethodSignature signature11 = new MethodSignature(method1);

    Method method2 = MethodSignatureTest.DummyClass2.class.getDeclaredMethod("OooO", int.class);
    MethodSignature signature2 = new MethodSignature(method2);
    MethodSignature signature22 = new MethodSignature(method2);

    assertEquals(signature1, signature1);
    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    assertNotSame(signature2, signature22);
    assertEquals(signature2, signature22);
    assertEquals(signature2.hashCode(), signature22.hashCode());

    assertNotEquals(method1, method2);
    assertNotEquals(signature1, signature2);
    assertNotEquals(signature1.hashCode(), signature2.hashCode());

    assertNotEquals(signature1, null);
    assertNotEquals(signature1, "not a method");

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

  @Ignore("See comment in ConstructorSignatureTest.equalsContract().")
  @Test
  public void equalsContract() {
    // TODO
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
