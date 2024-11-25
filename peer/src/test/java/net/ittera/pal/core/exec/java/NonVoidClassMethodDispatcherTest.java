/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core.exec.java;

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

import java.util.Random;
import java.util.stream.DoubleStream;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromClass;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.Unwrapper;
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
            dispatcherConnector,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new ClassMethodDispatcher(
            peerUuid,
            messageBuilder,
            dispatcherConnector,
            Boolean.FALSE.toString(),
            onlyPublicReflectionHelper,
            objectLookupStore);
  }

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    String methodName = "getRandomMinute";
    Class<?>[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(not(Void.getInstance())));
    assertTrue((short) returned >= 0 && (short) returned < 60);
  }

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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertTrue(returned >= 0 && returned < 60);
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "max";
    Class<?>[] parameterTypes = {Double.class, Double.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    double smallDouble = 8378d;
    double bigDouble = 827193d;
    Object[] args = {smallDouble, bigDouble};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(bigDouble));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Double returned = (Double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(bigDouble));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    String methodName = "min";
    Class<?>[] parameterTypes = {double.class, double.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    double smallDouble = 8378;
    double bigDouble = 827193;
    Object[] args = {smallDouble, bigDouble};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(smallDouble));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(smallDouble));
    assertThat(
        replyMsg.getReturnValue(),
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(3L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(bigDouble));
    assertThat(
        replyMsg.getReturnValue(),
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(realNumber));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {
    // signature
    String methodName = "max";
    Class<?>[] parameterTypes = {double[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    double d1 = 837;
    double d2 = 8293;
    double d3 = 137193;
    double d4 = 8287193;
    double[] varargs = {d1, d2, d3, d4};
    Object[] args = {varargs};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(d4));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertFalse(replyMsg.getReturnValue().getIsVoid());
    double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertEquals(d4, returned, 0);
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "divBy";
    Class<?>[] parameterTypes = {int.class, int.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    int number = 8378;
    int divisor = 0;
    Object[] args = {number, divisor};

    // dispatch
    try {
      @SuppressWarnings("unused")
      Object unused = dispatcher.dispatch(ctxt, this, null, args);
      fail("Should have failed with a div by zero overflow");
    } catch (ArithmeticException ae) {
      // all good
    }
    verifyDispatcherConnectorSendExecMessageCalledTwice();
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getRaisedThrowable(), is(nullValue()));
    assertThat((int) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject()), is(9));
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);

    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getRaisedThrowable());
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getRaisedThrowable());
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getRaisedThrowable());
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
