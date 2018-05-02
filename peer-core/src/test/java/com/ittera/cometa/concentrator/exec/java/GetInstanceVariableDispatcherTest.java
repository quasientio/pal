package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

// auxiliary class
class ClassForGetFieldTest {
	short someShort = 0;
	byte[] bytes;
	Integer someInteger;
	String aString = "I am a normal string";
	java.util.List anObject = new java.util.ArrayList();
	Object[] objects = {1, "a", false};
	Throwable lastError = new Error("dummy error");
	Class aNullClass;
}

@RunWith(MockitoJUnitRunner.class)
public class GetInstanceVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

	private Dispatcher dispatcher = new GetInstanceVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForGetFieldTest.class;

	@Override
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
		verifyDispatcherCalledTwice();
		assertEquals(target.someShort, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		short returned = -1;
		try {
			returned = (short) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(target.someShort, returned);
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertArrayEquals(target.bytes, (byte[]) returned);
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		byte[] returned = null;
		try {
			returned = (byte[]) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertArrayEquals(target.bytes, returned);
	}

	@Override
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
		verifyDispatcherCalledTwice();
		assertEquals(target.someInteger, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "someInteger";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Integer returned = null;
		try {
			returned = (Integer) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(target.someInteger, returned);
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(target.aString, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(target.aString, returned);
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(target.anObject, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "anObject";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(doneMessage.getReturnValue().getObject().getRef()));
		assertEquals(target.anObject, returned);
	}

	@Override
	@Test
	public void dispatch_nullObject_ok() throws Throwable {

		// signature
		String fieldName = "aNullClass";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertNull(returned);
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aNullClass";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		assertTrue(doneMessage.getReturnValue().getObject().getIsNull());
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertArrayEquals(target.objects, (Object[]) returned);
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(doneMessage.getReturnValue().getObject().getRef()));
		assertArrayEquals(target.objects, (Object[]) returned);

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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(target.lastError, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(2, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(doneMessage.getReturnValue().getObject().getRef()));
		assertEquals(target.lastError, returned);
	}
}