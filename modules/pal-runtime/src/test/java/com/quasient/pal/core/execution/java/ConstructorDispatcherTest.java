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
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.common.lang.reflect.ConstructorSignature;
import com.quasient.pal.common.lang.reflect.Signature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromClass;
import com.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.lang.reflect.Constructor;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// TODO: with remoteArgs
// TODO: with objectRefs
// TODO: use ExecMessageAssertions for dispatchIncoming tests
@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractMethodDispatcherTest {

  private final Class<?> targetClass = ClassForConstructorTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new ConstructorDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new ConstructorDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.FALSE.toString(),
            onlyPublicReflectionHelper,
            objectLookupStore);
  }

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    Class<?>[] parameterTypes = {};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);
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
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    Class<?>[] parameterTypes = {Integer.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);
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

    Class<?>[] parameterTypes = {Integer.class};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    Class<?>[] parameterTypes = {boolean.class, long.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {true, 983309835L};

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
    Class<?>[] parameterTypes = {boolean.class, long.class};
    Object[] args = {true, 983309835L};
    ObjectRef[] argRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(objRef)).aLong,
        is((long) args[1] + 1));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    Class<?>[] parameterTypes = {Integer.class};
    Integer arg = 459;
    ObjectRef objRef = objectLookupStore.storeObject(arg);
    Object[] args = {};
    ObjectRef[] argRefs = {objRef};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(2L));
    ObjectRef retObjRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(retObjRef));
    assertThat(objectLookupStore.lookupObject(retObjRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(retObjRef)).someInteger, is(arg));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {

    Class<?>[] parameterTypes = {Integer.class};
    Object[] args = {null};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        ((ClassForConstructorTest) objectLookupStore.lookupObject(objRef)).someInteger,
        is(nullValue()));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  public void dispatchIncoming_withNullArgsPrimitiveTypes_ok() {
    Class<?> targetClass = ClassForConstructorTest.class;
    Class<?>[] parameterTypes = {int.class};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    assertTrue(
        objectLookupStore.containsObjectRef(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())));
    assertThat(
        objectLookupStore.lookupObject(
            ObjectRef.from(responseMessage.getReturnValue().getObject().getRef())),
        instanceOf(targetClass));
  }

  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {
    // signature
    Class<?>[] parameterTypes = {String[].class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);
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

    Class<?>[] parameterTypes = {String[].class};
    Object[] args = new Object[1];
    args[0] =
        new String[] {"hello ", "world", "!"}; // varargs must be wrapped in array of expected type
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(1L));
    ObjectRef objRef = ObjectRef.from(responseMessage.getReturnValue().getObject().getRef());
    assertTrue(objectLookupStore.containsObjectRef(objRef));
    assertThat(objectLookupStore.lookupObject(objRef), instanceOf(targetClass));
    assertThat(
        responseMessage.getReturnValue(),
        Matchers.allOf(
            ComesFromClass.comesFromClass(targetClass),
            ComesFromReflectable.comesFrom(targetClass.getName())));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {
    // signature
    Class<?>[] parameterTypes = {String.class};
    Constructor<?> constructor = targetClass.getDeclaredConstructor(parameterTypes);
    Signature signature = new ConstructorSignature(constructor);

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {"49385InvalidNumber1001"};

    // dispatch
    try {
      @SuppressWarnings("unused")
      Object unused = dispatcher.dispatch(ctxt, this, null, args);
      fail("Should have thrown a NumberFormatException");
    } catch (NumberFormatException nfe) {
      // all good
    }

    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"49385InvalidNumber1001"};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertThat(objectLookupStore.size(), is(0L));
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NumberFormatException"));
  }

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() {
    Class<?>[] parameterTypes = {java.lang.String.class, Boolean.TYPE, java.lang.Integer.class};
    Object[] args = {"woiwefoj", true, 459};
    ObjectRef[] argRefs = {null, null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

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
  public void dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_publicAccessibleObject_noException() throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE};
    Object[] args = {459};
    ObjectRef[] argRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch with the onlyPublicDispatcher - expect no exception
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE, Integer.TYPE};
    Object[] args = {459, 459};
    ObjectRef[] argRefs = {null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  @Override
  @Test
  public void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable {
    Class<?>[] parameterTypes = {Integer.TYPE, Integer.TYPE, Integer.TYPE};
    Object[] args = {459, 459, 459};
    ObjectRef[] argRefs = {null, null, null};

    ExecMessage incomingMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, targetClass.getName(), toNames(parameterTypes), args, argRefs);

    // dispatch with the onlyPublicDispatcher - expect NoSuchMethodException
    ExecMessage responseMessage =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertThat(
        responseMessage.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    responseMessage = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertThat(responseMessage.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(responseMessage.getRaisedThrowable());
  }

  // auxiliary class
  @SuppressWarnings({"unused", "checkstyle:MemberName"})
  private static class ClassForConstructorTest {
    Integer someInteger;
    String joinedVarArgs;
    long aLong;

    ClassForConstructorTest() {}

    ClassForConstructorTest(boolean plusOne, long someLong) {
      this.aLong = plusOne ? someLong + 1 : someLong;
    }

    ClassForConstructorTest(Integer someInteger) {
      this.someInteger = someInteger;
    }

    ClassForConstructorTest(String someMalformedNumber) {
      this.someInteger = Integer.valueOf(someMalformedNumber);
    }

    ClassForConstructorTest(String... args) {
      this.joinedVarArgs = String.join("", args);
    }

    // for visibility tests
    public ClassForConstructorTest(int i) {}

    protected ClassForConstructorTest(int i, int j) {}

    private ClassForConstructorTest(int i, int j, int k) {}
  }
}
