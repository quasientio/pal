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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.testfixtures.dispatch.ClassForPutFieldTest;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class SetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

  private final Class<?> targetClass = ClassForPutFieldTest.class;

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid, runOptions, messageBuilder, outboundMessageGateway, objectLookupStore);
    wireRpcPolicyChecker(dispatcher);
  }

  private <T> ProceedingJoinPoint createPjp(
      Field field, Object target, Object value, Callable<T> proceedCallback) throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindFieldSet()
        .fieldExecutionSignature(field)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(target)
        .args(new Object[] {value})
        .proceedBehavior(proceedCallback)
        .build();
  }

  /* --------------------------------------------*/
  /*             Dispatcher interface            */
  /* --------------------------------------------*/

  /* ----------------------------------------------------------
   * 1.  dispatch_primitiveArray_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_primitiveArray_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "bytes";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    byte[] newFieldValue = "bytes".getBytes(StandardCharsets.UTF_8);

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.bytes = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.bytes, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_primitive_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_primitive_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "someShort";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    short newFieldValue = 987;

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.someShort = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.someShort, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_wrapper_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_wrapper_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "aLong";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Long newFieldValue = 100000L;

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.aLong = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.aLong, is(newFieldValue));
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
    String newFieldValue = "to string or not to";

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.aString = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.aString, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_object_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_object_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "aList";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    List<?> newFieldValue = Arrays.asList(938, 3038, 948, 394);

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.aList = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.aList, is(newFieldValue));
  }

  /* ----------------------------------------------------------
   * 6.  dispatch_nullObject_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_nullObject_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String fieldName = "aList";
    Field field = targetClass.getDeclaredField(fieldName);

    // ── value ──────────────────────────────────────────────────
    Object newFieldValue = null;

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── pre-assertions ─────────────────────────────────────────
    assertThat(target.aList, notNullValue());

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.aList = null;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.aList, is(nullValue()));
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
    Object[] newFieldValue = {1, "a", false};

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.objects = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.objects, is(newFieldValue));
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

    // ── value ──────────────────────────────────────────────────
    Error newFieldValue = new Error("uuh ooooh");

    // ── target ─────────────────────────────────────────────────
    ClassForPutFieldTest target = new ClassForPutFieldTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.lastError = newFieldValue;
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(field, target, newFieldValue, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.lastError, is(newFieldValue));
  }

  /* -------------------------------------------------------*/
  /*             ExecMessageDispatcher interface            */
  /* -------------------------------------------------------*/
  @Override
  @Test
  public void dispatchIncoming_primitive_ok() {

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = short.class.getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.someShort, is(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_primitiveArray_ok() {

    String fieldName = "bytes";
    byte[] newFieldValue = "bytes".getBytes(StandardCharsets.UTF_8);
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.bytes, is(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_wrapper_ok() {

    String fieldName = "aLong";
    Long newFieldValue = 100000L;
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aLong, is(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_string_ok() {

    String fieldName = "aString";
    String newFieldValue = "to string or not to";
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aString, is(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aList, sameInstance(newFieldValue));
    assertEquals(newFieldValue, target.aList);
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_nullObject_ok() {

    String fieldName = "aList";
    String fieldClassName = "java.util.List";

    // create and store new instance
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    assertThat(target.aList, notNullValue());

    ObjectRef targetObjRef = objectLookupStore.storeObject(target);
    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid, targetClass.getName(), fieldName, targetObjRef, fieldClassName, null);
    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.aList, is(nullValue()));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.objects, sameInstance(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertThat(target.lastError, sameInstance(newFieldValue));
    assertThat(
        responseMessage.getInstanceFieldPutDone(),
        allOf(comesFromClass(targetClass), comesFrom(fieldName)));
    assertThat(responseMessage.getInstanceFieldPutDone().getInstanceFieldPutId(), notNullValue());
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = short.class.getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getRaisedThrowable());
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aLong";
    Long newFieldValue = 98739L;
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aString";
    String newFieldValue = "I am a new string";
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String fieldName = "aPrivateString";
    String newFieldValue = "I am a new private string";
    String fieldClassName = newFieldValue.getClass().getName();

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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getInstanceFieldPutDone().getField().getName(), is(fieldName));
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL but without WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with WEBSOCKET_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new SetInstanceVariableDispatcher(
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
    String fieldClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL and WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with LOG_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForPutFieldTest target = new ClassForPutFieldTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new SetInstanceVariableDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            objectLookupStore);

    String fieldName = "someShort";
    short newFieldValue = 987;
    String fieldClassName = short.class.getName();
    ExecMessage incomingMessage =
        messageBuilder.buildPutObject(
            peerUuid,
            targetClass.getName(),
            fieldName,
            targetObjRef,
            fieldClassName,
            newFieldValue);

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }
}
