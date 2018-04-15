package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VoidClassMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new VoidClassMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = Thread.class;
		String methodName = "sleep";
		Class[] parameterTypes = new Class[]{long.class};
		Signature signature = new MethodSignature(targetClass, methodName, parameterTypes);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// args
		long millisTosleep = 1;
		Object[] args = new Object[]{millisTosleep};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		assertEquals(Void.getInstance(), returned);
	}
}