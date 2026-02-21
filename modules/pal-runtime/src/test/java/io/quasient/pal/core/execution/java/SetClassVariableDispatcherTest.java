/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static io.quasient.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static io.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private final Class<?> targetClass = ClassForPutStaticTest.class;

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            objectLookupStore);
  }

  @After
  public void resetTestClassVariables() {
    ClassForPutStaticTest.resetStaticVars();
  }

  private <T> ProceedingJoinPoint createPjp(Field field, Object value, Callable<T> proceedCallback)
      throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindFieldSet()
        .fieldExecutionSignature(field)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(null) // static op
        .args(new Object[] {value})
        .proceedBehavior(proceedCallback)
        .build();
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "someShort";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    short newFieldValue = 987;

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.someShort = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "bytes";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    byte[] newFieldValue = "this is just a test".getBytes(StandardCharsets.UTF_8);

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.bytes = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "someBoolean";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Boolean newFieldValue = true;

    // ── pre-assertions ─────────────────────────────────────────
    assertFalse(ClassForPutStaticTest.someBoolean);

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.someBoolean = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "aString";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    String newFieldValue = "abnormally";

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.aString = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "aCollection";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    var newFieldValue = new ArrayDeque<>();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.aCollection = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── expect ───────────────────────────────────────────────
    String fieldName = "aCollection";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Object newFieldValue = null;

    // ── pre-assertions ─────────────────────────────────────────
    assertThat(ClassForPutStaticTest.aCollection, notNullValue());

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.aCollection = null;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "objects";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Object[] newFieldValue = {1, "a", false, 9283.95d};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.objects = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

    // ── signature ────────────────────────────────────────────
    String fieldName = "lastError";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Exception newFieldValue = new Exception("not working");

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForPutStaticTest.lastError = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
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

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests (#775)                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_withoutWalIncomingRpc_sendsOnlyAfter() throws Exception {
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledOnce();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(
                RunOptions.WITH_WAL,
                RunOptions.WITH_WAL_INCOMING_RPC,
                RunOptions.WITH_WAL_ALL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsOnlyAfter() throws Exception {
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
