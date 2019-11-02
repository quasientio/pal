package com.ittera.cometa.core.exec.java;

import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.MethodSignature;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForVoidClassMethodTest {
  public static boolean slept;
  public static Long millisSlept;
  static Object verified;

  static {
    __resetStaticVars();
  }

  static void sleep() {
    slept = true;
  }

  static void sleep(Long millis) {
    millisSlept = millis;
  }

  static void sleepUnboxed(long millis) {
    millisSlept = millis;
  }

  static void verify(Object toVerify) {
    verified = toVerify;
  }

  static void add(List<Long> sumContainer, long... parts) {
    // add it manually, (use streams for verification)
    long sum = 0;
    for (int i = 0; i < parts.length; i++) {
      sum += parts[i];
    }
    sumContainer.add(sum);
  }

  static void addPositive(List<Long> aList, long chunk) {
    if (chunk > 0) {
      aList.add(chunk);
    }
  }

  // call this method from unit tests to restore class variables that have been modified
  static void __resetStaticVars() {
    verified = "blah";
    slept = false;
    millisSlept = 0l;
  }
}

@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {

  private Dispatcher dispatcher =
      new ClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectService);

  private Class targetClass = ClassForVoidClassMethodTest.class;

  @After
  public void resetTestClassVariables() {
    ClassForVoidClassMethodTest.__resetStaticVars();
  }

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    String methodName = "sleep";
    Class[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    assertFalse(ClassForVoidClassMethodTest.slept);
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertTrue(ClassForVoidClassMethodTest.slept);
  }

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() {

    String methodName = "sleep";
    Class[] parameterTypes = {};
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertTrue(ClassForVoidClassMethodTest.slept);
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "sleep";
    Class[] parameterTypes = {Long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Long millisToSleep = 5L;
    Object[] args = {millisToSleep};

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    String methodName = "sleep";
    Class[] parameterTypes = {Long.class};
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    String methodName = "sleepUnboxed";
    Class[] parameterTypes = {long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    long millisToSleep = 5;
    Object[] args = {millisToSleep};

    // pre-assertion
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(0L));
    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    String methodName = "sleep";
    Class[] parameterTypes = {long.class};
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    String methodName = "sleep";
    Class[] parameterTypes = {Long.class};
    Long millisToSleep = 5l;
    ObjectRef objRef = objectService.storeObject(millisToSleep);
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.millisSlept, is(millisToSleep));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {

    String methodName = "verify";
    Class[] parameterTypes = {Object.class};
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(ClassForVoidClassMethodTest.verified, is(nullValue()));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {
    // signature
    String methodName = "add";
    Class[] parameterTypes = {List.class, long[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    long[] someNumbers = {10L, 20L, 30L};
    List<Long> sumContainer = new ArrayList();
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
    Class[] parameterTypes = {List.class, long[].class};
    long[] someNumbers = {10L, 20L, 30L};
    List<Long> sumContainer = new ArrayList();
    Object[] args = {null, someNumbers};
    ObjectRef[] argObjRefs = {objectService.storeObject(sumContainer), null};

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
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(sumContainer.get(0), is(LongStream.of(someNumbers).sum()));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "addPositive";
    Class[] parameterTypes = {List.class, long.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    long aNumber = 2;
    List<Long> aList = null;
    Object[] args = {aList, aNumber};

    // dispatch
    try {
      Object returned = dispatcher.dispatch(ctxt, this, null, args);
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
    Class[] parameterTypes = {List.class, long.class};
    List<Long> aList = null;
    Object[] args = {aList, 2};
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
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NullPointerException"));
  }
}
