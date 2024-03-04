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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForPutFieldTest {
  public short someShort = 4;
  byte[] bytes;
  Long aLong = 8238L;
  protected String aString = "I am a normal string";
  private String aPrivateString = "I am a private string";
  List<?> aList = new ArrayList<>();
  Object[] objects;
  Throwable lastError = new Exception("dummy exception");
}

@RunWith(MockitoJUnitRunner.class)
public class SetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private final Class<?> targetClass = ClassForPutFieldTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  public void setUp() {
    super.setUp();
    dispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.FALSE.toString(),
            objectLookupStore);
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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

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
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    String fieldClassName = "[B";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

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
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

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
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

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
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    List<?> newFieldValue = Arrays.asList(938, 3038, 948, 394);
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
    List<?> newFieldValue = Arrays.asList(938, 3038, 948, 394);
    ObjectRef newValueObjRef = objectLookupStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // dispatch
    List<?> newFieldValue = null;
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
    List<?> newFieldValue = null;

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, "List.class", newFieldValue);

    // dispatch
    assertThat(target.aList, notNullValue());
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef newValueObjRef = objectLookupStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(replyMsg.getReturnValue());
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef newValueObjRef = objectLookupStore.storeObject(newFieldValue);

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, newValueObjRef);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(replyMsg.getReturnValue());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.lastError, sameInstance(newFieldValue));
    assertThat(replyMsg.getInstanceFieldPutDone(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = "short.class";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getRaisedThrowable());
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aLong";
    Long newFieldValue = 98739L;
    String fieldClassName = Long.class.getName();

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getInstanceFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aString";
    String newFieldValue = "I am a new string";
    String fieldClassName = String.class.getName();

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getInstanceFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aPrivateString";
    String newFieldValue = "I am a new private string";
    String fieldClassName = String.class.getName();

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getInstanceFieldPutDone());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(replyMsg.getRaisedThrowable());
  }
}
