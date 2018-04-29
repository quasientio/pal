package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

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
public class GetClassVariableDispatcherTest extends AbstractFieldOpDispatcherTest {

	private Dispatcher dispatcher = new GetClassVariableDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);

	private Class targetClass = ClassForGetStaticTest.class;

	@Override
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
		verifyDispatcherCalledTwice();
		assertEquals(ClassForGetStaticTest.someShort, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		short returned = -1;
		try {
			returned = (short) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(ClassForGetStaticTest.someShort, returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertArrayEquals(ClassForGetStaticTest.bytes, (byte[]) returned);
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

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
		assertArrayEquals(ClassForGetStaticTest.bytes, returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(ClassForGetStaticTest.someInteger, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "someInteger";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

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
		assertEquals(ClassForGetStaticTest.someInteger, returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(ClassForGetStaticTest.aString, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		String returned = null;
		try {
			returned = (String) Unwrapper.unwrapObject(doneMessage.getReturnValue().getObject());
		} catch (ClassNotFoundException cnfe) {
			fail(cnfe.getMessage());
		}
		assertEquals(ClassForGetStaticTest.aString, returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(ClassForGetStaticTest.anObject, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "anObject";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef());
		assertEquals(ClassForGetStaticTest.anObject, returned);
		assertTrue(ClassForGetStaticTest.anObject == returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertArrayEquals(ClassForGetStaticTest.objects, (Object[]) returned);
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		assertTrue(doneMessage.getReturnValue().getObject().hasRef());
		Object returned = objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef());
		assertArrayEquals(ClassForGetStaticTest.objects, (Object[]) returned);
		assertTrue(ClassForGetStaticTest.objects == returned);
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
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(ClassForGetStaticTest.lastError, returned);
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		assertTrue(doneMessage.getReturnValue().getObject().hasRef());
		Throwable returned = (Throwable) objectService.lookupObject(doneMessage.getReturnValue().getObject().getRef());
		assertEquals(ClassForGetStaticTest.lastError, returned);
	}
}