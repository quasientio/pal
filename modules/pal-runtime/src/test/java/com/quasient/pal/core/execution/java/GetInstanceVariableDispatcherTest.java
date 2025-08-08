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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {
  private final Class<?> targetClass = ClassForGetFieldTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            objectLookupStore);
    onlyPublicDispatcher =
        new GetInstanceVariableDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            objectLookupStore);
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

    ClassForGetFieldTest target = new ClassForGetFieldTest(); // ← real receiver

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt)
            .sender(this) // test class doing the access
            .target(target) // instance field ⇒ receiver
            .args(new Object[0]) // field get ⇒ no args
            .build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.someShort));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someShort));
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

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.bytes));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.bytes));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_wrapper_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_wrapper_ok() throws Throwable {

    String fieldName = "someInteger";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.someInteger));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.someInteger));
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

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.aString));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.aString));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_object_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_object_ok() throws Throwable {

    String fieldName = "anObject";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.anObject));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.anObject));
  }

  /* ----------------------------------------------------------
   * 6.  dispatch_nullObject_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_nullObject_ok() throws Throwable {

    String fieldName = "aNullClass";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.aNullClass));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(nullValue()));
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

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.objects));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(target.objects));
  }

  /* ----------------------------------------------------------
   * 8.  dispatch_throwable_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwable_ok() throws Throwable {

    String fieldName = "lastError";
    Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    ClassForGetFieldTest target = new ClassForGetFieldTest();

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(target).args(new Object[0]).build();

    Object returned = dispatcher.dispatch(ctxt, pjp, asProceed(() -> target.lastError));

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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNotNull(responseMessage.getRaisedThrowable());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNotNull(responseMessage.getRaisedThrowable());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertNotNull(responseMessage.getRaisedThrowable());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is(NoSuchFieldException.class.getName()));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    assertNull(responseMessage.getRaisedThrowable());
  }

  // auxiliary class
  @SuppressWarnings({"unused", "checkstyle:MemberName"})
  private static class ClassForGetFieldTest {
    public short someShort = 0;
    byte[] bytes;
    Integer someInteger;
    protected String aString = "I am a normal string";
    List<?> anObject = new ArrayList<>();
    Object[] objects = {1, "a", false};
    private final Object[] privateObjects = {0, "b", true};
    Throwable lastError = new Error("dummy error");
    Class<?> aNullClass;
  }
}
