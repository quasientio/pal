package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.common.lang.reflect.Signature;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import org.mockito.runners.MockitoJUnitRunner;

// auxiliary class
class ClassForSetClassVariableTest {
	static int myStaticInt = 10;
}

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new SetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);


	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = ClassForSetClassVariableTest.class;
		String fieldName = "myStaticInt";
		int modifiers = targetClass.getDeclaredField(fieldName).getModifiers();
		Signature signature = new FieldSignature(targetClass, targetClass.getTypeName(), modifiers, fieldName,
			targetClass.getDeclaredField(fieldName), java.io.PrintStream.class);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// dispatch
		int newFieldValue = 41739;
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForSetClassVariableTest.myStaticInt);
	}
}