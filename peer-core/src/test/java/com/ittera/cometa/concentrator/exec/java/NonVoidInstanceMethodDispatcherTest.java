package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

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

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class NonVoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new NonVoidInstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForNonVoidInstanceMethodTest.class;

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		String methodName = "toUpperCase";
		Class[] parameterTypes = new Class[]{};
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
		verifyDispatcherCalledTwice();
		assertEquals(value.toUpperCase(), returned);
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() {

		// create and store new instance
		String value = "a lowercase string";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		String targetObjRef = objectService.storeObject(target);

		String methodName = "toUpperCase";
		Class[] parameterTypes = new Class[]{};
		String[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 2, objectService.size());
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
		Class[] parameterTypes = new Class[]{String.class};
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
		verifyDispatcherCalledTwice();
		assertEquals(value + args[0], returned);
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		// create and store new instance
		String value = "blank";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		String targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = new Class[]{String.class};
		String[] argObjRefs = {null};
		Object[] args = {"et"};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 2, objectService.size());
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
	public void dispatchIncoming_withObjectRefArgs_ok() {

		// create and store new instance
		String value = "blank";
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest(value);
		String targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = new Class[]{String.class};
		Object[] args = {null};
		String etObjRef = objectService.storeObject("et");
		String[] argObjRefs = {etObjRef};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 2, objectService.size());
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
		String targetObjRef = objectService.storeObject(target);

		String methodName = "append";
		Class[] parameterTypes = new Class[]{String.class};
		Object[] args = {null};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
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
		Class[] parameterTypes = new Class[]{String.class, String[].class};
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
		verifyDispatcherCalledTwice();
		assertEquals("package::class::method", returned);
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() {

		// create and store new instance
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "join";
		Class[] parameterTypes = new Class[]{String.class, String[].class};
		String[] parts = {"package", "class", "method"};
		String joiner = "::";
		Object[] args = {joiner, parts};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 2, objectService.size());
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
		Class[] parameterTypes = new Class[]{};
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
		verifyDispatcherCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		// create and store new instance
		ClassForNonVoidInstanceMethodTest target = new ClassForNonVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "toUpperCase";
		Class[] parameterTypes = new Class[]{};
		Object[] args = {};
		String[] argObjRefs = {};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals(doneMessage.getRaisedThrowable().getThrowable().getType(), "java.lang.reflect.InvocationTargetException");
		assertEquals(doneMessage.getRaisedThrowable().getThrowable().getCause().getType(), "java.lang.NullPointerException");
	}
}