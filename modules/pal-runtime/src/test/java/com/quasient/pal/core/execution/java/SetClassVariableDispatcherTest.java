/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static com.quasient.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.quasient.pal.common.lang.reflect.FieldSignature;
import com.quasient.pal.common.lang.reflect.Signature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import org.aspectj.lang.ProceedingJoinPoint;
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
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            objectLookupStore);
  }

  @After
  public void resetTestClassVariables() {
    ClassForPutStaticTest.resetStaticVars();
  }

  /* --------------------------------------------*/
  /*             Dispatcher interface            */
  /* --------------------------------------------*/

  /* ----------------------------------------------------------
   * 1.  dispatch_primitive_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_primitive_ok() throws Throwable {

    String fieldName = "someShort";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    short newFieldValue = 987;
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt)
            .sender(this)
            .target(null) // static field
            .args(args) // value being written
            .build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.someShort = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_primitiveArray_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_primitiveArray_ok() throws Throwable {

    String fieldName = "bytes";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    byte[] newFieldValue = "this is just a test".getBytes(StandardCharsets.UTF_8);
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.bytes = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_wrapper_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_wrapper_ok() throws Throwable {

    String fieldName = "someBoolean";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    Boolean newFieldValue = true;
    Object[] args = {newFieldValue};

    assertFalse(ClassForPutStaticTest.someBoolean);

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.someBoolean = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_string_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_string_ok() throws Throwable {

    String fieldName = "aString";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    String newFieldValue = "abnormally";
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.aString = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_object_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_object_ok() throws Throwable {

    String fieldName = "aCollection";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    var newFieldValue = new ArrayDeque<>();
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.aCollection = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(newFieldValue, instanceOf(ArrayDeque.class));
    assertThat(ClassForPutStaticTest.aCollection, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 6.  dispatch_nullObject_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_nullObject_ok() throws Throwable {

    String fieldName = "aCollection";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    assertThat(ClassForPutStaticTest.aCollection, notNullValue());

    Object[] args = {null};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.aCollection = null));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.aCollection, is(nullValue()));
  }

  /* ----------------------------------------------------------
   * 7.  dispatch_objectArray_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_objectArray_ok() throws Throwable {

    String fieldName = "objects";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    Object[] newFieldValue = {1, "a", false, 9283.95d};
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.objects = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.objects, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 8.  dispatch_throwable_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  @SuppressWarnings("StaticAssignmentOfThrowable")
  public void dispatch_throwable_ok() throws Throwable {

    String fieldName = "lastError";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    Exception newFieldValue = new Exception("not working");
    Object[] args = {newFieldValue};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asVoidProceed(() -> ClassForPutStaticTest.lastError = newFieldValue));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForPutStaticTest.lastError, is(newFieldValue));
  }

  /* -------------------------------------------------------*/
  /*             ExecMessageDispatcher interface            */
  /* -------------------------------------------------------*/
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aCollection, sameInstance(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.aCollection, is(nullValue()));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.objects, sameInstance(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertThat(ClassForPutStaticTest.lastError, sameInstance(newFieldValue));
    assertThat(
        responseMessage.getStaticFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getStaticFieldPutDone().getStaticFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getStaticFieldPutDone());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getStaticFieldPutDone());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getStaticFieldPutDone());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
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
