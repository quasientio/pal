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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Before;
import org.junit.Ignore;
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

    Constructor constructor2 = DummyClass2.class.getDeclaredConstructor();
    ConstructorSignature signature2 = new ConstructorSignature(constructor2);
    ConstructorSignature signature22 = new ConstructorSignature(constructor2);

    assertEquals(signature1, signature1);
    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    assertNotSame(signature2, signature22);
    assertEquals(signature2, signature22);
    assertEquals(signature2.hashCode(), signature22.hashCode());

    assertNotEquals(constructor1, constructor2);
    assertNotEquals(signature1, signature2);
    assertNotEquals(signature1.hashCode(), signature2.hashCode());

    assertNotEquals(signature1, null);
    assertNotEquals(signature1, "silly string");
  }

  @Ignore("EqualsVerifier not working as expected.")
  @Test
  public void equalsContract() throws Exception {
    /* we keep getting an AssertionError with msg:
     *   Significant fields: equals relies on constructor, but hashCode does not.
     * TODO: create an issue and link to it in this TODO
     *  if this method can be fixed, the above equalsAndHashCode() will be redundant
     */
    class DummyClass {
      public void increment(int a, int b) {}
    }
    // EqualsVerifier requires prefabValues with actual instances of Parameter.
    // Since we can't instantiate Parameter, we use our DummyClass and reflection to get them.
    Method method = DummyClass.class.getDeclaredMethod("increment", int.class, int.class);
    Parameter parameter1 = method.getParameters()[0];
    Parameter parameter2 = method.getParameters()[1];
    EqualsVerifier.forClass(ConstructorSignature.class)
        .withPrefabValues(Parameter.class, parameter1, parameter2)
        .usingGetClass()
        .verify();
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
