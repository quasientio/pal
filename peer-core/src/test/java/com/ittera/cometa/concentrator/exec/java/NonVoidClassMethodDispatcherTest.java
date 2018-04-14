package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NonVoidClassMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new NonVoidClassMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	@Test
	public void dispatch() throws Throwable {

		String sourceFilename = null;
		int sourceLine = 10;
		Class targetClass = Math.class;
		String methodName = "max";
		Class[] parameterTypes = new Class[]{double.class, double.class};
		Signature signature = new MethodSignature(targetClass, methodName, parameterTypes);
		Context ctxt =  new Context(sourceFilename, sourceLine, targetClass, signature);

		double smallDouble = 8378;
		double bigDouble = 827193;

		Object[] args = new Object[]{ smallDouble, bigDouble};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		assertEquals(bigDouble, returned);
	}
}