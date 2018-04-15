package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import static org.hamcrest.core.IsInstanceOf.instanceOf;


import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Constructor;

@RunWith(MockitoJUnitRunner.class)
public class ConstructorDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new ConstructorDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = java.util.ArrayList.class;
		Class[] parameterTypes = new Class[]{};
		String[] parameterNames = new String[]{};
		Class[] exceptionTypes = new Class[]{};
		int modifiers = 0;
		Constructor constructor = targetClass.getConstructor(parameterTypes);
		Signature signature = new ConstructorSignature(targetClass, targetClass.getTypeName(),
			modifiers, targetClass.getSimpleName(), exceptionTypes, parameterNames, parameterTypes, constructor);

		// ctxt
		String sourceFilename = null;
		int sourceLine = 10;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// args
		Object[] args = new Object[]{};

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		assertNotNull(returned);
		assertThat(returned, instanceOf(targetClass));
	}
}