package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.LongStream;

// auxiliary class
class ClassForVoidClassMethodTest {
	public static boolean slept;
	public static long millisSlept;
	static Object verified;

	static {
		__resetStaticVars();
	}

	static void sleep() {
		slept = true;
	}

	static void sleep(long millis) {
		millisSlept = millis;
	}

	static void verify(Object toVerify) {
		verified = toVerify;
	}

	static void add(List<Long> sumContainer, long... parts) {
		// add it manually, (use streams for verification)
		long sum = 0;
		for (int i = 0; i < parts.length; i++) {
			sum += parts[i];
		}
		sumContainer.add(sum);
	}

	static void addPositive(List<Long> aList, long chunk) {
		if (chunk > 0) {
			aList.add(chunk);
		}
	}

	// call this method from unit tests to restore class variables that have been modified
	static void __resetStaticVars() {
		verified = "blah";
		slept = false;
		millisSlept = 0;
	}
}

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new ClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	private Class targetClass = ClassForVoidClassMethodTest.class;

	@After
	public void resetTestClassVariables() {
		ClassForVoidClassMethodTest.__resetStaticVars();
	}

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		String methodName = "sleep";
		Class[] parameterTypes = new Class[]{};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{};

		// dispatch
		assertFalse(ClassForVoidClassMethodTest.slept);
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertTrue(ClassForVoidClassMethodTest.slept);
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() {

		String methodName = "sleep";
		Class[] parameterTypes = new Class[]{};
		String[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		assertFalse(ClassForVoidClassMethodTest.slept);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertTrue(ClassForVoidClassMethodTest.slept);
	}

	@Test
	@Override
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "sleep";
		Class[] parameterTypes = {long.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		long millisToSleep = 5;
		Object[] args = new Object[]{millisToSleep};

		// dispatch
		assertEquals(0, ClassForVoidClassMethodTest.millisSlept);
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(millisToSleep, ClassForVoidClassMethodTest.millisSlept);
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		String methodName = "sleep";
		Class[] parameterTypes = {long.class};
		String[] argObjRefs = {null};
		long millisToSleep = 5;
		Object[] args = {millisToSleep};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		assertEquals(0, ClassForVoidClassMethodTest.millisSlept);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(millisToSleep, ClassForVoidClassMethodTest.millisSlept);
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() {

		String methodName = "sleep";
		Class[] parameterTypes = {long.class};
		Long millisToSleep = 5l;
		String objRef = objectService.storeObject(millisToSleep);
		Object[] args = {null};
		String[] argObjRefs = {objRef};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		assertEquals(0, ClassForVoidClassMethodTest.millisSlept);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(argObjRefs.length, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(millisToSleep.longValue(), ClassForVoidClassMethodTest.millisSlept);
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() {

		String methodName = "verify";
		Class[] parameterTypes = {Object.class};
		Object[] args = {null};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		assertNotNull(ClassForVoidClassMethodTest.verified);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertNull(ClassForVoidClassMethodTest.verified);
	}

	@Test
	@Override
	public void dispatch_varargs_ok() throws Throwable {
		// signature
		String methodName = "add";
		Class[] parameterTypes = {List.class, long[].class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		long[] someNumbers = {10L, 20L, 30L};
		List<Long> sumContainer = new ArrayList();
		Object[] args = new Object[]{sumContainer, someNumbers};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(1, sumContainer.size());
		assertEquals(LongStream.of(someNumbers).sum(), (long) sumContainer.get(0));
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() {

		String methodName = "add";
		Class[] parameterTypes = {List.class, long[].class};
		long[] someNumbers = {10L, 20L, 30L};
		List<Long> sumContainer = new ArrayList();
		Object[] args = {sumContainer, someNumbers};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(LongStream.of(someNumbers).sum(), (long) sumContainer.get(0));
	}

	@Test
	@Override
	public void dispatch_throwsException_exceptionThrown() throws Throwable {

		// signature
		String methodName = "addPositive";
		Class[] parameterTypes = new Class[]{List.class, long.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		long aNumber = 2;
		List<Long> aList = null;
		Object[] args = {aList, aNumber};

		// dispatch
		try {
			Object returned = dispatcher.dispatch(ctxt, this, null, args);
			fail("Should have thrown a NPE");
		} catch (NullPointerException npe) {
			// all good
		}
		verifyDispatcherCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		String methodName = "addPositive";
		Class[] parameterTypes = new Class[]{List.class, long.class};
		List<Long> aList = null;
		Object[] args = {aList, 2};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());

		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals("java.lang.reflect.InvocationTargetException",
			doneMessage.getRaisedThrowable().getThrowable().getType());
		assertEquals("java.lang.NullPointerException",
			doneMessage.getRaisedThrowable().getThrowable().getCause().getType());
	}
}