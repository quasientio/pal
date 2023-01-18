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

package net.ittera.pal.core.exec.java;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromClass;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable;
import net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.Unwrapper;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForGetFieldTest {
  short someShort = 0;
  byte[] bytes;
  Integer someInteger;
  String aString = "I am a normal string";
  java.util.List anObject = new java.util.ArrayList();
  Object[] objects = {1, "a", false};
  Throwable lastError = new Error("dummy error");
  Class aNullClass;
}

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private Dispatcher dispatcher =
      new GetInstanceVariableDispatcher(
          peerUuid, messageBuilder, dispatcherConnector, objectStore, sessionStore);

  private Class targetClass = ClassForGetFieldTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Override
  @Test
  public void dispatch_primitive_ok() throws Throwable {

    // signature
    String fieldName = "someShort";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someShort));
  }

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() throws Exception {

    String fieldName = "someShort";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(target.someShort));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_primitiveArray_ok() throws Throwable {

    // signature
    String fieldName = "bytes";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.bytes));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() throws Exception {

    String fieldName = "bytes";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    byte[] returned = (byte[]) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(target.bytes));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_wrapper_ok() throws Throwable {

    // signature
    String fieldName = "someInteger";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someInteger));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() throws Exception {

    String fieldName = "someInteger";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(target.someInteger));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_string_ok() throws Throwable {

    // signature
    String fieldName = "aString";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.aString));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() throws Exception {

    String fieldName = "aString";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(target.aString));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_object_ok() throws Throwable {

    // signature
    String fieldName = "anObject";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.anObject));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() throws Exception {

    String fieldName = "anObject";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Object returned =
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.anObject));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_nullObject_ok() throws Throwable {

    // signature
    String fieldName = "aNullClass";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aNullClass";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    assertTrue(replyMsg.getReturnValue().getObject().getIsNull());
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_objectArray_ok() throws Throwable {

    // signature
    String fieldName = "objects";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.objects));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Object returned =
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.objects));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatch_throwable_ok() throws Throwable {

    // signature
    String fieldName = "lastError";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.lastError));
  }

  @Override
  @Test
  public void dispatchIncoming_throwable_ok() {

    String fieldName = "lastError";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Object returned =
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.lastError));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }
}
