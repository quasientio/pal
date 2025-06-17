package com.quasient.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Test;

public class ExecMessageSummaryUtilTest {

  private final MessageBuilder messageBuilder = new MessageBuilder();

  private static String getClassnameWithoutPackage(String className) {
    if (className.contains(".")) {
      return className.substring(className.lastIndexOf('.') + 1);
    } else {
      return className;
    }
  }

  @Test
  public void getOneLinerSummary_constructor() {
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertEquals("new ExampleClass", ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_instanceMethod() {
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    String ref = "9237239";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
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
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String className = "org.cometera.example.ExampleClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
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
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    ExecMessage execMessage = messageBuilder.buildGetStatic(peerUuid, className, fieldName);
    assertEquals(
        "get ExampleClass." + fieldName, ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putStatic() {
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aStaticField";
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerUuid, className, fieldName, valueObjectRef);
    assertEquals(
        "put ExampleClass." + fieldName + " ⇦ " + "@" + valueObjectRef.getRef(),
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_getField() {
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef targetObjRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildGetObject(peerUuid, className, fieldName, targetObjRef);
    assertEquals(
        "get ExampleClass." + fieldName + "@" + targetObjRef.getRef(),
        ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_putField() {
    UUID peerUuid = UUID.randomUUID();
    String className = "org.cometera.example.ExampleClass";
    String fieldName = "aField";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutObject(peerUuid, className, fieldName, targetObjRef, valueObjectRef);
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
    UUID peerUuid = UUID.randomUUID();
    String fieldName = "myField";
    String instanceFieldPutUuid = UUID.randomUUID().toString();
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            peerUuid, accessibleObject, instanceFieldPutUuid, responseToId);
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
    UUID peerUuid = UUID.randomUUID();
    String fieldName = "aStaticField";
    String staticFieldPutUuid = UUID.randomUUID().toString();
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            peerUuid, accessibleObject, staticFieldPutUuid, responseToId);
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

    UUID peerUuid = UUID.randomUUID();
    Method method = DummyClass.class.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            peerUuid,
            null,
            method,
            returnValueObjRef,
            method.getReturnType() == void.class,
            responseToId);

    assertEquals("return void", ExecMessageSummaryUtil.getOneLinerSummary(execMessage));
  }

  @Test
  public void getOneLinerSummary_returnValueNonVoid() throws NoSuchMethodException {
    class DummyClass {
      @SuppressWarnings("unused")
      public int addInts(int a, int b) {
        return a + b;
      }
    }

    UUID peerUuid = UUID.randomUUID();
    Class<?> targetClass = DummyClass.class;
    Method method = targetClass.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    int returnValue = 4;
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            peerUuid,
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
