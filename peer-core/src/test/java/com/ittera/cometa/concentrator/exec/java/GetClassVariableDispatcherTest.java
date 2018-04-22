package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

// auxiliary class
class ClassForGetStaticTest {
	static short someShort = 4;
	static byte[] bytes = "Some".getBytes();
	static Integer someInteger = 965235;
	static String aString = "I am a normal string";
	static java.util.List anObject = new java.util.ArrayList();
	static Object[] objects = {1, "a", false};
	static Throwable lastError = new Exception("dummy exception");
}

@RunWith(MockitoJUnitRunner.class)
public class GetClassVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new GetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForGetStaticTest.class;

	@Test
	public void dispatch_primitive_ok() throws Throwable {

		// signature
		String fieldName = "someShort";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(ClassForGetStaticTest.someShort, returned);
	}

	@Test
	public void dispatch_primitiveArray_ok() throws Throwable {

		// signature
		String fieldName = "bytes";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertArrayEquals(ClassForGetStaticTest.bytes, (byte[]) returned);
	}

	@Test
	public void dispatch_wrapper_ok() throws Throwable {

		// signature
		String fieldName = "someInteger";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(ClassForGetStaticTest.someInteger, returned);
	}

	@Test
	public void dispatch_string_ok() throws Throwable {

		// signature
		String fieldName = "aString";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(ClassForGetStaticTest.aString, returned);
	}

	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "anObject";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(ClassForGetStaticTest.anObject, returned);
	}

	@Test
	public void dispatch_objects_ok() throws Throwable {

		// signature
		String fieldName = "objects";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertArrayEquals(ClassForGetStaticTest.objects, (Object[]) returned);
	}

	@Test
	public void dispatch_throwable_ok() throws Throwable {

		// signature
		String fieldName = "lastError";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		assertEquals(ClassForGetStaticTest.lastError, returned);
	}
}