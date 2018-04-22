package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

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
public class NonVoidInstanceMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new NonVoidInstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForNonVoidInstanceMethodTest.class;

	@Test
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
		Object target = new ClassForNonVoidInstanceMethodTest("blank");
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals("blanket", returned);
	}

	@Test
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
}