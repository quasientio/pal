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
package io.quasient.pal.serdes.colfer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.messages.Marshallable;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldGet;
import org.junit.Test;

public class ColferUtilsTest {

  static class ClassForColferUtilsTest {
    public int field;
  }

  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private Marshallable createMarshallable() throws NoSuchFieldException {
    // create signature
    FieldSignature signature =
        new FieldSignature(ClassForColferUtilsTest.class.getDeclaredField("field"));
    String sourceFile = "ColferUtilsTest.java";
    int lineNumber = 17;
    Class<?> withinType = ClassForColferUtilsTest.class;
    // create context
    Context context = new Context(sourceFile, lineNumber, withinType, signature);
    io.quasient.pal.messages.colfer.Class clazz =
        Wrapper.getWrappedClass(signature.getDeclaringType());

    // create marshallable message
    Field field =
        Wrapper.getWrappedField(
            signature.getFieldType(), signature.getName(), signature.getModifiers());
    io.quasient.pal.messages.colfer.Context ctxt =
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
  public void toBytes_NullInstanceFieldGet_emptyArray() {
    byte[] result = ColferUtils.toBytes(null);
    assertNotNull(result);
    assertEquals(0, result.length);
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
