package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GetClassVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new GetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = System.class;
		String fieldName = "out";
		int modifiers = targetClass.getField(fieldName).getModifiers();
		Signature signature = new FieldSignature(targetClass, targetClass.getTypeName(), modifiers, fieldName,
			targetClass.getField(fieldName), java.io.PrintStream.class);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(System.out, returned);
	}
}