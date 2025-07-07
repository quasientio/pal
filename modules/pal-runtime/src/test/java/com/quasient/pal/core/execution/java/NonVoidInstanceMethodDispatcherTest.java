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

import static com.quasient.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.quasient.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.quasient.pal.common.lang.reflect.MethodSignature;
import com.quasient.pal.common.lang.reflect.Signature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("FloatingPointLiteralPrecision")
public class NonVoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {
  private final Class<?> targetClass = ClassForNonVoidInstanceMethodTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Before
  @Override
  public void setUp() {
    super.setUp();
    dispatcher =
        new InstanceMethodDispatcher(
            peerUuid,
            messageBuilder,
            outboundMessageGateway,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new InstanceMethodDispatcher(
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
    String methodName = "toUpperCase";
    Class<?>[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    String value = "a lowercase string";
    Object target = new ClassForNonVoidInstanceMethodTest(value);
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(value.toUpperCase(Locale.getDefault())));
  }

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
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "append";
    Class<?>[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {

    // signature
    String methodName = "floatAsString";
    Class<?>[] parameterTypes = {float.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
  public void dispatch_varargs_ok() throws Throwable {

    // signature
    String methodName = "join";
    Class<?>[] parameterTypes = {String.class, String[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "toUpperCase";
    Class<?>[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    Object target = new ClassForNonVoidInstanceMethodTest();
    try {
      @SuppressWarnings("unused")
      Object unused = dispatcher.dispatch(ctxt, this, target, args);
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
    Class<?>[] parameterTypes = {java.lang.String.class};
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
