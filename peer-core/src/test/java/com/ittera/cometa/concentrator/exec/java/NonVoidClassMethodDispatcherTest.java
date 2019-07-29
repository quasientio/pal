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

import static org.hamcrest.Matchers.*;

import org.mockito.junit.MockitoJUnitRunner;

import java.util.stream.DoubleStream;

import java.util.Random;

// auxiliary class
class ClassForNonVoidClassMethodTest {
	private static Random random = new Random();

	static short getRandomMinute() {
		return (short) random.nextInt(60);
	}

	static Double max(Double a, Double b) {
		return Math.max(a, b);
	}

	static double min(double a, double b) {
		return Math.min(a, b);
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

@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new ClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(not(Void.getInstance())));
		assertTrue((short) returned >= 0 && (short) returned < 60);
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() throws Exception {

		String methodName = "getRandomMinute";
		Class[] parameterTypes = {};
		ObjectRef[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertTrue(returned >= 0 && returned < 60);

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "max";
		Class[] parameterTypes = {Double.class, Double.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Double smallDouble = 8378d;
		Double bigDouble = 827193d;
		Object[] args = {smallDouble, bigDouble};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(bigDouble));
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() throws Exception {

		String methodName = "max";
		Class[] parameterTypes = {Double.class, Double.class};
		Double smallDouble = 8378d;
		Double bigDouble = 827193d;
		Object[] args = {smallDouble, bigDouble};
		ObjectRef[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Double returned = (Double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(bigDouble));

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatch_withPrimitiveArgs_ok() throws Throwable {
		// signature
		String methodName = "min";
		Class[] parameterTypes = {double.class, double.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = {smallDouble, bigDouble};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(smallDouble));
	}

	@Test
	@Override
	public void dispatchIncoming_withPrimitiveArgs_ok() throws Exception {
		String methodName = "min";
		Class[] parameterTypes = {double.class, double.class};
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = {smallDouble, bigDouble};
		ObjectRef[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(smallDouble));

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() throws Exception {

		String methodName = "max";
		Class[] parameterTypes = {double.class, double.class};
		double smallDouble = 8378;
		double bigDouble = 827193;
		Object[] args = {null, null};
		ObjectRef[] argObjRefs = {objectService.storeObject(smallDouble), objectService.storeObject(bigDouble)};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(3));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(bigDouble));

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() throws Exception {

		String methodName = "add";
		Integer realNumber = 6565;
		Class[] parameterTypes = {Integer.class, Integer.class};
		Object[] args = {null, realNumber};
		ObjectRef[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(realNumber));

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatch_varargs_ok() throws Throwable {
		// signature
		String methodName = "max";
		Class[] parameterTypes = {double[].class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		double d1 = 837;
		double d2 = 8293;
		double d3 = 137193;
		double d4 = 8287193;
		double[] varargs = {d1, d2, d3, d4};
		Object[] args = {varargs};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(d4));
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() throws Exception {

		String methodName = "max";
		Class[] parameterTypes = {double[].class};
		double d1 = 837;
		double d2 = 8293;
		double d3 = 137193;
		double d4 = 8287193;
		double[] varargs = {d1, d2, d3, d4};
		Object[] args = {varargs};
		ObjectRef[] argObjRefs = {null, null, null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		double returned = (double) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertEquals(d4, returned, 0);

		assertThat(replyMsg.getReturnValue().getClazz().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getReturnValue().getFrom().getMethod().getRepr(),
			allOf(containsString(targetClass.getName()),containsString(methodName)));
	}

	@Test
	@Override
	public void dispatch_throwsException_exceptionThrown() throws Throwable {

		// signature
		String methodName = "divBy";
		Class[] parameterTypes = {int.class, int.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		int number = 8378;
		int divisor = 0;
		Object[] args = {number, divisor};

		// dispatch
		try {
			Object returned = dispatcher.dispatch(ctxt, this, null, args);
			fail("Should have failed with a div by zero overflow");
		} catch (ArithmeticException ae) {
			// all good
		}
		verifyDispatcherConnectorCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		String methodName = "divBy";
		Class[] parameterTypes = {int.class, int.class};
		int number = 8378;
		int divisor = 0;
		Object[] args = {number, divisor};
		ObjectRef[] argObjRefs = {null, null};

		DataMessage incomingMessage = messageBuilder.buildClassMethod(peerUuid, targetClass.getName(), methodName,
			toNames(parameterTypes), this, null, args, argObjRefs);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		assertThat(replyMsg.getRaisedThrowable().getThrowable().getType(), is("java.lang.ArithmeticException"));
	}
}