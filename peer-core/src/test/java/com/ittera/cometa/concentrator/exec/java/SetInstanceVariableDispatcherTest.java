package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

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
public class SetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

	private Dispatcher dispatcher = new SetInstanceVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForPutFieldTest.class;

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
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.someShort);
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";
		short newFieldValue = 987;
		String fieldClassName = "short.class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertEquals(newFieldValue, target.someShort);
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
		byte[] newFieldValue = "bytes".getBytes();
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, target.bytes);
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";
		byte[] newFieldValue = "bytes".getBytes();
		String fieldClassName = "byte[].class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertArrayEquals(newFieldValue, target.bytes);
	}

	@Override
	@Test
	public void dispatch_wrapper_ok() throws Throwable {

		// signature
		String fieldName = "aLong";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Long newFieldValue = 100000L;
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.aLong);
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "aLong";
		Long newFieldValue = 100000L;
		String fieldClassName = "Long.class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertEquals(newFieldValue, target.aLong);
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
		String newFieldValue = "to string or not to";
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.aString);
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";
		String fieldClassName = "String.class";
		String newFieldValue = "to string or not to";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertEquals(newFieldValue, target.aString);
	}

	@Override
	@Test
	public void dispatch_object_ok() throws Throwable {

		// signature
		String fieldName = "anObject";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		List newFieldValue = Arrays.asList(938, 3038, 948, 394);
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.anObject);
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "anObject";
		List newFieldValue = Arrays.asList(938, 3038, 948, 394);
		String newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertEquals(newFieldValue, target.anObject);
		assertTrue(newFieldValue == target.anObject);
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
		Object[] newFieldValue = {1, "a", false};
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertArrayEquals(newFieldValue, target.objects);
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";
		Object[] newFieldValue = {1, "a", false};
		String newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertArrayEquals(newFieldValue, target.objects);
		assertTrue(newFieldValue == target.objects);
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
		Error newFieldValue = new Error("uuh ooooh");
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(newFieldValue, target.lastError);
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";
		Error newFieldValue = new Error("uuh ooooh");
		String newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		String targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.hasReturnValue());
		assertEquals(fieldName, doneMessage.getInstanceFieldPutDone().getField().getName());
		assertEquals(newFieldValue, target.lastError);
		assertTrue(newFieldValue == target.lastError);
	}
}