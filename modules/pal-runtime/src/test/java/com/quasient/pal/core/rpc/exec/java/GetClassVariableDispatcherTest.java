/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.common.lang.reflect.FieldSignature;
import com.quasient.pal.common.lang.reflect.Signature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private final Class<?> targetClass = ClassForGetStaticTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new GetClassVariableDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new GetClassVariableDispatcher(
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.someShort));
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Integer returned =
        (Integer) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.someInteger));
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(ClassForGetStaticTest.aString));
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Object returned =
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, sameInstance(ClassForGetStaticTest.anObject));
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertTrue(responseMessage.getReturnValue().getObject().getIsNull());
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNotNull(responseMessage.getReturnValue().getObject().getRef());
    Object returned =
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, sameInstance(ClassForGetStaticTest.objects));
    assertThat(
        responseMessage.getReturnValue(),
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNotNull(responseMessage.getReturnValue().getObject().getRef());
    Throwable returned =
        (Throwable)
            objectLookupStore.lookupObject(
                ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, is(ClassForGetStaticTest.lastError));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String fieldName = "someShort";
    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "someInteger";
    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchFieldException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aString";
    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchFieldException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "privateObjects";
    ExecMessage incomingMessage =
        messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchFieldException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  // auxiliary class
  @SuppressWarnings({"unused", "StaticAssignmentOfThrowable"})
  private static class ClassForGetStaticTest {
    public static short someShort = 4;
    static byte[] bytes = "Some".getBytes(StandardCharsets.UTF_8);
    static Integer someInteger = 965235;
    protected static String aString = "I am a normal string";
    static List<?> anObject = new ArrayList<>();
    static Object[] objects = {1, "a", false};
    private static final Object[] privateObjects = new Object[] {0, "b", true};
    static Throwable lastError = new Exception("dummy exception");
    static Map<?, ?> aNullMap;
  }
}
