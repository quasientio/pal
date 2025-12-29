/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.jsonrpc;

import static org.junit.Assert.assertEquals;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class JsonRpcMessageSummaryUtilTest {

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
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildConstructorCall("1", className, null);
    assertEquals("new ExampleClass", JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_instanceMethod() {
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    Integer instance = 9237239;
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildInstanceMethodCall(
            "1",
            className,
            methodName,
            instance,
            List.of(new Argument.Builder().withType("String").withValue("test").build()));
    assertEquals(
        "call ExampleClass." + methodName + "@" + instance,
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_staticMethod() {
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildClassMethodCall(
            "1",
            className,
            methodName,
            List.of(new Argument.Builder().withType("String").withValue("test").build()));
    assertEquals(
        "call ExampleClass." + methodName,
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_getStatic() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildStaticFieldGet("1", className, fieldName);
    assertEquals(
        "get ExampleClass." + fieldName,
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_putStatic() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    ObjectRef valueObjectRef = ObjectRef.randomRef();
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildStaticFieldPut(
            "1",
            className,
            fieldName,
            new Argument.Builder().withRef(valueObjectRef.getRef()).build());
    assertEquals(
        "put ExampleClass." + fieldName + " ⇦ " + "@" + valueObjectRef.getRef(),
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_getField() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef instance = ObjectRef.randomRef();
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildInstanceFieldGet("1", className, instance.getRef(), fieldName);
    assertEquals(
        "get ExampleClass." + fieldName + "@" + instance.getRef(),
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
  }

  @Test
  public void getOneLinerSummary_putField() {
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef instance = ObjectRef.randomRef();
    ObjectRef valueObjectRef = ObjectRef.randomRef();
    JsonRpcRequest jsonRpcRequest =
        JsonRpcMessageFactory.buildInstanceFieldPut(
            "1",
            className,
            instance.getRef(),
            fieldName,
            new Argument.Builder().withRef(valueObjectRef).build());
    assertEquals(
        "put ExampleClass."
            + fieldName
            + "@"
            + instance.getRef()
            + " ⇦ "
            + "@"
            + valueObjectRef.getRef(),
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcRequest));
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
    JsonRpcResponse jsonRpcResponse =
        messageBuilder.jsonRpcResponseFromExecMessageResponse(execMessage);
    assertEquals(
        "put_done " + getClassnameWithoutPackage(targetClass.getName()) + "." + fieldName,
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcResponse));
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
    JsonRpcResponse jsonRpcResponse =
        messageBuilder.jsonRpcResponseFromExecMessageResponse(execMessage);
    assertEquals(
        "put_done " + getClassnameWithoutPackage(targetClass.getName()) + "." + fieldName,
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcResponse));
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

    JsonRpcResponse jsonRpcResponse =
        messageBuilder.jsonRpcResponseFromExecMessageResponse(execMessage);
    assertEquals(
        "throw RuntimeException: \"" + throwableMessage + "\"",
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcResponse));
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

    JsonRpcResponse jsonRpcResponse =
        messageBuilder.jsonRpcResponseFromExecMessageResponse(execMessage);
    assertEquals("return void", JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcResponse));
  }

  @Test
  public void getOneLinerSummary_returnValueNonVoid() throws NoSuchMethodException {
    class DummyClass {
      @SuppressWarnings("unused")
      public int addInts(int a, int b) {
        return a + b;
      }
    }

    Class<?> targetClass = DummyClass.class;
    Method method = targetClass.getMethod("addInts", int.class, int.class);
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

    JsonRpcResponse jsonRpcResponse =
        messageBuilder.jsonRpcResponseFromExecMessageResponse(execMessage);
    assertEquals(
        "return "
            + method.getReturnType().getSimpleName()
            + "@"
            + returnValueObjRef.getRef()
            + "(="
            + returnValue
            + ")",
        JsonRpcMessageSummaryUtil.getOneLinerSummary(jsonRpcResponse));
  }
}
