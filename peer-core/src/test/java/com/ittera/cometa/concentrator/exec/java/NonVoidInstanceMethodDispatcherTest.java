package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonVoidInstanceMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new NonVoidInstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = String.class;
		String methodName = "toUpperCase";
		Class[] parameterTypes = new Class[]{};
		Signature signature = new MethodSignature(targetClass, methodName, parameterTypes);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// args
		Object[] args = new Object[]{};

		// dispatch
		Object target = String.valueOf("a lowercase string");
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		assertEquals(((String) target).toUpperCase(), returned);
	}
}