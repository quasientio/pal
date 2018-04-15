package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class VoidInstanceMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new VoidInstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = java.io.PrintStream.class;
		String methodName = "println";
		Class[] parameterTypes = new Class[]{String.class};
		Signature signature = new MethodSignature(targetClass, methodName, parameterTypes);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// args
		Object[] args = new Object[]{"hello from test"};

		// dispatch
		Object target = System.out;
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		assertEquals(Void.getInstance(), returned);
	}
}