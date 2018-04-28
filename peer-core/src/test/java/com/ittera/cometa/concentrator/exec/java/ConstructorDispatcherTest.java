package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

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
 * - with remoteArgs
 * - with with objectRefs
 * - use DataMessageAssertions for dispatchIncoming* tests
 */
@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new ConstructorDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	private Class targetClass = ClassForConstructorTest.class;

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
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
	@Override
	public void dispatchIncoming_noArgs_ok() {

		DataMessage incomingMessage = messageBuilder.buildEmptyConstructor(peerUuid, targetClass.getName());

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
		assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
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
		verifyDispatcherCalledTwice();
		assertNotNull(returned);
		assertThat(returned, instanceOf(targetClass));
		assertEquals(args[0], ((ClassForConstructorTest) returned).someInteger);
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		Class[] parameterTypes = {Integer.class};
		Object[] args = {459};
		String[] argRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
			toNames(parameterTypes), args, argRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
		assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() {

		Class[] parameterTypes = {Integer.class};
		Integer arg = new Integer(459);
		String objRef = objectService.storeObject(arg);
		Object[] args = {};
		String[] argRefs = {objRef};

		DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
			toNames(parameterTypes), args, argRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(argRefs.length + 1, objectService.size());
		assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
		assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
		assertEquals(arg, ((ClassForConstructorTest) objectService.lookupObject(
			doneMessage.getReturnValue().getObject().getRef())).someInteger);
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() {

		Class[] parameterTypes = {Integer.class};
		Object[] args = {null};
		String[] argRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
			toNames(parameterTypes), args, argRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
		assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
		assertNull(((ClassForConstructorTest) objectService.lookupObject(
			doneMessage.getReturnValue().getObject().getRef())).someInteger);
	}

	/**
	 * previous test but para type changed to primitive , failing now
	 *
	 * @Test public void dispatchIncoming_withArgs_ok() {
	 * <p>
	 * Class targetClass = ClassForConstructorTest.class;
	 * Class[] parameterTypes = {int.class};
	 * Object[] args = {459};
	 * String[] argRefs = {null};
	 * <p>
	 * String[] parameterTypesNamesArray = Arrays.stream(parameterTypes).map(p -> p.getName()).collect(toList()).
	 * toArray(new String[0]);
	 * <p>
	 * DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
	 * parameterTypesNamesArray, args, argRefs);
	 * <p>
	 * // dispatch
	 * DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);
	 * <p>
	 * // expect
	 * verifyDispatcherCalledOnce();
	 * assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
	 * assertEquals(args.length + 1, objectService.size());
	 * assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
	 * assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
	 * }
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
	@Override
	public void dispatchIncoming_varargs_ok() {

		Class[] parameterTypes = {String[].class};
		Object[] args = new Object[1];
		args[0] = new String[]{"hello ", "world", "!"}; //varargs must be wrapped in array of expected type
		String[] argRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
			toNames(parameterTypes), args, argRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(objectService.containsObjectRef(doneMessage.getReturnValue().getObject().getRef()));
		assertThat(objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef()), instanceOf(targetClass));
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

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		Class[] parameterTypes = {String.class};
		Object[] args = new Object[]{"49385InvalidNumber1001"};
		String[] argRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildNonEmptyConstructor(peerUuid, targetClass.getName(),
			toNames(parameterTypes), args, argRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length, objectService.size());
		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals(doneMessage.getRaisedThrowable().getThrowable().getType(),
			"java.lang.reflect.InvocationTargetException");
		assertEquals(doneMessage.getRaisedThrowable().getThrowable().getCause().getType(),
			"java.lang.NumberFormatException");
	}
}