package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

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
}

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new NonVoidClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	private Class targetClass = ClassForNonVoidClassMethodTest.class;

	@Test
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
		assertNotEquals(Void.getInstance(), returned);
		assertTrue((short) returned >= 0 && (short) returned < 60);
	}

	@Test
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
		assertEquals(bigDouble, returned);
	}

	@Test
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
		assertEquals(d4, returned);
	}

	@Test
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
	}

}