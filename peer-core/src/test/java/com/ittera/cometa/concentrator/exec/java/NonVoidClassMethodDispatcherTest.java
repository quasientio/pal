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

import java.util.stream.DoubleStream;

import java.util.Random;

// auxiliary class
class ClassForNonVoidClassMethodTest {
	private static Random random = new Random();

	static short getRandomMinute() {
		return (short) random.nextInt(60);
	}

	static double max(double a, double b) {
		return Math.max(a, b);
	}

	static double max(double... doubles) {
		return DoubleStream.of(doubles).max().getAsDouble();
	}

	static double divBy(int number, int divisor) {
		return number / divisor;
	}

	static Integer add(Integer a, Integer b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return a + b;
	}
}

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new NonVoidClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	private Class targetClass = ClassForNonVoidClassMethodTest.class;

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		String methodName = "getRandomMinute";
		Class[] parameterTypes = {};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = {};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertNotEquals(Void.getInstance(), returned);
		assertTrue((short) returned >= 0 && (short) returned < 60);
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() {

		String methodName = "getRandomMinute";
		Class[] parameterTypes = {};
		String[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		short returned = -1;
		try {
			returned = (short) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
			assertTrue(returned >= 0 && returned < 60);
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
	}

	@Test
	@Override
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "max";
		Class[] parameterTypes = new Class[]{double.class, double.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = new Object[]{smallDouble, bigDouble};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(bigDouble, (double) returned, 0);
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		String methodName = "max";
		Class[] parameterTypes = new Class[]{double.class, double.class};
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = new Object[]{smallDouble, bigDouble};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		double returned = -1;
		try {
			returned = (double) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(bigDouble, returned, 0);
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() {

		String methodName = "max";
		Class[] parameterTypes = new Class[]{double.class, double.class};
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = new Object[]{null, null};
		String[] argObjRefs = {objectService.storeObject(smallDouble), objectService.storeObject(bigDouble)};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		double returned = -1;
		try {
			returned = (double) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(bigDouble, returned, 0);
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() {

		String methodName = "add";
		Integer realNumber = 6565;
		Class[] parameterTypes = new Class[]{Integer.class, Integer.class};
		Object[] args = new Object[]{null, realNumber};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Integer returned = null;
		try {
			returned = (Integer) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(realNumber, returned);
	}

	@Test
	@Override
	public void dispatch_varargs_ok() throws Throwable {
		// signature
		String methodName = "max";
		Class[] parameterTypes = new Class[]{double[].class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		double d1 = 837;
		double d2 = 8293;
		double d3 = 137193;
		double d4 = 8287193;
		double[] varargs = new double[]{d1, d2, d3, d4};
		Object[] args = {varargs};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(d4, returned);
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() {

		String methodName = "max";
		Class[] parameterTypes = new Class[]{double[].class};
		double d1 = 837;
		double d2 = 8293;
		double d3 = 137193;
		double d4 = 8287193;
		double[] varargs = new double[]{d1, d2, d3, d4};
		Object[] args = {varargs};
		String[] argObjRefs = {null, null, null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		double returned = -1;
		try {
			returned = (double) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(d4, returned, 0);
	}

	@Test
	@Override
	public void dispatch_throwsException_exceptionThrown() throws Throwable {

		// signature
		String methodName = "divBy";
		Class[] parameterTypes = new Class[]{int.class, int.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		int number = 8378;
		int divisor = 0;
		Object[] args = new Object[]{number, divisor};

		// dispatch
		try {
			Object returned = dispatcher.dispatch(ctxt, this, null, args);
			fail("Should have failed with a div by zero overflow");
		} catch (ArithmeticException ae) {
			// all good
		}
		verifyDispatcherCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		String methodName = "divBy";
		Class[] parameterTypes = new Class[]{int.class, int.class};
		int number = 8378;
		int divisor = 0;
		Object[] args = new Object[]{number, divisor};
		String[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals("java.lang.reflect.InvocationTargetException",
			doneMessage.getRaisedThrowable().getThrowable().getType());
		assertEquals("java.lang.ArithmeticException",
			doneMessage.getRaisedThrowable().getThrowable().getCause().getType());
	}

}