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

import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromClass;
import net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable;
import net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.hamcrest.Matchers;
import org.junit.Test;
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
      new ConstructorDispatcher(
          peerUuid, messageBuilder, dispatcherConnector, objectStore, sessionStore);

  private Class targetClass = ClassForConstructorTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    Class[] parameterTypes = {};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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

    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(
        objectStore.containsObjectRef(
            ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())));
    assertThat(
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    Class[] parameterTypes = {Integer.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {459};

    // dispatch
    Object returned = dispatcher.dispatch(ctxt, this, null, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, notNullValue());
    assertThat(returned, instanceOf(targetClass));
    assertThat(((ClassForConstructorTest) returned).someInteger, is(args[0]));
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
    assertThat(objectStore.size(), is(1L));
    assertTrue(
        objectStore.containsObjectRef(
            ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())));
    assertThat(
        objectStore.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    Class[] parameterTypes = {boolean.class, long.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    assertThat(objectStore.size(), is(1L));
    assertTrue(objectStore.containsObjectRef(objRef));
    assertThat(objectStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectStore.lookupObject(objRef)).aLong, is((long) args[1] + 1));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    Class[] parameterTypes = {Integer.class};
    Integer arg = new Integer(459);
    ObjectRef objRef = objectStore.storeObject(arg);
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
    assertThat(objectStore.size(), is(2L));
    assertTrue(objectStore.containsObjectRef(retObjRef));
    assertThat(objectStore.lookupObject(retObjRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectStore.lookupObject(retObjRef)).someInteger, is(arg));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
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
    assertThat(objectStore.size(), is(1L));
    assertTrue(objectStore.containsObjectRef(objRef));
    assertThat(objectStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectStore.lookupObject(objRef)).someInteger, is(nullValue()));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
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
   * is(incomingMessage.getMessageUuid())); assertThat(objectStore.size(), is(1));
   * assertTrue(objectStore.containsObjectRef(replyMsg.getReturnValue().getObject().getRef()));
   * assertThat(objectStore.lookupObject(replyMsg.getReturnValue().getObject().getRef()),
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
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    assertThat(objectStore.size(), is(1L));
    assertTrue(objectStore.containsObjectRef(objRef));
    assertThat(objectStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(replyMsg.getReturnValue(), HasDeclaringClassOf.hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {
    // signature
    Class[] parameterTypes = {String.class};
    Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    assertThat(objectStore.size(), is(0L));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NumberFormatException"));
  }
}
