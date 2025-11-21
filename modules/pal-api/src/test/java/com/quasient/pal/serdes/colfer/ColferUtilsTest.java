/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.lang.reflect.FieldSignature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.messages.Marshallable;
import com.quasient.pal.messages.colfer.Field;
import com.quasient.pal.messages.colfer.InstanceFieldGet;
import org.junit.Test;

public class ColferUtilsTest {

  static class ClassForColferUtilsTest {
    public int field;
  }

  private Marshallable createMarshallable() throws NoSuchFieldException {
    // create signature
    FieldSignature signature =
        new FieldSignature(ClassForColferUtilsTest.class.getDeclaredField("field"));
    String sourceFile = "ColferUtilsTest.java";
    int lineNumber = 17;
    Class<?> withinType = ClassForColferUtilsTest.class;
    // create context
    Context context = new Context(sourceFile, lineNumber, withinType, signature);
    com.quasient.pal.messages.colfer.Class clazz =
        Wrapper.getWrappedClass(signature.getDeclaringType());

    // create marshallable message
    Field field =
        Wrapper.getWrappedField(
            signature.getFieldType(), signature.getName(), signature.getModifiers());
    com.quasient.pal.messages.colfer.Context ctxt =
        Wrapper.getWrappedContext(context, this, ObjectRef.randomRef());
    return new InstanceFieldGet().withClazz(clazz).withField(field).withContext(ctxt);
  }

  @Test
  public void toBytes_InstanceFieldGet_byteArray() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    byte[] result = ColferUtils.toBytes(message);
    assertNotNull(result);
    assertThat(result.length, greaterThan(0));
  }

  @Test
  public void toBytes_NullInstanceFieldGet_null() {
    assertNull(ColferUtils.toBytes(null));
  }

  @Test
  public void toJson_Marshallable_NonPrettyJsonString() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    String json = ColferUtils.toJson(message);
    assertNotNull(json);
    assertFalse(json.contains("\n"));
    assertFalse(json.contains(" "));
  }

  @Test
  public void toJson_MarshallableWithPrettyPrint_PrettyJsonString() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    String prettyJson = ColferUtils.toJson(message, true);
    assertNotNull(prettyJson);
    assertTrue(prettyJson.contains("\n"));
    assertTrue(prettyJson.contains(" "));
  }

  @Test
  public void format_Marshallable_FormattedObject() throws NoSuchFieldException {
    Marshallable message = createMarshallable();
    Object formatted = ColferUtils.format(message);
    assertNotNull(formatted);
    assertEquals(ColferUtils.toJson(message, false), formatted.toString());
  }
}
