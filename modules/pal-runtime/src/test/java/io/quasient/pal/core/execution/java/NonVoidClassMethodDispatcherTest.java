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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.reflect.Void;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import io.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.testfixtures.dispatch.ClassForNonVoidClassMethodTest;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForNonVoidClassMethodTest.class;

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
   * 1.  dispatch_noArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "getRandomMinute";
    Class<?>[] parameterTypes = {};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = ClassForNonVoidClassMethodTest::getRandomMinute;
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(not(Void.getInstance())));
    assertTrue((short) returned >= 0 && (short) returned < 60);
  }

  /* ----------------------------------------------------------
   * 2.  dispatch_withArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "max";
    Class<?>[] parameterTypes = {Double.class, Double.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    double smallDouble = 8378d;
    double bigDouble = 827193d;
    Object[] args = {smallDouble, bigDouble};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> ClassForNonVoidClassMethodTest.max((Double) args[0], (Double) args[1]);
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(bigDouble));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "min";
    Class<?>[] parameterTypes = {double.class, double.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    double smallDouble = 8378;
    double bigDouble = 827193;
    Object[] args = {smallDouble, bigDouble};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> ClassForNonVoidClassMethodTest.min((double) args[0], (double) args[1]);
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(smallDouble));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "max";
    Class<?>[] parameterTypes = {double[].class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    double d1 = 837;
    double d2 = 8293;
    double d3 = 137193;
    double d4 = 8287193;
    double[] varargs = {d1, d2, d3, d4};
    Object[] args = {varargs};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback = () -> ClassForNonVoidClassMethodTest.max(varargs);
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    Object returned = dispatcher.dispatch(pjp);

    // ── expect ───────────────────────────────────────────────
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(d4));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // ── signature ────────────────────────────────────────────
    String methodName = "divBy";
    Class<?>[] parameterTypes = {int.class, int.class};
    Method m = targetClass.getDeclaredMethod(methodName, parameterTypes);

    // ── args ──────────────────────────────────────────────────
    int number = 8378;
    int divisor = 0;
    Object[] args = {number, divisor};

    // ── PJP ──────────────────────────────────────────────────
    Callable<Object> proceedCallback =
        () -> {
          throw new ArithmeticException("/ by zero");
        };
    ProceedingJoinPoint pjp = createPjp(m, args, proceedCallback);

    // ── dispatch ─────────────────────────────────────────────
    try {
      dispatcher.dispatch(pjp);
      fail("Should have failed with a div by zero overflow");
    } catch (ArithmeticException ae) {
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
  public void dispatchIncoming_noArgs_ok() throws Exception {

    String methodName = "getRandomMinute";
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

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertTrue(returned >= 0 && returned < 60);
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() throws Exception {

    String methodName = "max";
    Class<?>[] parameterTypes = {Double.class, Double.class};
    double smallDouble = 8378d;
    double bigDouble = 827193d;
    Object[] args = {smallDouble, bigDouble};
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
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Double returned = (Double) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(bigDouble));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() throws Exception {
    String methodName = "min";
    Class<?>[] parameterTypes = {double.class, double.class};
    double smallDouble = 8378;
    double bigDouble = 827193;
    Object[] args = {smallDouble, bigDouble};
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
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(smallDouble));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() throws Exception {

    String methodName = "max";
    Class<?>[] parameterTypes = {double.class, double.class};
    double smallDouble = 8378;
    double bigDouble = 827193;
    Object[] args = {null, null};
    ObjectRef[] argObjRefs = {
      objectLookupStore.storeObject(smallDouble), objectLookupStore.storeObject(bigDouble)
    };

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
    assertThat(objectLookupStore.size(), is(3L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(bigDouble));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() throws Exception {

    String methodName = "add";
    Integer realNumber = 6565;
    Class<?>[] parameterTypes = {Integer.class, Integer.class};
    Object[] args = {null, realNumber};
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
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    Integer returned =
        (Integer) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertThat(returned, is(realNumber));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() throws Exception {

    String methodName = "max";
    Class<?>[] parameterTypes = {double[].class};
    double d1 = 837;
    double d2 = 8293;
    double d3 = 137193;
    double d4 = 8287193;
    Object[] args = {new double[] {d1, d2, d3, d4}};
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

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageNeverCalled();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(responseMessage.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject());
    assertEquals(d4, returned, 0);
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    String methodName = "divBy";
    Class<?>[] parameterTypes = {int.class, int.class};
    int number = 8378;
    int divisor = 0;
    Object[] args = {number, divisor};
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
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.ArithmeticException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {

    String methodName = "phantomMethod";
    Class<?>[] parameterTypes = {int.class, int.class};
    Object[] args = {34, 56};
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
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    String methodName = "somePublicMethod";
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
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(responseMessage.getRaisedThrowable(), is(nullValue()));
    assertThat((int) Unwrapper.unwrapObject(responseMessage.getReturnValue().getObject()), is(9));
  }

  @Override
  @Test
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    String methodName = "getRandomMinute";
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
    String methodName = "min";
    Class<?>[] parameterTypes = {Double.TYPE, Double.TYPE};
    ObjectRef[] argObjRefs = {null, null};
    Object[] args = {23d, 44d};

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
    String methodName = "add";
    Class<?>[] parameterTypes = {Integer.TYPE, Integer.TYPE};
    ObjectRef[] argObjRefs = {null, null};
    Object[] args = {23, 44};

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

    String methodName = "getRandomMinute";
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

    String methodName = "getRandomMinute";
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

    String methodName = "getRandomMinute";
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

    String methodName = "getRandomMinute";
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
