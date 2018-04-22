package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.common.lang.reflect.Signature;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.mockito.runners.MockitoJUnitRunner;

import java.util.LinkedList;

// auxiliary class
class ClassForPutStaticTest {
	static short someShort = 4;
	static byte[] bytes;
	static Boolean someBoolean = false;
	static String aString = "I am a normal string";
	static java.util.List anObject = new java.util.ArrayList();
	static Object[] objects;
	static Throwable lastError = new Exception("dummy exception");
}

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new SetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForPutStaticTest.class;

	@Test
	public void dispatch_primitive_ok() throws Throwable {

		// signature
		String fieldName = "someShort";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		short newFieldValue = 987;
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForPutStaticTest.someShort);
	}

	@Test
	public void dispatch_primitiveArray_ok() throws Throwable {

		// signature
		String fieldName = "bytes";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		byte[] newFieldValue = "this is just a test".getBytes();
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, ClassForPutStaticTest.bytes);
	}

	@Test
	public void dispatch_wrapper_ok() throws Throwable {

		// signature
		String fieldName = "someBoolean";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Boolean newFieldValue = true;
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForPutStaticTest.someBoolean);
	}

	@Test
	public void dispatch_string_ok() throws Throwable {

		// signature
		String fieldName = "aString";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		String newFieldValue = "abnormally";
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForPutStaticTest.aString);
	}

	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "anObject";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		LinkedList newFieldValue = new LinkedList();
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertThat(newFieldValue, instanceOf(LinkedList.class));
		assertEquals(newFieldValue, ClassForPutStaticTest.anObject);
	}

	@Test
	public void dispatch_objectArray_ok() throws Throwable {

		// signature
		String fieldName = "objects";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object[] newFieldValue = {1, "a", false, 9283.95d};
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, ClassForPutStaticTest.objects);
	}

	@Test
	public void dispatch_throwable_ok() throws Throwable {

		// signature
		String fieldName = "lastError";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Exception newFieldValue = new Exception("not working");
		Object[] args = {newFieldValue};
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForPutStaticTest.lastError);
	}
}