package net.ittera.pal.core.exec.java;

import static java.util.stream.Collectors.joining;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Arrays;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.Dispatcher;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.messages.Unwrapper;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForNonVoidInstanceMethodTest {
  private String value;

  ClassForNonVoidInstanceMethodTest() {}

  ClassForNonVoidInstanceMethodTest(String value) {
    this.value = value;
  }

  String floatAsString(float someFloat) {
    return String.valueOf(someFloat);
  }

  String toUpperCase() {
    return value.toUpperCase();
  }

  String append(String value) {
    if (value == null) {
      return this.value;
    }
    return this.value.concat(value);
  }

  String join(String joiner, String... values) {
    return Arrays.stream(values).collect(joining(joiner));
  }
}

@RunWith(MockitoJUnitRunner.class)
public class NonVoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {

  private Dispatcher dispatcher =
      new InstanceMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectStore);

  private Class targetClass = ClassForNonVoidInstanceMethodTest.class;

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    String methodName = "toUpperCase";
    Class[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    String value = "a lowercase string";
    Object target = new ClassForNonVoidInstanceMethodTest(value);
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(value.toUpperCase()));
  }

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() throws Exception {

    // create and store new instance
    String value = "a lowercase string";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "toUpperCase";
    Class[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(value.toUpperCase()));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "append";
    Class[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {"et"};

    // dispatch
    String value = "blank";
    Object target = new ClassForNonVoidInstanceMethodTest(value);
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertEquals(value + args[0], returned);
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "append";
    Class[] parameterTypes = {String.class};
    ObjectRef[] argObjRefs = {null};
    Object[] args = {"et"};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(value + args[0]));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // signature
    String methodName = "floatAsString";
    Class[] parameterTypes = {float.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    float floatArg = 238923.32f;
    Object[] args = {floatArg};

    // dispatch
    Object target = new ClassForNonVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(String.valueOf(floatArg)));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() throws Exception {
    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "floatAsString";
    Class[] parameterTypes = {float.class};
    float floatArg = 238923.32f;
    Object[] args = {floatArg};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(String.valueOf(floatArg)));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "append";
    Class[] parameterTypes = {String.class};
    Object[] args = {null};
    ObjectRef etObjRef = objectStore.storeObject("et");
    ObjectRef[] argObjRefs = {etObjRef};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(3L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is("blanket"));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() throws Exception {

    // create and store new instance
    String value = "blank";
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "append";
    Class[] parameterTypes = {String.class};
    Object[] args = {null};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(value));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // signature
    String methodName = "join";
    Class[] parameterTypes = {String.class, String[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    String[] parts = {"package", "class", "method"};
    String joiner = "::";
    Object[] args = {joiner, parts};

    // dispatch
    Object target = new ClassForNonVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is("package::class::method"));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() throws Exception {

    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "join";
    Class[] parameterTypes = {String.class, String[].class};
    String[] parts = {"package", "class", "method"};
    String joiner = "::";
    Object[] args = {joiner, parts};
    ObjectRef[] argObjRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is("package::class::method"));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "toUpperCase";
    Class[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    Object target = new ClassForNonVoidInstanceMethodTest();
    try {
      Object returned = dispatcher.dispatch(ctxt, this, target, args);
      fail("Should have thrown a NPE");
    } catch (NullPointerException npe) {
      // all good
    }
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    // create and store new instance
    ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "toUpperCase";
    Class[] parameterTypes = {};
    Object[] args = {};
    ObjectRef[] argObjRefs = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NullPointerException"));
  }
}
