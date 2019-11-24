package com.ittera.cometa.core.exec.java;

import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForPutFieldTest {
  short someShort = 4;
  byte[] bytes;
  Long aLong = 8238l;
  String aString = "I am a normal string";
  java.util.List aList = new java.util.ArrayList();
  Object[] objects;
  Throwable lastError = new Exception("dummy exception");
}

@RunWith(MockitoJUnitRunner.class)
public class SetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private Dispatcher dispatcher =
      new SetInstanceVariableDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectStore);

  private Class targetClass = ClassForPutFieldTest.class;

  @Override
  @Test
  public void dispatch_primitive_ok() throws Throwable {

    // signature
    String fieldName = "someShort";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    short newFieldValue = 987;
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.someShort, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() {

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = "short.class";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.someShort, is(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_primitiveArray_ok() throws Throwable {

    // signature
    String fieldName = "bytes";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    byte[] newFieldValue = "bytes".getBytes();
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.bytes, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() {

    String fieldName = "bytes";
    byte[] newFieldValue = "bytes".getBytes();
    String fieldClassName = "byte[].class";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.bytes, is(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_wrapper_ok() throws Throwable {

    // signature
    String fieldName = "aLong";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    Long newFieldValue = 100000L;
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.aLong, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() {

    String fieldName = "aLong";
    Long newFieldValue = 100000L;
    String fieldClassName = "Long.class";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aLong, is(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_string_ok() throws Throwable {

    // signature
    String fieldName = "aString";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    String newFieldValue = "to string or not to";
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.aString, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() {

    String fieldName = "aString";
    String fieldClassName = "String.class";
    String newFieldValue = "to string or not to";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aString, is(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_object_ok() throws Throwable {

    // signature
    String fieldName = "aList";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    List newFieldValue = Arrays.asList(938, 3038, 948, 394);
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.aList, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() {

    String fieldName = "aList";
    List newFieldValue = Arrays.asList(938, 3038, 948, 394);
    ObjectRef newValueObjRef = objectStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aList, sameInstance(newFieldValue));
    assertEquals(newFieldValue, target.aList);
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_nullObject_ok() throws Throwable {

    // signature
    String fieldName = "aList";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    List newFieldValue = null;
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    assertThat(target.aList, notNullValue());
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.aList, is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aList";
    List newFieldValue = null;

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, "List.class", newFieldValue);

    // dispatch
    assertThat(target.aList, notNullValue());
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aList, is(nullValue()));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_objectArray_ok() throws Throwable {

    // signature
    String fieldName = "objects";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    Object[] newFieldValue = {1, "a", false};
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.objects, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";
    Object[] newFieldValue = {1, "a", false};
    ObjectRef newValueObjRef = objectStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.objects, sameInstance(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_throwable_ok() throws Throwable {

    // signature
    String fieldName = "lastError";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // dispatch
    Error newFieldValue = new Error("uuh ooooh");
    Object[] args = {newFieldValue};
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.lastError, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_throwable_ok() {

    String fieldName = "lastError";
    Error newFieldValue = new Error("uuh ooooh");
    ObjectRef newValueObjRef = objectStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.lastError, sameInstance(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }
}
