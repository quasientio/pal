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

package net.ittera.pal.core.rpc.exec.java;

import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
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
    String methodName = "sleep";
    Class<?>[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    assertFalse(ClassForVoidClassMethodTest.slept);
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertTrue(ClassForVoidClassMethodTest.slept);
  }

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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "sleep";
    Class<?>[] parameterTypes = {Long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Long millisToSleep = 5L;
    Object[] args = {millisToSleep};

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    String methodName = "sleepUnboxed";
    Class<?>[] parameterTypes = {long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    long millisToSleep = 5;
    Object[] args = {millisToSleep};

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));
    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(net.ittera.pal.core.rpc.exec.java.Void.getInstance()));
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
  public void dispatch_varargs_ok() throws Throwable {
    // signature
    String methodName = "add";
    Class<?>[] parameterTypes = {List.class, long[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    long[] someNumbers = {10L, 20L, 30L};
    List<Long> sumContainer = new ArrayList<>();
    Object[] args = {sumContainer, someNumbers};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(sumContainer.size(), is(1));
    assertThat(sumContainer.get(0), is(LongStream.of(someNumbers).sum()));
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "addPositive";
    Class<?>[] parameterTypes = {List.class, long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {null, 2};

    // dispatch
    try {
      @SuppressWarnings("unused")
      Object unused = dispatcher.dispatch(ctxt, this, null, args);
      fail("Should have thrown a NPE");
    } catch (NullPointerException npe) {
      // all good
    }
    verifyDispatcherConnectorSendExecMessageCalledTwice();
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    verifyDispatcherConnectorSendExecMessageCalledOnce();
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
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

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(responseMessage.getReturnValue());
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(responseMessage.getReturnValue());
    assertNull(responseMessage.getRaisedThrowable());
  }

  // auxiliary class
  @SuppressWarnings({"unused", "MemberName"})
  private static class ClassForVoidClassMethodTest {
    public static boolean slept;
    public static Long millisSlept;
    static Object verified;

    static {
      resetStaticVars();
    }

    static void sleep() {
      slept = true;
    }

    public static void sleep(Long millis) {
      millisSlept = millis;
    }

    protected static void sleepUnboxed(long millis) {
      millisSlept = millis;
    }

    static void verify(Object toVerify) {
      verified = toVerify;
    }

    static void add(List<Long> sumContainer, long... parts) {
      // add it manually, (use streams for verification)
      long sum = 0;
      for (long part : parts) {
        sum += part;
      }
      sumContainer.add(sum);
    }

    static void addPositive(List<Long> someList, long chunk) {
      if (chunk > 0) {
        someList.add(chunk);
      }
    }

    private static void nap() {
      sleep(30 * 60 * 1000L);
    }

    // call this method from unit tests to restore class variables that have been modified
    static void resetStaticVars() {
      verified = "blah";
      slept = false;
      millisSlept = 0L;
    }
  }
}
