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

import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import java.util.LinkedList;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForPutStaticTest {

  static {
    __resetStaticVars();
  }

  static short someShort;
  static byte[] bytes;
  static Boolean someBoolean;
  static String aString;
  static java.util.List aList;
  static Object[] objects;
  static Throwable lastError;

  static void __resetStaticVars() {
    someShort = 4;
    bytes = null;
    someBoolean = false;
    aString = "I am a normal string";
    aList = new java.util.ArrayList();
    objects = null;
    lastError = new Exception("dummy exception");
  }
}

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private Dispatcher dispatcher =
      new SetClassVariableDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectStore);

  private Class targetClass = ClassForPutStaticTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @After
  public void resetTestClassVariables() {
    ClassForPutStaticTest.__resetStaticVars();
  }

  @Override
  @Test
  public void dispatch_primitive_ok() throws Throwable {

    // signature
    String fieldName = "someShort";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    short newFieldValue = 987;
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() {

    String fieldName = "someShort";
    String fieldClassName = "short.class";
    short newFieldValue = 987;

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
    byte[] newFieldValue = "this is just a test".getBytes();
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() {

    String fieldName = "bytes";
    String fieldClassName = "byte[].class";
    byte[] newFieldValue = "this is just a test".getBytes();

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_wrapper_ok() throws Throwable {

    // signature
    String fieldName = "someBoolean";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    Boolean newFieldValue = true;
    Object[] args = {newFieldValue};
    assertFalse(ClassForPutStaticTest.someBoolean);
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() {

    String fieldName = "someBoolean";
    String fieldClassName = "Boolean.class";
    Boolean newFieldValue = true;

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch
    assertFalse(ClassForPutStaticTest.someBoolean);
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
    String newFieldValue = "abnormally";
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() {

    String fieldName = "aString";
    String fieldClassName = "String.class";
    String newFieldValue = "abnormally";

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_object_ok() throws Throwable {

    // signature
    String fieldName = "aList";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    LinkedList newFieldValue = new LinkedList();
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(newFieldValue, instanceOf(LinkedList.class));
    assertThat(ClassForPutStaticTest.aList, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() {

    String fieldName = "aList";
    LinkedList newFieldValue = new LinkedList();
    ObjectRef valueObjRef = objectStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aList, sameInstance(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_nullObject_ok() throws Throwable {

    // signature
    String fieldName = "aList";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    java.util.List newFieldValue = null;
    Object[] args = {newFieldValue};
    assertThat(ClassForPutStaticTest.aList, notNullValue());
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.aList, is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aList";
    LinkedList newFieldValue = null;

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, "List.class", newFieldValue);

    // dispatch
    assertThat(ClassForPutStaticTest.aList, notNullValue());
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aList, is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
    Object[] newFieldValue = {1, "a", false, 9283.95d};
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.objects, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";
    Object[] newFieldValue = {1, "a", false, 9283.95d};
    ObjectRef valueObjRef = objectStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.objects, sameInstance(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
    Exception newFieldValue = new Exception("not working");
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForPutStaticTest.lastError, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_throwable_ok() {

    String fieldName = "lastError";
    Exception newFieldValue = new Exception("not working");
    ObjectRef valueObjRef = objectStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.lastError, sameInstance(newFieldValue));
    assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
  }
}
