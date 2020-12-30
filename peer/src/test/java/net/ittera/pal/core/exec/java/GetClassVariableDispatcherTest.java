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
import static org.hamcrest.Matchers.sameInstance;
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
import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForGetStaticTest {
  static short someShort = 4;
  static byte[] bytes = "Some".getBytes();
  static Integer someInteger = 965235;
  static String aString = "I am a normal string";
  static java.util.List anObject = new java.util.ArrayList();
  static Object[] objects = {1, "a", false};
  static Throwable lastError = new Exception("dummy exception");
  static java.util.Map aNullMap;
}

@RunWith(MockitoJUnitRunner.class)
public class GetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private Dispatcher dispatcher =
      new GetClassVariableDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectStore);

  private Class targetClass = ClassForGetStaticTest.class;

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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.someShort));
  }

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() throws Exception {

    String fieldName = "someShort";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.someShort));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.bytes));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() throws Exception {

    String fieldName = "bytes";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    byte[] returned = (byte[]) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.bytes));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.someInteger));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() throws Exception {

    String fieldName = "someInteger";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.someInteger));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.aString));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() throws Exception {

    String fieldName = "aString";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.aString));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.anObject));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() {

    String fieldName = "anObject";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Object returned =
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, sameInstance(ClassForGetStaticTest.anObject));
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
    String fieldName = "aNullMap";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aNullMap";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.objects));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    assertTrue(replyMsg.getReturnValue().getObject().hasRef());
    Object returned =
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, sameInstance(ClassForGetStaticTest.objects));
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
    Object returned = dispatcher.dispatch(ctxt, this, null, null);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(ClassForGetStaticTest.lastError));
  }

  @Override
  @Test
  public void dispatchIncoming_throwable_ok() {

    String fieldName = "lastError";

    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    assertTrue(replyMsg.getReturnValue().getObject().hasRef());
    Throwable returned =
        (Throwable)
            objectStore.lookupObject(
                ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
    assertThat(returned, is(ClassForGetStaticTest.lastError));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }
}
