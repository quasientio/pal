package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.junit.MockitoJUnitRunner;

import static java.util.stream.Collectors.*;

import java.util.Arrays;

// auxiliary class
class ClassForNonVoidInstanceMethodTest {
	private String value;

	ClassForNonVoidInstanceMethodTest() {
	}

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

	private Dispatcher dispatcher = new InstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForNonVoidInstanceMethodTest.class;

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		String methodName = "toUpperCase";
		Class[] parameterTypes = {};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = {};

		// dispatch
		String value = "a lowercase string";
		Object target = new ClassForNonVoidInstanceMethodTest(value);
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertEquals(value.toUpperCase(), returned);
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() {

		// create and store new instance
		String value = "a lowercase string";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "toUpperCase";
		Class[] parameterTypes = {};
		ObjectRef[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(value.toUpperCase(), returned);
	}

	@Test
	@Override
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "append";
		Class[] parameterTypes = {String.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = {"et"};

		// dispatch
		String value = "blank";
		Object target = new ClassForNonVoidInstanceMethodTest(value);
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertEquals(value + args[0], returned);
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		// create and store new instance
		String value = "blank";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = {String.class};
		ObjectRef[] argObjRefs = {null};
		Object[] args = {"et"};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(value + args[0], returned);
	}

	@Test
	@Override
	public void dispatch_withPrimitiveArgs_ok() throws Throwable {

		// signature
		String methodName = "floatAsString";
		Class[] parameterTypes = {float.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		float floatArg = 238923.32f;
		Object[] args = {floatArg};

		// dispatch
		Object target = new ClassForNonVoidInstanceMethodTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertEquals(String.valueOf(floatArg), returned);
	}

	@Test
	@Override
	public void dispatchIncoming_withPrimitiveArgs_ok() {
		// create and store new instance
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "floatAsString";
		Class[] parameterTypes = {float.class};
		float floatArg = 238923.32f;
		Object[] args = {floatArg};
		ObjectRef[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(String.valueOf(floatArg), returned);
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() {

		// create and store new instance
		String value = "blank";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = {String.class};
		Object[] args = {null};
		ObjectRef etObjRef = objectService.storeObject("et");
		ObjectRef[] argObjRefs = {etObjRef};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(3, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals("blanket", returned);
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() {

		// create and store new instance
		String value = "blank";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = {String.class};
		Object[] args = {null};
		ObjectRef[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(value, returned);
	}

	@Test
	@Override
	public void dispatch_varargs_ok() throws Throwable {

		// signature
		String methodName = "join";
		Class[] parameterTypes = {String.class, String[].class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

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
		verifyDispatcherConnectorCalledTwice();
		assertEquals("package::class::method", returned);
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() {

		// create and store new instance
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "join";
		Class[] parameterTypes = {String.class, String[].class};
		String[] parts = {"package", "class", "method"};
		String joiner = "::";
		Object[] args = {joiner, parts};
		ObjectRef[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals("package::class::method", returned);
	}


	@Test
	@Override
	public void dispatch_throwsException_exceptionThrown() throws Throwable {

		// signature
		String methodName = "toUpperCase";
		Class[] parameterTypes = {};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

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
		verifyDispatcherConnectorCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		// create and store new instance
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		String methodName = "toUpperCase";
		Class[] parameterTypes = {};
		Object[] args = {};
		ObjectRef[] argObjRefs = {};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			target, targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals("java.lang.NullPointerException", doneMessage.getRaisedThrowable().getThrowable().getType());
	}
}