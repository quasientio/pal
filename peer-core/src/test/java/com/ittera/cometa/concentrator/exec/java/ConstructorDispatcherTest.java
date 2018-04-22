package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import static org.hamcrest.core.IsInstanceOf.instanceOf;


import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static java.util.stream.Collectors.*;

// auxiliary class
class ClassForConstructorTest {
	Integer someInteger;
	String joinedVarArgs;

	ClassForConstructorTest() {
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
 * TODO:
 *  - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new ConstructorDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	@Test
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		Class targetClass = ClassForConstructorTest.class;
		Class[] parameterTypes = new Class[]{};
		Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
		Signature signature = new ConstructorSignature(constructor);

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertNotNull(returned);
		assertThat(returned, instanceOf(targetClass));
	}

	@Test
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		Class targetClass = ClassForConstructorTest.class;
		Class[] parameterTypes = {Integer.class};
		Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
		Signature signature = new ConstructorSignature(constructor);

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{459};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertNotNull(returned);
		assertThat(returned, instanceOf(targetClass));
		assertEquals(args[0], ((ClassForConstructorTest) returned).someInteger);
	}

	@Test
	public void dispatch_varargs_ok() throws Throwable {
		// signature
		Class targetClass = ClassForConstructorTest.class;
		Class[] parameterTypes = {String[].class};
		Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
		Signature signature = new ConstructorSignature(constructor);

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[1];
		args[0] = new String[]{"hello ", "world", "!"}; //varargs must be wrapped in array of expected type

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertNotNull(returned);
		assertThat(returned, instanceOf(targetClass));
		assertEquals("hello world!", ((ClassForConstructorTest) returned).joinedVarArgs);
	}

	@Test
	public void dispatch_throwsException_exceptionThrown() throws Throwable {
		// signature
		Class targetClass = ClassForConstructorTest.class;
		Class[] parameterTypes = {String.class};
		Constructor constructor = targetClass.getDeclaredConstructor(parameterTypes);
		Signature signature = new ConstructorSignature(constructor);

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{"49385InvalidNumber1001"};

		// dispatch
		try {
			Object returned = dispatcher.dispatch(ctxt, this, null, args);
			fail("Should have thrown a NumberFormatException");
		} catch (NumberFormatException nfe) {
			// all good
		}

		verifyDispatcherCalledTwice();
	}
}