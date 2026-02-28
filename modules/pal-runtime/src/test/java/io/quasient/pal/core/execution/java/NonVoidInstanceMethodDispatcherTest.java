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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("FloatingPointLiteralPrecision")
public class NonVoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForNonVoidInstanceMethodTest.class;

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
    String methodName = "toUpperCase";
    Class<?>[] parameterTypes = {};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {};

    // ── target instance ─────────────────────────────────────
    String value = "a lowercase string";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = target::toUpperCase;
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(value.toUpperCase(Locale.getDefault())));
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_withArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {"et"};

    // ── target instance ─────────────────────────────────────
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.append((String) args[0]);
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertEquals(value + args[0], returned);
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "floatAsString";
    Class<?>[] parameterTypes = {float.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    float floatArg = 238923.32f;
    Object[] args = {floatArg};

    // ── target instance ─────────────────────────────────────
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.floatAsString((float) args[0]);
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(String.valueOf(floatArg)));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "join";
    Class<?>[] parameterTypes = {String.class, String[].class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    String[] parts = {"package", "class", "method"};
    String joiner = "::";
    Object[] args = {joiner, parts};

    // ── target instance ─────────────────────────────────────
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> target.join((String) args[0], (String[]) args[1]);
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is("package::class::method"));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "toUpperCase";
    Class<?>[] parameterTypes = {};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {};

    // ── target instance ─────────────────────────────────────
    ClassForNonVoidInstanceMethodTest target =
        new ClassForNonVoidInstanceMethodTest(); // value == null

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = target::toUpperCase;
    ProceedingJoinPoint pjp = createPjp(m, target, args, proceedCallback);

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

  /* ----------------------------------------------------------
   * 6.  dispatch_throwsException_walAfterMessageIsExecThrowable
   * ---------------------------------------------------------- */
  @Test
  public void dispatch_throwsException_walAfterMessageIsExecThrowable() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "toUpperCase";
    Method m = targetClass.getDeclaredMethod(methodName);

    // ── target instance (value == null → NPE when called) ───
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();

    // ── PJP ──────────────────────────────────────────────────
    ProceedingJoinPoint pjp = createPjp(m, target, new Object[] {}, target::toUpperCase);

    // ── dispatch (expect NPE) ────────────────────────────────
    try {
      dispatcher.dispatch(pjp);
      fail("Should have thrown a NullPointerException");
    } catch (NullPointerException expected) {
      // expected
    }

    // ── capture the after-exec message sent to the gateway ──
    ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
    ArgumentCaptor<ExecPhase> phaseCaptor = ArgumentCaptor.forClass(ExecPhase.class);
    verify(outboundMessageGateway, times(2))
        .sendExecMessage(msgCaptor.capture(), phaseCaptor.capture());

    // Second call is the AFTER phase
    Message afterMessage = msgCaptor.getAllValues().get(1);
    ExecMessage afterExec = afterMessage.getExecMessage();
    assertThat(
        "After-exec message should be EXEC_THROWABLE when the method threw an exception",
        ExecMessageUtils.getMessageTypeOf(afterExec),
        is(MessageType.EXEC_THROWABLE));
    assertThat(afterExec.getRaisedThrowable(), is(notNullValue()));
    assertThat(afterExec.getReturnValue(), is(nullValue()));
    assertThat(
        afterExec.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NullPointerException"));
  }

  /* -------------------------------------------------------*/
  /*             ExecMessageDispatcher interface            */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() throws Exception {

    // create and store new instance
    String value = "a lowercase string";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "toUpperCase";
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
    assertThat(objectLookupStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(value.toUpperCase(Locale.getDefault())));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
    ObjectRef[] argObjRefs = {null};
    Object[] args = {"et"};

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
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(value + args[0]));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() throws Exception {
    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "floatAsString";
    Class<?>[] parameterTypes = {float.class};
    float floatArg = 238923.32f;
    Object[] args = {floatArg};
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
    assertThat(objectLookupStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(String.valueOf(floatArg)));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {null};
    ObjectRef etObjRef = objectLookupStore.storeObject("et");
    ObjectRef[] argObjRefs = {etObjRef};

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
    assertThat(objectLookupStore.size(), is(3L));
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is("blanket"));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
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
    assertThat(objectLookupStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(value));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() throws Exception {

    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "join";
    Class<?>[] parameterTypes = {String.class, String[].class};
    String[] parts = {"package", "class", "method"};
    String joiner = "::";
    Object[] args = {joiner, parts};
    ObjectRef[] argObjRefs = {null, null};

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
    String returned = (String) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is("package::class::method"));
    assertThat(
        responseMessage.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "toUpperCase";
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

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NullPointerException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {

    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    // we use a method that exists, but with the wrong number of parameters
    String methodName = "toUpperCase";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"et alia"};
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
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "floatAsString";
    Class<?>[] parameterTypes = {float.class};
    float floatArg = 238923.32f;
    Object[] args = {floatArg};
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

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "toUpperCase";
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

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"something else"};
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
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "join";
    Class<?>[] parameterTypes = {String.class, String[].class};
    Object[] args = {",", new String[] {"hello", "world!"}};
    ObjectRef[] argObjRefs = {null, null};

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
    assertThat(
        Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject()), is("hello,world!"));
    assertNull(responseMessage.getRaisedThrowable());
  }

  /* -------------------------------------------------------*/
  /*        WAL incoming RPC tests (#775)                   */
  /* -------------------------------------------------------*/

  @Test
  @Override
  public void dispatchIncoming_withWalIncomingRpc_sendsBothBeforeAndAfter() throws Exception {
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);

    String methodName = "toUpperCase";
    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(new Class<?>[] {}),
            new Object[] {},
            new ObjectRef[] {});

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Ignore("Awaiting implementation in #878")
  @Override
  public void dispatchIncoming_withoutWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL but without WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with WEBSOCKET_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);

    String methodName = "toUpperCase";
    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(new Class<?>[] {}),
            new Object[] {},
            new ObjectRef[] {});

    // TODO(#878): Remove @Ignore when AFTER message gating is implemented
    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  @Test
  @Override
  public void dispatchIncoming_logRpc_withWalAllIncomingRpc_sendsBothBeforeAndAfter()
      throws Exception {
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            EnumSet.of(
                RunOptions.WITH_WAL,
                RunOptions.WITH_WAL_INCOMING_RPC,
                RunOptions.WITH_WAL_ALL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);

    String methodName = "toUpperCase";
    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(new Class<?>[] {}),
            new Object[] {},
            new ObjectRef[] {});

    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Ignore("Awaiting implementation in #878")
  @Override
  public void dispatchIncoming_logRpc_withWalIncomingRpc_sendsNeither() throws Exception {
    // Given: A dispatcher configured with WITH_WAL and WITH_WAL_INCOMING_RPC
    // When: dispatchIncoming() is called with LOG_RPC channel
    // Then: Neither BEFORE nor AFTER messages are sent to the gateway (zero calls)
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest("a string");
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    ExecMessageDispatcher walDispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);

    String methodName = "toUpperCase";
    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(new Class<?>[] {}),
            new Object[] {},
            new ObjectRef[] {});

    // TODO(#878): Remove @Ignore when AFTER message gating is implemented
    walDispatcher.dispatchIncoming(incomingMessage, MessageChannelType.LOG_RPC);
    verifyDispatcherConnectorSendExecMessageNeverCalled();
  }

  // auxiliary class
  @SuppressWarnings("unused")
  private static class ClassForNonVoidInstanceMethodTest {
    private String value;

    ClassForNonVoidInstanceMethodTest() {}

    ClassForNonVoidInstanceMethodTest(String value) {
      this.value = value;
    }

    public String floatAsString(float someFloat) {
      return String.valueOf(someFloat);
    }

    String toUpperCase() {
      return value.toUpperCase(Locale.getDefault());
    }

    protected String append(String value) {
      if (value == null) {
        return this.value;
      }
      return this.value.concat(value);
    }

    private String join(String joiner, String... values) {
      return String.join(joiner, values);
    }
  }
}
