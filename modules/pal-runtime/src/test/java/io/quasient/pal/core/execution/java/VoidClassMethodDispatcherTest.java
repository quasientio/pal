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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.testfixtures.dispatch.ClassForVoidClassMethodTest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.LongStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForVoidClassMethodTest.class;

  @After
  public void resetTestClassVariables() {
    ClassForVoidClassMethodTest.resetStaticVars();
  }

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);
    wireRpcPolicyChecker(dispatcher);
  }

  private <T> ProceedingJoinPoint createPjp(
      Method method, Object[] args, Callable<T> proceedCallback) throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindMethodCall()
        .methodExecutionSignature(method)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(null) // static method
        .args(args)
        .proceedBehavior(proceedCallback)
        .build();
  }

  /* --------------------------------------------*/
  /*             Dispatcher interface            */
  /* --------------------------------------------*/
  /* ----------------------------------------------------------
   * 1.  dispatch_noArgs_ok           (void, no parameters)
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "sleep";
    Class<?>[] parameterTypes = {};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {};

    // ── pre-assertions ────────────────────────────────────────
    assertFalse(ClassForVoidClassMethodTest.slept);

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForVoidClassMethodTest.sleep();
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertTrue(ClassForVoidClassMethodTest.slept);
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_withArgs_ok         (void, boxed Long)
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "sleep";
    Class<?>[] parameterTypes = {Long.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Long millisToSleep = 5L;
    Object[] args = {millisToSleep};

    // ── pre-assertions ─────────────────────────────────────────────
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForVoidClassMethodTest.sleep(millisToSleep);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok (void, primitive long)
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "sleepUnboxed";
    Class<?>[] parameterTypes = {long.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    long millisToSleep = 5;
    Object[] args = {millisToSleep};

    // ── pre-assertions ────────────────────────────────────────
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForVoidClassMethodTest.sleepUnboxed(millisToSleep);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok          (void, var-args long[])
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "add";
    Class<?>[] parameterTypes = {List.class, long[].class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    long[] someNumbers = {10L, 20L, 30L};
    List<Long> sumContainer = new ArrayList<>();
    Object[] args = {sumContainer, someNumbers};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForVoidClassMethodTest.add(sumContainer, someNumbers);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(sumContainer.size(), is(1));
    assertThat(sumContainer.get(0), is(LongStream.of(someNumbers).sum()));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown (void, throws)
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "addPositive";
    Class<?>[] parameterTypes = {List.class, long.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    Object[] args = {null, 2L}; // will trigger NPE in method

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          ClassForVoidClassMethodTest.addPositive(null, 2L);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    try {
      dispatcher.dispatch(pjp);
      fail("Should have thrown a NPE");
    } catch (NullPointerException npe) {
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

    String methodName = "sleep";
    Class<?>[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // dispatch
    assertFalse(ClassForVoidClassMethodTest.slept);
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertTrue(ClassForVoidClassMethodTest.slept);
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    String methodName = "sleep";
    Class<?>[] parameterTypes = {Long.class};
    ObjectRef[] argObjRefs = {null};
    Long millisToSleep = 5L;
    Object[] args = {millisToSleep};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));
    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    String methodName = "sleep";
    Class<?>[] parameterTypes = {long.class};
    ObjectRef[] argObjRefs = {null};
    long millisToSleep = 5L;
    Object[] args = {millisToSleep};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));
    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    String methodName = "sleep";
    Class<?>[] parameterTypes = {Long.class};
    Long millisToSleep = 5L;
    ObjectRef objRef = objectLookupStore.storeObject(millisToSleep);
    Object[] args = {null};
    ObjectRef[] argObjRefs = {objRef};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));
    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {

    String methodName = "verify";
    Class<?>[] parameterTypes = {Object.class};
    Object[] args = {null};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.verified, notNullValue());
    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.verified, is(nullValue()));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() {

    String methodName = "add";
    Class<?>[] parameterTypes = {List.class, long[].class};
    long[] someNumbers = {10L, 20L, 30L};
    List<Long> sumContainer = new ArrayList<>();
    Object[] args = {null, someNumbers};
    ObjectRef[] argObjRefs = {objectLookupStore.storeObject(sumContainer), null};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(sumContainer.get(0), is(LongStream.of(someNumbers).sum()));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {
    String methodName = "addPositive";
    Class<?>[] parameterTypes = {List.class, long.class};
    Object[] args = {null, 2};
    ObjectRef[] argObjRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

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
        is("java.lang.NullPointerException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {
    String methodName = "idontexist";
    Class<?>[] parameterTypes = {};
    Object[] args = {};
    ObjectRef[] argObjRefs = {};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

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
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String methodName = "sleep";
    Class<?>[] parameterTypes = {Long.class};
    ObjectRef[] argObjRefs = {null};
    Object[] args = {8888L};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String methodName = "sleep";
    Class<?>[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String methodName = "sleepUnboxed";
    Class<?>[] parameterTypes = {Long.TYPE};
    ObjectRef[] argObjRefs = {null};
    Object[] args = {23423L};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String methodName = "nap";
    Class<?>[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(parameterTypes),
            this,
            null,
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ExecMessageDispatcher walDispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    String methodName = "sleep";
    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(new Class<?>[] {}),
            this,
            null,
            new Object[] {},
            new ObjectRef[] {});

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
        new ClassMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    String methodName = "sleep";
    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(new Class<?>[] {}),
            this,
            null,
            new Object[] {},
            new ObjectRef[] {});

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ExecMessageDispatcher walDispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            EnumSet.of(
                RunOptions.WITH_WAL,
                RunOptions.WITH_WAL_INCOMING_RPC,
                RunOptions.WITH_WAL_ALL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    String methodName = "sleep";
    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(new Class<?>[] {}),
            this,
            null,
            new Object[] {},
            new ObjectRef[] {});

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
        new ClassMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            reflectionHelper,
            objectLookupStore);

    String methodName = "sleep";
    ExecMessage incomingMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            toNames(new Class<?>[] {}),
            this,
            null,
            new Object[] {},
            new ObjectRef[] {});

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }
}
