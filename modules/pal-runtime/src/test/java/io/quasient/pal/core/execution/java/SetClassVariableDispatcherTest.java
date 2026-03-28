/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.quasient.testfixtures.dispatch.ClassForPutStaticTest;
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
            peerUuid, runOptions, messageBuilder, outboundMessageGateway, objectLookupStore);
    wireRpcPolicyChecker(dispatcher);
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
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

    // dispatch
    ExecMessage responseMessage =
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

    // dispatch
    ExecMessage responseMessage =
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

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(responseMessage.getStaticFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests                   */
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
  public void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL but without WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with WEBSOCKET_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
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
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL and WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with LOG_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ExecMessageDispatcher walDispatcher =
        new SetClassVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldValueClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutStatic(
            peerUuid, targetClass.getName(), fieldName, fieldValueClassName, newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }
}
