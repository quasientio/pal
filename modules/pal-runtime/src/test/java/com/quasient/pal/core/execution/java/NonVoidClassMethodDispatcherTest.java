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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.common.lang.reflect.MethodSignature;
import com.quasient.pal.common.lang.reflect.Signature;
import com.quasient.pal.common.lang.reflect.Void;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Random;
import java.util.stream.DoubleStream;
import org.aspectj.lang.ProceedingJoinPoint;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForNonVoidClassMethodTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            onlyPublicReflectionHelper,
            objectLookupStore);
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
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ── ctxt ─────────────────────────────────────────────────
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // ── args ─────────────────────────────────────────────────
    Object[] args = {};

    // ── PJP ──────────────────────────────────────────────────
    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt)
            .sender(this) // "caller"
            .target(null) // static method
            .args(args)
            .build();

    // ── dispatch ─────────────────────────────────────────────
    Object returned =
        dispatcher.dispatch(ctxt, pjp, asProceed(ClassForNonVoidClassMethodTest::getRandomMinute));

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

    String methodName = "max";
    Class<?>[] parameterTypes = {Double.class, Double.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    double smallDouble = 8378d;
    double bigDouble = 827193d;
    Object[] args = {smallDouble, bigDouble};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt,
            pjp,
            asProceed(
                () -> ClassForNonVoidClassMethodTest.max((Double) args[0], (Double) args[1])));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(bigDouble));
  }

  /* ----------------------------------------------------------
   * 3.  dispatch_withPrimitiveArgs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    String methodName = "min";
    Class<?>[] parameterTypes = {double.class, double.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    double smallDouble = 8378;
    double bigDouble = 827193;
    Object[] args = {smallDouble, bigDouble};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt,
            pjp,
            asProceed(
                () -> ClassForNonVoidClassMethodTest.min((double) args[0], (double) args[1])));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(smallDouble));
  }

  /* ----------------------------------------------------------
   * 4.  dispatch_varargs_ok
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    String methodName = "max";
    Class<?>[] parameterTypes = {double[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    double d1 = 837;
    double d2 = 8293;
    double d3 = 137193;
    double d4 = 8287193;
    double[] varargs = {d1, d2, d3, d4};
    Object[] args = {varargs};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    Object returned =
        dispatcher.dispatch(
            ctxt, pjp, asProceed(() -> ClassForNonVoidClassMethodTest.max(varargs)));

    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(d4));
  }

  /* ----------------------------------------------------------
   * 5.  dispatch_throwsException_exceptionThrown
   * ---------------------------------------------------------- */
  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    String methodName = "divBy";
    Class<?>[] parameterTypes = {int.class, int.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    int number = 8378;
    int divisor = 0;
    Object[] args = {number, divisor};

    ProceedingJoinPoint pjp =
        PjpBuilder.forContext(ctxt).sender(this).target(null).args(args).build();

    try {
      dispatcher.dispatch(
          ctxt,
          pjp,
          asProceed(
              () -> {
                throw new ArithmeticException("/ by zero");
              }));
      fail("Should have failed with a div by zero overflow");
    } catch (ArithmeticException ae) {
      // expected
    }

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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    assertThat(responseMessage.getReturnValue(), is(nullValue()));
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher)
            .dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);
    assertThat(responseMessage.getReturnValue(), is(nullValue()));
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

  // auxiliary class
  @SuppressWarnings("unused")
  private static class ClassForNonVoidClassMethodTest {
    private static final Random random = new Random();

    static short getRandomMinute() {
      return (short) random.nextInt(60);
    }

    static Double max(Double a, Double b) {
      return Math.max(a, b);
    }

    static double max(double... doubles) {
      return DoubleStream.of(doubles).max().orElseThrow();
    }

    protected static double min(double a, double b) {
      return Math.min(a, b);
    }

    @SuppressWarnings("NarrowCalculation")
    static double divBy(int number, int divisor) {
      return number / divisor;
    }

    public static int somePublicMethod() {
      return add(4, 5);
    }

    private static Integer add(Integer a, Integer b) {
      if (a == null) {
        return b;
      }
      if (b == null) {
        return a;
      }
      return a + b;
    }
  }
}
