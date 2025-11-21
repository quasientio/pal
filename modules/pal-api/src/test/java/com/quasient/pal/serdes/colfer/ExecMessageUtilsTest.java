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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class ExecMessageUtilsTest {

  private final UUID peerId = UUID.randomUUID();
  private final MessageBuilder messageBuilder = new MessageBuilder(peerId);

  @SuppressWarnings("unused")
  static class ClassForTest {
    public int testField;

    public void testMethod() {}

    public String nonVoidTestMethod() {
      return null;
    }
  }

  // <editor-fold desc="getClassname">
  @Test
  public void getClassname_constructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_instanceMethod() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(), className, "testMethod", ObjectRef.randomRef(), null, null);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_classMethod() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, "testMethod", null, null, null, null);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_getStatic() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, "testField");
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_getField() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, "testField", ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putStatic() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, "testField", ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putField() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_returnValue() throws NoSuchMethodException {
    Method method = ClassForTest.class.getMethod("nonVoidTestMethod");
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            "test", method, ObjectRef.randomRef(), false, UUID.randomUUID().toString());
    assertEquals(method.getReturnType().getName(), ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putObjectDone() {
    AccessibleObject accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertEquals(ClassForTest.class.getName(), ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putStaticDone() {
    AccessibleObject accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertEquals(ClassForTest.class.getName(), ExecMessageUtils.getClassname(execMessage));
  }

  // </editor-fold>

  // <editor-fold desc="getExecutableName">
  @Test
  public void getExecutableName_constructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    assertEquals("new", ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_instanceMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(), className, methodName, ObjectRef.randomRef(), null, null);
    assertEquals(methodName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_classMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, methodName, null, null, null, null);
    assertEquals(methodName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_getStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, fieldName);
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_getField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_putStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_putField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_returnValue() throws NoSuchMethodException {
    Method method = ClassForTest.class.getMethod("testMethod");
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            "test", method, ObjectRef.randomRef(), false, UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_putObjectDone() {
    Field accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_putStaticDone() {
    Field accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_raisedThrowable() throws NoSuchMethodException {
    Method method = ClassForTest.class.getMethod("testMethod");
    Throwable throwable = new Throwable("some message");
    ExecMessage execMessage =
        messageBuilder.buildAccessibleObjectThrowable(
            UUID.randomUUID(), method, throwable, UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  // </editor-fold>

  // <editor-fold desc="getFromExecutableName">

  @Test
  public void getFromExecutableName_putFieldDone() {
    Field field = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(), field, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    assertEquals(field.getName(), ExecMessageUtils.getFromExecutableName(execMessage));
  }

  @Test
  public void getFromExecutableName_putStaticDone() {
    Field field = ClassForTest.class.getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(), field, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    assertEquals(field.getName(), ExecMessageUtils.getFromExecutableName(execMessage));
  }

  @Test
  public void getFromExecutableName_returnValue() throws NoSuchMethodException {
    Method method = ClassForTest.class.getMethod("testMethod");
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            "test", method, ObjectRef.randomRef(), false, UUID.randomUUID().toString());
    assertEquals(method.getName(), ExecMessageUtils.getFromExecutableName(execMessage));
  }

  @Test
  public void getFromExecutableName_raisedThrowable() throws NoSuchMethodException {
    Method method = ClassForTest.class.getMethod("testMethod");
    Throwable throwable = new Throwable("some message");
    ExecMessage execMessage =
        messageBuilder.buildAccessibleObjectThrowable(
            UUID.randomUUID(), method, throwable, UUID.randomUUID().toString());
    assertEquals(method.getName(), ExecMessageUtils.getFromExecutableName(execMessage));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_constructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_instanceMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(), className, methodName, ObjectRef.randomRef(), null, null);
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_classMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, methodName, null, null, null, null);
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_getStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, fieldName);
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_getField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_putStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getFromExecutableName_putField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef(), ObjectRef.randomRef());
    ExecMessageUtils.getFromExecutableName(execMessage);
  }

  // </editor-fold>

  // <editor-fold desc="getParameterTypes">
  @Test
  public void getParameterTypes_Constructor() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildConstructor(
            UUID.randomUUID(), className, parameterTypes, args, null, null);

    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_instanceMethod() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(),
            className,
            "testMethod",
            ObjectRef.randomRef(),
            parameterTypes,
            args);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_classMethod() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, "testMethod", parameterTypes, null, null, args);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_classMethod_withRefParam_hasDeclaredType() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {String.class.getName(), Object.class.getName()};
    Object[] args = new Object[] {"x", null};
    ObjectRef[] argRefs = new ObjectRef[] {null, ObjectRef.randomRef()};

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(),
            className,
            "testMethod",
            parameterTypes,
            this,
            ObjectRef.randomRef(),
            args,
            argRefs);

    List<String> types = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(types);
    assertEquals(2, types.size());
    // by-value retains type; by-ref retains declared parameter type for builder methods
    assertTrue(types.contains(String.class.getName()));
    assertTrue(types.contains(Object.class.getName()));
  }

  @Test
  public void getParameterTypes_returnsEmptyListForEmptyConstructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertTrue(returnedTypes.isEmpty());
  }

  @Test
  public void getParameterTypes_returnsNullForUnsupported_FieldOps() {
    String className = "TestClass";
    String fieldName = "testField";

    // GetStatic
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, fieldName);

    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // GetField
    execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutStatic
    execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutField
    execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutFieldDone
    AccessibleObject accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutStaticDone
    accessibleObject = ClassForTest.class.getDeclaredFields()[0];
    execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));
  }

  @Test
  public void getParameterTypes_returnsNullForUnsupported_ReturnValue()
      throws NoSuchMethodException {
    AccessibleObject accessibleObject = ClassForTest.class.getMethod("testMethod");
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            "test", accessibleObject, ObjectRef.randomRef(), false, UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));
  }
  // </editor-fold>
}
