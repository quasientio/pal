package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

// auxiliary class
class ClassForGetFieldTest {
	short someShort = 0;
	byte[] bytes;
	Integer someInteger = null;
	String aString = "I am a normal string";
	java.util.List anObject = new java.util.ArrayList();
	Object[] objects = {1, "a", false};
	Throwable lastError = new Error("dummy error");
}

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new GetInstanceVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForGetFieldTest.class;

	@Test
	public void dispatch_primitive_ok() throws Throwable {

		// signature
		String fieldName = "someShort";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.someShort, returned);
	}

	@Test
	public void dispatch_primitiveArray_ok() throws Throwable {

		// signature
		String fieldName = "bytes";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertArrayEquals(target.bytes, (byte[]) returned);
	}

	@Test
	public void dispatch_wrapper_ok() throws Throwable {

		// signature
		String fieldName = "someInteger";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.someInteger, returned);
	}

	@Test
	public void dispatch_string_ok() throws Throwable {

		// signature
		String fieldName = "aString";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.aString, returned);
	}

	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "anObject";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.anObject, returned);
	}

	@Test
	public void dispatch_objectArray_ok() throws Throwable {

		// signature
		String fieldName = "objects";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertArrayEquals(target.objects, (Object[]) returned);
	}

	@Test
	public void dispatch_throwable_ok() throws Throwable {

		// signature
		String fieldName = "lastError";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		assertEquals(target.lastError, returned);
	}
}