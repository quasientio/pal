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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForVoidInstanceMethodTest.class;

  @Before
  @Override
  public void setUp() {
    super.setUp();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
    dispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            runOptions,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            onlyPublicReflectionHelper,
            objectLookupStore);
  }

  private <T> ProceedingJoinPoint createPjp(
      Method method, Object target, Object[] args, Callable<T> proceedCallback) throws Throwable {
    String sourceFilename = "NotARealClass.java";
    return PjpBuilder.create()
        .kindMethodCall()
        .methodExecutionSignature(method)
        .source(/*file*/ sourceFilename, /*line*/ -1, /*within*/ this.getClass())
        .sender(this)
        .target(target)
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
    String methodName = "addHelloWorld";
    Class<?>[] parameterTypes = {};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {};

    // ── target instance ─────────────────────────────────────
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.addHelloWorld();
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.wordsCollected.size(), is(2));
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_withArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {"hello"};

    // ── target instance ─────────────────────────────────────
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.addWord((String) args[0]);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.wordsCollected.size(), is(1));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "addWords";
    Class<?>[] parameterTypes = {int.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    int numberOfWordsToAdd = 5;
    Object[] args = {numberOfWordsToAdd};

    // ── target instance ─────────────────────────────────────
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.addWords((int) args[0]);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.wordsCollected.size(), is(numberOfWordsToAdd));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "addWords";
    Class<?>[] parameterTypes = {String[].class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    String[] words = {"hey", "there", "!", "whats", "up", "?"};
    Object[] args = {words};

    // ── target instance ─────────────────────────────────────
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.addWords((String[]) args[0]);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNull(returned);
    assertThat(target.wordsCollected.size(), is(4));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {","}; // invalid word, will trigger IAE

    // ── target instance ─────────────────────────────────────
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          target.addWord((String) args[0]);
          return null;
        };
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    try {
      dispatcher.dispatch(pjp);
      fail("Should have failed with an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
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

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addHelloWorld";
    Class<?>[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(2));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"hello"};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(1));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {int.class};
    int numberOfWordsToAdd = 15;
    Object[] args = {numberOfWordsToAdd};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(numberOfWordsToAdd));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWordList";
    Class<?>[] parameterTypes = {List.class};
    List<String> wordList = Arrays.asList("the", "truth", "is", "out", "there");
    ObjectRef listObjRef = objectLookupStore.storeObject(wordList);
    Object[] args = {null};
    ObjectRef[] argObjRefs = {listObjRef};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(wordList.size()));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {List.class};
    Object[] args = {null};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(0));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {String[].class};
    String[] words = {"hey", "there", "!", "whats", "up", "?"};
    Object[] args = {words};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(responseMessage.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(4));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {","};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.IllegalArgumentException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    // we use a method name that exists but with wrong parameter types
    String methodName = "addWord";
    Object arg = 489;
    Class<?>[] parameterTypes = {arg.getClass()};
    Object[] args = {arg};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"hello"};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {Integer.TYPE};
    Object[] args = {4};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {String[].class};
    Object[] args = {new String[] {"hello", "world"}};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addHelloWorld";
    Class<?>[] parameterTypes = {};
    Object[] args = {};
    ObjectRef[] argObjRefs = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests (#775)                   */
  /* -------------------------------------------------------*/

  @Test
  @Ignore("Awaiting implementation in #776")
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    //        dispatcher created with these runOptions
    //        incomingMessage built via messageBuilder.buildInstanceMethod()
    //        channel = WEBSOCKET_RPC
    //
    // When: dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC)
    //
    // Then: outboundMessageGateway.sendExecMessage() called exactly 2 times
    //       first call with ExecPhase.BEFORE, second with ExecPhase.AFTER

    // TODO(#776): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #776")
  @Override
  public void dispatchIncoming_withoutWalIncomingRpc_sendsOnlyAfter() throws Exception {
    // Given: runOptions = {WITH_WAL} (no WITH_WAL_INCOMING_RPC)
    //        dispatcher created with these runOptions
    //        incomingMessage built via messageBuilder.buildInstanceMethod()
    //        channel = WEBSOCKET_RPC
    //
    // When: dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC)
    //
    // Then: outboundMessageGateway.sendExecMessage() called exactly 1 time (only AFTER)
    //       backward compatibility: without WITH_WAL_INCOMING_RPC, only AFTER is sent

    // TODO(#776): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #776")
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = false
    //        dispatcher created with these runOptions
    //        incomingMessage built via messageBuilder.buildInstanceMethod()
    //        channel = LOG_RPC
    //
    // When: dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC)
    //
    // Then: outboundMessageGateway.sendExecMessage() called exactly 2 times
    //       LOG_RPC included because WITH_WAL_ALL_INCOMING_RPC is set

    // TODO(#776): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #776")
  @Override
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsOnlyAfter() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC} (no WITH_WAL_ALL_INCOMING_RPC)
    //        dispatcher created with these runOptions
    //        incomingMessage built via messageBuilder.buildInstanceMethod()
    //        channel = LOG_RPC
    //
    // When: dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC)
    //
    // Then: outboundMessageGateway.sendExecMessage() called exactly 1 time (only AFTER)
    //       LOG_RPC excluded because WITH_WAL_ALL_INCOMING_RPC is not set

    // TODO(#776): Implement test logic
    fail("Not yet implemented");
  }

  // auxiliary class
  @SuppressWarnings("unused")
  private static class ClassForVoidInstanceMethodTest {
    public List<String> wordsCollected = new ArrayList<>();
    private static final String WORD_REGEX = "^\\w+$";

    ClassForVoidInstanceMethodTest() {}

    private void addHelloWorld() {
      wordsCollected.add("Hello");
      wordsCollected.add("World");
    }

    public void addWord(String word) {
      if (word == null) {
        return;
      }

      if (word.matches(WORD_REGEX)) {
        wordsCollected.add(word);
      } else {
        throw new IllegalArgumentException("Not a word: " + word);
      }
    }

    void addWords(int n) {
      for (int i = 0; i < n; i++) {
        addWord("again");
      }
    }

    protected void addWords(String... words) {
      Arrays.stream(words).filter(w -> w.matches(WORD_REGEX)).forEach(w -> wordsCollected.add(w));
    }

    void addWordList(List<String> wordList) {
      wordsCollected.addAll(wordList);
    }
  }
}
