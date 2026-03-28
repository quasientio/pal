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
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.JsonUtil;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;

/**
 * Edge case tests for {@link SetFieldDispatcher}.
 *
 * <p>This class tests edge cases and error handling of SetFieldDispatcher via a minimal test
 * subclass, covering field loading and error handling in value extraction.
 *
 * @see SetFieldDispatcher
 * @see SetFieldDispatcherTest
 */
public class SetFieldDispatcherEdgeTest {

  /** Sample class with fields for testing. */
  @SuppressWarnings("unused")
  static class Sample {
    @SuppressWarnings({"FieldCanBeLocal", "UnusedVariable"})
    private int hidden = 0;

    public int x = 0;
  }

  /** Sample class with final field for testing. */
  @SuppressWarnings("unused")
  static class SampleWithFinal {
    public final int constValue;

    SampleWithFinal(int value) {
      this.constValue = value;
    }
  }

  /** Minimal subclass to expose protected methods. */
  static class TestDispatcher extends SetFieldDispatcher {
    /**
     * Exposes loadAccessibleObject for testing.
     *
     * @param className the class name
     * @param fieldName the field name
     * @return the accessible object
     * @throws Exception if field cannot be loaded
     */
    public AccessibleObject load(String className, String fieldName) throws Exception {
      return loadAccessibleObject(className, fieldName);
    }

    /**
     * Exposes getValueFromMessage for testing.
     *
     * @param valueObject the value object
     * @param ref the object reference
     * @param ao the accessible object
     * @return the extracted value
     */
    public Object valueFrom(Obj valueObject, int ref, AccessibleObject ao) {
      return getValueFromMessage(valueObject, ref, ao);
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      return new ExecMessage();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    protected MessageType getAfterExecMessageType() {
      return MessageType.EXEC_PUT_FIELD_DONE;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_PUT_FIELD;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_PUT_FIELD;
    }
  }

  /** Test that loadAccessibleObject loads declared (non-public) fields. */
  @Test
  public void loadAccessibleObject_declaredField() throws Exception {
    TestDispatcher d = new TestDispatcher();

    AccessibleObject ao = d.load(Sample.class.getName(), "hidden");
    Field fld = (Field) ao;
    assertThat(fld.getName(), is("hidden"));
  }

  /** Test that getValueFromMessage throws IllegalArgumentException when class is not found. */
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getValueFromMessage_classNotFound_throwsIAE() throws Exception {
    TestDispatcher d = new TestDispatcher();
    Field fld = Sample.class.getDeclaredField("x");
    // Build Obj with bogus class name so Unwrapper.unwrapObject throws ClassNotFoundException
    Obj val = new Obj();
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName("com.no.such.Class");
    val.setClazz(clazz);
    val.setIsNull(false);
    // valueFrom should wrap CNFE into IAE
    assertThrows(IllegalArgumentException.class, () -> d.valueFrom(val, 0, fld));
  }

  /**
   * Test that invoke handles final fields correctly by using reflection.
   *
   * <p>Edge case: final fields require special handling within constructor context.
   */
  @Test
  public void testInvoke_finalField_usesReflection() throws Throwable {
    // Given: A ProceedingJoinPoint for a final field (inside constructor context)
    TestDispatcher dispatcher = new TestDispatcher();
    SampleWithFinal target = new SampleWithFinal(10);
    Field field = SampleWithFinal.class.getField("constValue");
    int newValue = 999;

    // Use PjpBuilder for proper PJP setup
    ProceedingJoinPoint pjp =
        PjpBuilder.create()
            .kindFieldSet()
            .fieldExecutionSignature(field)
            .source("SetFieldDispatcherEdgeTest.java", 0, getClass())
            .sender(this)
            .target(target)
            .args(new Object[] {newValue})
            // No proceed behavior needed - final fields are handled directly via reflection
            .build();

    // And: Arguments containing the value to set
    Object[] args = new Object[] {newValue};

    // When: invoke is called
    // Use reflection to access the protected invoke method
    Method invokeMethod =
        SetFieldDispatcher.class.getDeclaredMethod(
            "invoke", ProceedingJoinPoint.class, Object[].class);
    invokeMethod.setAccessible(true);
    Object result = invokeMethod.invoke(dispatcher, pjp, args);

    // Then: The field is set directly via reflection (field.setAccessible + field.set)
    assertThat(target.constValue, is(newValue));
    // And: null is returned (not delegating to super.invoke)
    assertThat(result, is(nullValue()));
  }

  /**
   * Test that getValueFromMessage extracts value using field type when Obj has no type info.
   *
   * <p>Edge case: fallback behavior when type information is missing.
   */
  @Test
  public void testGetValueFromMessage_noTypeInfo_usesFieldType() throws Exception {
    // Given: A message object (Obj) without type information (null or empty class name)
    TestDispatcher dispatcher = new TestDispatcher();
    Field field = Sample.class.getField("x");

    // Build Obj without type info but with a JSON value
    Obj valueObj = new Obj();
    valueObj.setValue(JsonUtil.MAPPER.writeValueAsString(42));
    valueObj.setClazz(null); // No type info
    valueObj.setIsNull(false);

    // When: getValueFromMessage is called
    Object result = dispatcher.valueFrom(valueObj, 0, field);

    // Then: The value is unwrapped using the field's declared type
    assertThat(result, is(notNullValue()));
    // And: The correct value is returned
    assertThat(result, is(42));
  }

  /**
   * Test that loadAccessibleObject correctly loads a public field.
   *
   * <p>Edge case: verifies public field access path.
   */
  @Test
  public void testLoadAccessibleObject_publicField_loadsSuccessfully() throws Exception {
    // Given: A class name and a public field name
    TestDispatcher dispatcher = new TestDispatcher();
    String className = Sample.class.getName();
    String fieldName = "x";

    // When: loadAccessibleObject is called
    AccessibleObject ao = dispatcher.load(className, fieldName);

    // Then: The public field is returned as AccessibleObject
    assertThat(ao, is(notNullValue()));
    Field fld = (Field) ao;
    assertThat(fld.getName(), is(fieldName));
    // And: No exception is thrown (we got here)
  }

  /** Test that loadAccessibleObject can load non-public fields. */
  @Test
  public void testLoadAccessibleObject_nonPublicField_succeeds() throws Exception {
    // Given: A class with a private field
    TestDispatcher dispatcher = new TestDispatcher();

    String className = Sample.class.getName();
    String fieldName = "hidden"; // private field

    // When: loadAccessibleObject is called with the private field name
    // Then: The field is found (no exception)
    AccessibleObject ao = dispatcher.load(className, fieldName);
    assertThat(ao, is(notNullValue()));
    assertThat(((Field) ao).getName(), is(fieldName));
  }

  /**
   * Test that getValueFromMessage returns null when Obj.isNull is true.
   *
   * <p>Edge case: null handling in value extraction.
   */
  @Test
  public void testGetValueFromMessage_objIsNull_returnsNull() throws Exception {
    // Given: An Obj with isNull = true
    TestDispatcher dispatcher = new TestDispatcher();
    Field field = Sample.class.getField("x");

    Obj valueObj = new Obj();
    valueObj.setIsNull(true);

    // When: getValueFromMessage is called
    Object result = dispatcher.valueFrom(valueObj, 0, field);

    // Then: null is returned
    assertThat(result, is(nullValue()));
  }

  /**
   * Test that loadAccessibleObject works with nested class fields.
   *
   * <p>Edge case: verifies class loading with nested class names.
   */
  @Test
  public void testLoadAccessibleObject_nestedClass_loadsSuccessfully() throws Exception {
    // Given: A nested class name and a field name
    TestDispatcher dispatcher = new TestDispatcher();
    String className = Sample.class.getName(); // This is already a nested class
    String fieldName = "x";

    // When: loadAccessibleObject is called
    AccessibleObject ao = dispatcher.load(className, fieldName);

    // Then: The field is loaded successfully
    assertThat(ao, is(notNullValue()));
    assertThat(((Field) ao).getName(), is(fieldName));
  }
}
