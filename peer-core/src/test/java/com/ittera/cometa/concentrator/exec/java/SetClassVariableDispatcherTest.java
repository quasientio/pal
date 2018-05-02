package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.common.lang.reflect.Signature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.mockito.runners.MockitoJUnitRunner;

import java.util.LinkedList;

// auxiliary class
class ClassForPutStaticTest {

	static {
		__resetStaticVars();
	}

	static short someShort;
	static byte[] bytes;
	static Boolean someBoolean;
	static String aString;
	static java.util.List aList;
	static Object[] objects;
	static Throwable lastError;

	static void __resetStaticVars() {
		someShort = 4;
		bytes = null;
		someBoolean = false;
		aString = "I am a normal string";
		aList = new java.util.ArrayList();
		objects = null;
		lastError = new Exception("dummy exception");
	}
}

@RunWith(MockitoJUnitRunner.class)
public class SetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

	private Dispatcher dispatcher = new SetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForPutStaticTest.class;

	@After
	public void resetTestClassVariables() {
		ClassForPutStaticTest.__resetStaticVars();
	}

	@Override
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

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";
		String fieldClassName = "short.class";
		short newFieldValue = 987;

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertEquals(newFieldValue, ClassForPutStaticTest.someShort);
	}

	@Override
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

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";
		String fieldClassName = "byte[].class";
		byte[] newFieldValue = "this is just a test".getBytes();

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertArrayEquals(newFieldValue, ClassForPutStaticTest.bytes);
	}

	@Override
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
		assertFalse(ClassForPutStaticTest.someBoolean);
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, ClassForPutStaticTest.someBoolean);
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "someBoolean";
		String fieldClassName = "Boolean.class";
		Boolean newFieldValue = true;

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		assertFalse(ClassForPutStaticTest.someBoolean);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertEquals(newFieldValue, ClassForPutStaticTest.someBoolean);
	}

	@Override
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

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";
		String fieldClassName = "String.class";
		String newFieldValue = "abnormally";

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertEquals(newFieldValue, ClassForPutStaticTest.aString);
	}

	@Override
	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "aList";
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
		assertEquals(newFieldValue, ClassForPutStaticTest.aList);
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "aList";
		LinkedList newFieldValue = new LinkedList();
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertEquals(newFieldValue, ClassForPutStaticTest.aList);
		assertTrue(newFieldValue == ClassForPutStaticTest.aList);
	}

	@Override
	@Test
	public void dispatch_nullObject_ok() throws Throwable {

		// signature
		String fieldName = "aList";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		java.util.List newFieldValue = null;
		Object[] args = {newFieldValue};
		assertNotNull(ClassForPutStaticTest.aList);
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertNull(ClassForPutStaticTest.aList);
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aList";
		LinkedList newFieldValue = null;

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			"List.class", newFieldValue);

		// dispatch
		assertNotNull(ClassForPutStaticTest.aList);
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(0, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertNull(ClassForPutStaticTest.aList);
	}

	@Override
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

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";
		Object[] newFieldValue = {1, "a", false, 9283.95d};
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertArrayEquals(newFieldValue, ClassForPutStaticTest.objects);
		assertTrue(newFieldValue == ClassForPutStaticTest.objects);
	}

	@Override
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

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";
		Exception newFieldValue = new Exception("not working");
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		DataMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getStaticFieldPutDone().getField().getName());
		assertEquals(newFieldValue, ClassForPutStaticTest.lastError);
		assertTrue(newFieldValue == ClassForPutStaticTest.lastError);
	}
}