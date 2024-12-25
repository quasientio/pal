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

package net.ittera.pal.core.rpc.exec.java;

import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private final Class<?> targetClass = ClassForPutStaticTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.FALSE.toString(),
            objectLookupStore);
  }

  @After
  public void resetTestClassVariables() {
    ClassForPutStaticTest.resetStaticVars();
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
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() {

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    byte[] newFieldValue = "this is just a test".getBytes(StandardCharsets.UTF_8);
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() {

    String fieldName = "bytes";
    String fieldClassName = "[B";
    byte[] newFieldValue = "this is just a test".getBytes(StandardCharsets.UTF_8);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() {

    String fieldName = "someBoolean";
    Boolean newFieldValue = true;
    String fieldValueClassName = newFieldValue.getClass().getName();

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    // dispatch
    assertFalse(ClassForPutStaticTest.someBoolean);
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() {

    String fieldName = "aString";
    String newFieldValue = "abnormally";
    String fieldValueClassName = newFieldValue.getClass().getName();

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_object_ok() throws Throwable {

    // signature
    String fieldName = "aCollection";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    var newFieldValue = new ArrayDeque<>();
    Object[] args = {newFieldValue};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(newFieldValue, instanceOf(ArrayDeque.class));
    assertThat(ClassForPutStaticTest.aCollection, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() {

    String fieldName = "aCollection";
    ArrayDeque<?> newFieldValue = new ArrayDeque<>();
    ObjectRef valueObjRef = objectLookupStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aCollection, sameInstance(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatch_nullObject_ok() throws Throwable {

    // signature
    String fieldName = "aCollection";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    assertThat(ClassForPutStaticTest.aCollection, notNullValue());
    Object[] args = {null};
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.aCollection, is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aCollection";
    String valueClassName = "java.util.List";

    assertThat(ClassForPutStaticTest.aCollection, notNullValue());
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, valueClassName, null);
    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aCollection, is(nullValue()));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForPutStaticTest.objects, is(newFieldValue));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";
    Object[] newFieldValue = {1, "a", false, 9283.95d};
    ObjectRef valueObjRef = objectLookupStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.objects, sameInstance(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ObjectRef valueObjRef = objectLookupStore.storeObject(newFieldValue);

    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.lastError, sameInstance(newFieldValue));
    assertThat(
        replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "someBoolean";
    String fieldClassName = Boolean.class.getName();
    Boolean newFieldValue = true;
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getStaticFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aString";
    String fieldClassName = String.class.getName();
    String newFieldValue = "snafulupagus";
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getStaticFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "secretString";
    String fieldClassName = String.class.getName();
    String newFieldValue = "snafulupagus";
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldClassName, newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getStaticFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  // auxiliary class
  @SuppressWarnings({"unused", "StaticAssignmentOfThrowable"})
  private static class ClassForPutStaticTest {

    static {
      resetStaticVars();
    }

    public static short someShort;
    static byte[] bytes;
    static Boolean someBoolean;
    protected static String aString;
    private static String secretString;
    static ArrayDeque<?> aCollection;
    static Object[] objects;
    static Throwable lastError;

    static void resetStaticVars() {
      someShort = 4;
      bytes = null;
      someBoolean = false;
      aString = "I am a normal string";
      aCollection = new ArrayDeque<>();
      objects = null;
      lastError = new Exception("dummy exception");
    }
  }
}
