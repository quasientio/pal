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
