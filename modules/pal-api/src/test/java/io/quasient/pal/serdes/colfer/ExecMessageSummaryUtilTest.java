/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Test;

public class ExecMessageSummaryUtilTest {

  private final UUID peerId = UUID.randomUUID();
  private final MessageBuilder messageBuilder = new MessageBuilder(peerId);

  private static String getClassnameWithoutPackage(String className) {
    if (className.contains(".")) {
      return className.substring(className.lastIndexOf('.') + 1);
    } else {
      return className;
    }
  }

  @Test
  public void getOneLinerSummary_constructor() {
    String className = "org.cometera.example.ExampleClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerId, className);
    assertEquals("new ExampleClass", ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_instanceMethod() {
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    String ref = "9237239";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerId,
            className,
            methodName,
            ObjectRef.from(ref),
            new String[] {"String"},
            new Object[] {"test"},
            new ObjectRef[] {null});
    assertEquals(
        "call ExampleClass." + methodName + "@" + ref,
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_staticMethod() {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerId,
            className,
            methodName,
            new String[] {"String"},
            sender,
            senderObjRef,
            new Object[] {"test"});
    assertEquals(
        "call ExampleClass." + methodName, ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_getStatic() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    ExecMessage execMessage = messageBuilder.buildGetStatic(peerId, className, fieldName);
    assertEquals(
        "get ExampleClass." + fieldName, ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putStatic() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerId, className, fieldName, valueObjectRef);
    assertEquals(
        "put ExampleClass." + fieldName + " ⇦ " + "@" + valueObjectRef.getRef(),
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_getField() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef targetObjRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildGetObject(peerId, className, fieldName, targetObjRef);
    assertEquals(
        "get ExampleClass." + fieldName + "@" + targetObjRef.getRef(),
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putField() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutObject(peerId, className, fieldName, targetObjRef, valueObjectRef);
    assertEquals(
        "put ExampleClass."
            + fieldName
            + "@"
            + targetObjRef.getRef()
            + " ⇦ "
            + "@"
            + valueObjectRef.getRef(),
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putFieldDone() throws NoSuchFieldException {
    class DummyClass {
      @SuppressWarnings("unused")
      Object myField;
    }

    var targetClass = DummyClass.class;
    String fieldName = "myField";
    String instanceFieldPutUuid = UUID.randomUUID().toString();
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            peerId, accessibleObject, instanceFieldPutUuid, responseToId);
    assertEquals(
        "put_done " + getClassnameWithoutPackage(targetClass.getName()) + "." + fieldName,
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putStaticFieldDone() throws NoSuchFieldException {
    class DummyClass {
      @SuppressWarnings("unused")
      static Object aStaticField;
    }

    var targetClass = DummyClass.class;
    String fieldName = "aStaticField";
    String staticFieldPutUuid = UUID.randomUUID().toString();
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            peerId, accessibleObject, staticFieldPutUuid, responseToId);
    assertEquals(
        "put_done " + getClassnameWithoutPackage(targetClass.getName()) + "." + fieldName,
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_throwable() throws NoSuchMethodException {
    class DummyClass {
      @SuppressWarnings("unused")
      public Object myMethod() {
        throw new RuntimeException("An error occurred");
      }
    }

    UUID peerUuid = UUID.randomUUID();
    String throwableMessage = "my throwable message";
    Throwable throwable = new RuntimeException(throwableMessage);
    AccessibleObject accessibleObject = DummyClass.class.getMethod("myMethod");
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildAccessibleObjectThrowable(
            peerUuid, accessibleObject, throwable, responseToId);
    assertEquals(
        "throw RuntimeException: \"" + throwableMessage + "\"",
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_returnValueVoid() throws NoSuchMethodException {
    class DummyClass {
      @SuppressWarnings("unused")
      public void addInts(int a, int b) {}
    }

    Method method = DummyClass.class.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            null, method, returnValueObjRef, method.getReturnType() == void.class, responseToId);

    assertEquals("return void", ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_returnValueNonVoid() throws NoSuchMethodException {
    class DummyAdder {
      @SuppressWarnings("unused")
      public int add(int a, int b) {
        return a + b;
      }
    }

    Class<?> targetClass = DummyAdder.class;
    Method method = targetClass.getMethod("add", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    int returnValue = 4;
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            returnValue,
            method,
            returnValueObjRef,
            method.getReturnType() == void.class,
            responseToId);

    assertEquals(
        "return "
            + method.getReturnType().getSimpleName()
            + "@"
            + returnValueObjRef.getRef()
            + "(="
            + returnValue
            + ")",
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }
}
