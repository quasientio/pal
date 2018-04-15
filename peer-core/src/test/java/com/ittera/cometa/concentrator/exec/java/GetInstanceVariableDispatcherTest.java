package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

// auxiliary class
class ClassForGetInstanceVariableTest {
	int myInt = 10;
}

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new GetInstanceVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	@Test
	public void dispatch() throws Throwable {

		// signature
		Class targetClass = ClassForGetInstanceVariableTest.class;
		String fieldName = "myInt";
		int modifiers = targetClass.getDeclaredField(fieldName).getModifiers();
		Signature signature = new FieldSignature(targetClass, targetClass.getTypeName(), modifiers, fieldName,
			targetClass.getDeclaredField(fieldName), java.io.PrintStream.class);

		// ctxt
		String sourceFilename = null;
		int sourceLine = -1;
		Context ctxt = new Context(sourceFilename, sourceLine, targetClass, signature);

		// dispatch
		ClassForGetInstanceVariableTest target = new ClassForGetInstanceVariableTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.myInt, returned);
	}
}