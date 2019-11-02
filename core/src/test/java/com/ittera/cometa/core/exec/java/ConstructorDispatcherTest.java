package com.ittera.cometa.core.exec.java;

import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static java.util.stream.Collectors.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForConstructorTest {
  Integer someInteger;
  String joinedVarArgs;
  long aLong;

  ClassForConstructorTest() {}

  ClassForConstructorTest(boolean plusOne, long aLong) {
    this.aLong = plusOne ? aLong + 1 : aLong;
  }

  ClassForConstructorTest(Integer someInteger) {
    this.someInteger = someInteger;
  }

  ClassForConstructorTest(String aMalformedNumber) {
    this.someInteger = Integer.valueOf(aMalformedNumber);
  }

  ClassForConstructorTest(String... args) {
    this.joinedVarArgs = Arrays.stream(args).collect(joining());
  }
}

/**
 * TODO: - with remoteArgs - with with objectRefs - use ExecMessageAssertions for dispatchIncoming*
 * tests
 */
@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractMethodDispatcherTest {

  private Dispatcher dispatcher =
      new ConstructorDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectService);

  private Class targetClass = ClassForConstructorTest.class;

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    Class[] parameterTypes = {};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertNotNull(returned);
    assertThat(returned, instanceOf(targetClass));
  }

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() {

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(replyMsg.getFollowingUuid(), equalTo(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), equalTo(1));
    assertTrue(
        objectService.containsObjectRef(
            ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())));
    assertThat(
        objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    Class[] parameterTypes = {Integer.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {459};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).someInteger, equalTo(args[0]));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    Class[] parameterTypes = {Integer.class};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(
        objectService.containsObjectRef(
            ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())));
    assertThat(
        objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    Class[] parameterTypes = {boolean.class, long.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {true, 983309835l};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).aLong, is((long) args[1] + 1));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    Class[] parameterTypes = {boolean.class, long.class};
    Object[] args = {true, 983309835l};
    ObjectRef[] argRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    ObjectRef objRef = ObjectRef.from(replyMsg.getReturnValue().getObject().getRef());
    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(objectService.containsObjectRef(objRef));
    assertThat(objectService.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectService.lookupObject(objRef)).aLong,
        is((long) args[1] + 1));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    Class[] parameterTypes = {Integer.class};
    Integer arg = new Integer(459);
    ObjectRef objRef = objectService.storeObject(arg);
    Object[] args = {};
    ObjectRef[] argRefs = {objRef};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    ObjectRef retObjRef = ObjectRef.from(replyMsg.getReturnValue().getObject().getRef());
    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(2));
    assertTrue(objectService.containsObjectRef(retObjRef));
    assertThat(objectService.lookupObject(retObjRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectService.lookupObject(retObjRef)).someInteger, is(arg));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {

    Class[] parameterTypes = {Integer.class};
    Object[] args = {null};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    ObjectRef objRef = ObjectRef.from(replyMsg.getReturnValue().getObject().getRef());
    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(objectService.containsObjectRef(objRef));
    assertThat(objectService.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectService.lookupObject(objRef)).someInteger,
        is(nullValue()));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  /**
   * previous test but para type changed to primitive , failing now @Test public void
   * dispatchIncoming_withArgs_ok() {
   *
   * <p>Class targetClass = ClassForConstructorTest.class; Class[] parameterTypes = {int.class};
   * Object[] args = {459}; ObjectRef[] argRefs = {null};
   *
   * <p>String[] parameterTypesNamesArray = Arrays.stream(parameterTypes).map(p ->
   * p.getName()).collect(toList()). toArray(new String[0]);
   *
   * <p>ExecMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid,
   * targetClass.getName(), parameterTypesNamesArray, args, argRefs);
   *
   * <p>// dispatch ExecMessage replyMsg = dispatcher.dispatchIncoming(incomingMessage);
   *
   * <p>// expect verifyDispatcherCalledOnce(); assertThat(replyMessage.getFollowingUuid(),
   * is(incomingMessage.getMessageUuid())); assertThat(objectService.size(), is(1));
   * assertTrue(objectService.containsObjectRef(replyMsg.getReturnValue().getObject().getRef()));
   * assertThat(objectService.lookupObject(replyMsg.getReturnValue().getObject().getRef()),
   * instanceOf(targetClass)); }
   */
  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {
    // signature
    Class[] parameterTypes = {String[].class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = new Object[1];
    args[0] =
        new String[] {"hello ", "world", "!"}; // varargs must be wrapped in array of expected type

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).joinedVarArgs, is("hello world!"));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() {

    Class[] parameterTypes = {String[].class};
    Object[] args = new Object[1];
    args[0] =
        new String[] {"hello ", "world", "!"}; // varargs must be wrapped in array of expected type
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    ObjectRef objRef = ObjectRef.from(replyMsg.getReturnValue().getObject().getRef());
    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(1));
    assertTrue(objectService.containsObjectRef(objRef));
    assertThat(objectService.lookupObject(objRef), instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        allOf(comesFromClass(targetClass), comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {
    // signature
    Class[] parameterTypes = {String.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {"49385InvalidNumber1001"};

    // dispatch
    try {
      Object returned = dispatcher.dispatch(ctxt, this, null, args);
      fail("Should have thrown a NumberFormatException");
    } catch (NumberFormatException nfe) {
      // all good
    }

    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    Class[] parameterTypes = {String.class};
    Object[] args = {"49385InvalidNumber1001"};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectService.size(), is(0));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NumberFormatException"));
  }
}
