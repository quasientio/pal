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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.testfixtures.dispatch.ClassForConstructorTest;
import java.lang.reflect.Constructor;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractMethodDispatcherTest {

  private final Class<?> targetClass = ClassForConstructorTest.class;

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new ConstructorDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);
    wireRpcPolicyChecker(dispatcher);
  }

  private <T> ProceedingJoinPoint createPjp(
      Constructor<?> constructor, Object[] args, Callable<T> proceedCallback) throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindConstructorCall()
        .constructorExecutionSignature(constructor)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(null) // ctor ⇒ no target yet
        .args(args)
        .proceedBehavior(proceedCallback)
        .build();
  }

  /* --------------------------------------------*/
  /*             Dispatcher interface            */
  /* --------------------------------------------*/

  /* ----------------------------------------------------------
   * 1.  dispatch_noArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    Class<?>[] parameterTypes = {};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {}; // no-arg ctor

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = constructor::newInstance;
    ProceedingJoinPoint pjp = createPjp(constructor, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNotNull(returned);
    assertThat(returned, instanceOf(targetClass));
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_withArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    Class<?>[] parameterTypes = {Integer.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {459};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> constructor.newInstance(args);
    ProceedingJoinPoint pjp = createPjp(constructor, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).someInteger, is(args[0]));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    Class<?>[] parameterTypes = {boolean.class, long.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {true, 983309835L};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> constructor.newInstance(args);
    ProceedingJoinPoint pjp = createPjp(constructor, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).aLong, is((long) args[1] + 1));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    Class<?>[] parameterTypes = {String[].class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {new String[] {"hello ", "world", "!"}};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> constructor.newInstance(args);
    ProceedingJoinPoint pjp = createPjp(constructor, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).joinedVarArgs, is("hello world!"));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // ── signature ────────────────────────────────────────────
    Class<?>[] parameterTypes = {String.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {"49385InvalidNumber1001"}; // will trigger NumberFormatException

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> constructor.newInstance(args);
    ProceedingJoinPoint pjp = createPjp(constructor, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    try {
      dispatcher.dispatch(pjp);
      fail("Should have thrown a NumberFormatException");
    } catch (NumberFormatException nfe) {
      // expected
    }

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  /* -------------------------------------------------------*/
  /*             ExecMessageDispatcher interface            */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() {

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();

    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    Class<?>[] parameterTypes = {Integer.class};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();

    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    Class<?>[] parameterTypes = {boolean.class, long.class};
    Object[] args = {true, 983309835L};
    ObjectRef[] argRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(objRef)).aLong,
        is((long) args[1] + 1));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    Class<?>[] parameterTypes = {Integer.class};
    Integer arg = 459;
    ObjectRef objRef = objectLookupStore.storeObject(arg);
    Object[] args = {};
    ObjectRef[] argRefs = {objRef};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    ObjectRef retObjRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(retObjRef));
    assertThat(objectLookupStore.lookupObject(retObjRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(retObjRef)).someInteger, is(arg));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {

    Class<?>[] parameterTypes = {Integer.class};
    Object[] args = {null};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(objRef)).someInteger,
        is(nullValue()));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  public void dispatchIncoming_withNullArgsPrimitiveTypes_ok() {
    Class<?> targetClass = ClassForConstructorTest.class;
    Class<?>[] parameterTypes = {int.class};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() {

    Class<?>[] parameterTypes = {String[].class};
    Object[] args = new Object[1];
    args[0] =
        new String[] {"hello ", "world", "!"}; // varargs must be wrapped in array of expected type
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"49385InvalidNumber1001"};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NumberFormatException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {
    Class<?>[] parameterTypes = {String.class, Boolean.TYPE, Integer.class};
    Object[] args = {"woiwefoj", true, 459};
    ObjectRef[] argRefs = {null, null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE, Integer.TYPE};
    Object[] args = {459, 459};
    ObjectRef[] argRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE, Integer.TYPE, Integer.TYPE};
    Object[] args = {459, 459, 459};
    ObjectRef[] argRefs = {null, null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ExecMessageDispatcher walDispatcher =
        new ConstructorDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());
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
        new ConstructorDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ExecMessageDispatcher walDispatcher =
        new ConstructorDispatcher(
            peerUuid,
            EnumSet.of(
                RunOptions.WITH_WAL,
                RunOptions.WITH_WAL_INCOMING_RPC,
                RunOptions.WITH_WAL_ALL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());
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
        new ConstructorDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }
}
