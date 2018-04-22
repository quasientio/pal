package com.ittera.cometa.concentrator.exec.java;

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

	static void sleep() {
		slept = true;
	}

	static void sleep(long millis) {
		millisSlept = millis;
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
}

/**
 * TODO:
 *  - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new VoidClassMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForVoidClassMethodTest.class;

	@Test
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
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "sleep";
		Class[] parameterTypes = new Class[]{long.class};
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
		Object[] args = new Object[]{aList, aNumber};

		// dispatch
		try {
			Object returned = dispatcher.dispatch(ctxt, this, null, args);
			fail("Should have thrown a NPE");
		} catch (NullPointerException npe) {
			// all good
		}
		verifyDispatcherCalledTwice();
	}


}