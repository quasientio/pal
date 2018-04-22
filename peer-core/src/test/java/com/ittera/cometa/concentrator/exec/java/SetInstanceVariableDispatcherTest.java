package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Arrays;

// auxiliary class
class ClassForPutFieldTest {
	short someShort = 4;
	byte[] bytes;
	Long aLong = 8238l;
	String aString = "I am a normal string";
	java.util.List anObject = new java.util.ArrayList();
	Object[] objects;
	Throwable lastError = new Exception("dummy exception");
}

@RunWith(MockitoJUnitRunner.class)
public class SetInstanceVariableDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new SetInstanceVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForPutFieldTest.class;

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
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.someShort);
	}

	@Test
	public void dispatch_primitiveArray_ok() throws Throwable {

		// signature
		String fieldName = "bytes";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		byte[] newFieldValue = "bytes".getBytes();
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, target.bytes);
	}

	@Test
	public void dispatch_wrapper_ok() throws Throwable {

		// signature
		String fieldName = "aLong";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Long newFieldValue = null;
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.aLong);
	}

	@Test
	public void dispatch_string_ok() throws Throwable {

		// signature
		String fieldName = "aString";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		String newFieldValue = "to string or not to";
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.aString);
	}

	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "anObject";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		List newFieldValue = Arrays.asList(938,3038,948,394);
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.anObject);
	}

	@Test
	public void dispatch_objectArray_ok() throws Throwable {

		// signature
		String fieldName = "objects";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object[] newFieldValue = {1, "a", false};
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, target.objects);
	}

	@Test
	public void dispatch_throwable_ok() throws Throwable {

		// signature
		String fieldName = "lastError";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Error newFieldValue = new Error("uuh ooooh");
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.lastError);
	}
}