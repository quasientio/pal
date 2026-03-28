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
package io.quasient.pal.common.lang.reflect;

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
