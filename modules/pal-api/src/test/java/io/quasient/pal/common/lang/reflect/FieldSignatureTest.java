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

import java.lang.reflect.Field;
import org.junit.Before;
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
  private Class<?> fieldType;
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

    assertNotSame(signature1, signature11);
    assertEquals(signature1, signature11);
    assertEquals(signature1.hashCode(), signature11.hashCode());

    Field field2 = FieldSignatureTest.DummyClass2.class.getDeclaredField("floatingField");
    FieldSignature signature2 = new FieldSignature(field2);
    FieldSignature signature22 = new FieldSignature(field2);

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
