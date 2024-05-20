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

import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Locale;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.Unwrapper;
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
            dispatcherConnector,
            Boolean.TRUE.toString(),
            reflectionHelper,
            objectLookupStore);
    onlyPublicDispatcher =
        new InstanceMethodDispatcher(
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
    String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
    assertThat(returned, is(value.toUpperCase(Locale.getDefault())));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(3L));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
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
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertNotNull(replyMsg.getReturnValue());
    assertNull(replyMsg.getRaisedThrowable());
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
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
    ExecMessage replyMsg =
        ((ExecMessageDispatcher) onlyPublicDispatcher).dispatchIncoming(incomingMessage);
    assertNull(replyMsg.getReturnValue());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));

    // dispatch with the all access dispatcher - expect no exception
    replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);
    assertNotNull(replyMsg.getReturnValue());
    assertThat(Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject()), is("hello,world!"));
    assertNull(replyMsg.getRaisedThrowable());
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
