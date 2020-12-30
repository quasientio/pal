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

import java.lang.reflect.Field;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class FieldSignatureTest {

  static class DummyClass1 {
    float floatingField;
  }

  static class DummyClass2 {
    float floatingField;
  }

  static class DummyClass3 {
    int intField;
  }

  static class DummyClassForFieldSignatureTest {
    int myField;
  }

  private Field field;
  private Class fieldType;
  private FieldSignature fieldSignature;

  @Before
  public void setUp() throws Exception {
    field = DummyClassForFieldSignatureTest.class.getDeclaredField("myField");
    fieldType = field.getType();
    fieldSignature = new FieldSignature(field);
  }

  @Test
  public void getField() {
    assertEquals(field, fieldSignature.getField());
  }

  @Test
  public void getFieldType() {
    assertEquals(fieldType, fieldSignature.getFieldType());
  }

  @Test
  public void testEqualsAndHashCode() throws NoSuchFieldException {
    Field field1 = FieldSignatureTest.DummyClass1.class.getDeclaredField("floatingField");
    FieldSignature signature1 = new FieldSignature(field1);
    FieldSignature signature11 = new FieldSignature(field1);

    Field field2 = FieldSignatureTest.DummyClass2.class.getDeclaredField("floatingField");
    FieldSignature signature2 = new FieldSignature(field2);
    FieldSignature signature22 = new FieldSignature(field2);

    assertEquals(signature1, signature1);
    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    assertNotSame(signature2, signature22);
    assertEquals(signature2, signature22);
    assertEquals(signature2.hashCode(), signature22.hashCode());

    assertNotEquals(field1, field2);
    assertNotEquals(signature1, signature2);
    assertNotEquals(signature1.hashCode(), signature2.hashCode());

    Field field3 = FieldSignatureTest.DummyClass3.class.getDeclaredField("intField");
    FieldSignature signature3 = new FieldSignature(field3);
    assertNotEquals(signature1, signature3);

    assertNotEquals(signature1, null);
    assertNotEquals(signature1, "silly lilly");
  }

  @Ignore("See comment in ConstructorSignatureTest.equalsContract().")
  @Test
  public void equalsContract() {
    // TODO
  }

  @Test
  public void testToString() {
    assertEquals(
        fieldSignature.toString(),
        "FieldSignature{"
            + "declaringType="
            + fieldSignature.getDeclaringType()
            + ", declaringTypeName="
            + fieldSignature.getDeclaringTypeName()
            + ", name="
            + fieldSignature.getName()
            + ", modifiers="
            + fieldSignature.getModifiers()
            + ", field="
            + fieldSignature.getField()
            + ", fieldType="
            + fieldSignature.getFieldType()
            + '}');
  }
}
