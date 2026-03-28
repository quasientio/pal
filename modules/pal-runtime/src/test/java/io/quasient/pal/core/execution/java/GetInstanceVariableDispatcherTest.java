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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.testfixtures.dispatch.ClassForGetFieldTest;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {
  private final Class<?> targetClass = ClassForGetFieldTest.class;

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid, runOptions, messageBuilder, outboundMessageGateway, objectLookupStore);
    wireRpcPolicyChecker(dispatcher);
  }

  private <T> ProceedingJoinPoint createPjp(Field field, Object target, Callable<T> proceedCallback)
      throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindFieldGet()
        .fieldExecutionSignature(field)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(target)
        .args(new Object[0])
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

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest(); // ← real receiver

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.someShort;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someShort));
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

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.bytes;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.bytes));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_wrapper_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_wrapper_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "someInteger";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.someInteger;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someInteger));
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

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.aString;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.aString));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_object_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_object_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "anObject";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.anObject;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.anObject));
  }

  /* ----------------------------------------------------------
   * 6.  dispatch_nullObject_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_nullObject_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "aNullClass";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.aNullClass;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(nullValue()));
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

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.objects;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.objects));
  }

  /* ----------------------------------------------------------
   * 8.  dispatch_throwable_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwable_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "lastError";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── target ─────────────────────────────────────────────────
    ClassForGetFieldTest target = new ClassForGetFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.lastError;
    ProceedingJoinPoint pjp = createPjp(field, target, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.lastError));
  }

  /* -------------------------------------------------------*/
  /*             ExecMessageDispatcher interface            */
  /* -------------------------------------------------------*/

  @Override
  @Test
  public void dispatchIncoming_primitive_ok() throws Exception {

    String fieldName = "someShort";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(target.someShort));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() throws Exception {

    String fieldName = "bytes";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    byte[] returned = (byte[]) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(target.bytes));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() throws Exception {

    String fieldName = "someInteger";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Integer returned =
        (Integer) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(target.someInteger));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() throws Exception {

    String fieldName = "aString";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(target.aString));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_object_ok() {

    String fieldName = "anObject";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Object returned =
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.anObject));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aNullClass";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertTrue(responseMessage.getReturnValue().getObject().getIsNull());
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_objectArray_ok() {

    String fieldName = "objects";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Object returned =
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.objects));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_throwable_ok() {

    String fieldName = "lastError";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Object returned =
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef()));
    assertThat(returned, is(target.lastError));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass), ComesFromReflectable.comesFrom(fieldName)));
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String fieldName = "someShort";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "someInteger";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aString";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "privateObjects";

    // create and store new instance
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL but without WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with WEBSOCKET_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(
                RunOptions.WITH_WAL,
                RunOptions.WITH_WAL_INCOMING_RPC,
                RunOptions.WITH_WAL_ALL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL and WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with LOG_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForGetFieldTest target = new ClassForGetFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    ExecMessage incomingMessage =
        messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName, targetObjRef);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }
}
