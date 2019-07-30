package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static com.ittera.cometa.concentrator.DataMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.concentrator.DataMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.concentrator.DataMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.junit.runner.RunWith;

import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForGetStaticTest {
	static short someShort = 4;
	static byte[] bytes = "Some".getBytes();
	static Integer someInteger = 965235;
	static String aString = "I am a normal string";
	static java.util.List anObject = new java.util.ArrayList();
	static Object[] objects = {1, "a", false};
	static Throwable lastError = new Exception("dummy exception");
	static java.util.Map aNullMap;
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.someShort));
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() throws Exception {

		String fieldName = "someShort";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(ClassForGetStaticTest.someShort));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.bytes));
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() throws Exception {

		String fieldName = "bytes";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		byte[] returned = (byte[]) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(ClassForGetStaticTest.bytes));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.someInteger));
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() throws Exception {

		String fieldName = "someInteger";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(ClassForGetStaticTest.someInteger));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.aString));
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() throws Exception {

		String fieldName = "aString";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(ClassForGetStaticTest.aString));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.anObject));
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "anObject";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
		assertThat(returned, sameInstance(ClassForGetStaticTest.anObject));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
	}

	@Override
	@Test
	public void dispatch_nullObject_ok() throws Throwable {

		// signature
		String fieldName = "aNullMap";
		Signature signature = new FieldSignature(targetClass.getDeclaredField(fieldName));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// dispatch
		Object returned = dispatcher.dispatch(ctxt, this, null, null);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(nullValue()));
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aNullMap";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		assertTrue(replyMsg.getReturnValue().getObject().getIsNull());
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.objects));
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		assertTrue(replyMsg.getReturnValue().getObject().hasRef());
		Object returned = objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
		assertThat(returned, sameInstance(ClassForGetStaticTest.objects));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(ClassForGetStaticTest.lastError));
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";

		DataMessage incomingMessage = messageBuilder.buildGetStatic(peerUuid, targetClass.getName(), fieldName);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		assertTrue(replyMsg.getReturnValue().getObject().hasRef());
		Throwable returned = (Throwable) objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().
			getObject().getRef()));
		assertThat(returned, is(ClassForGetStaticTest.lastError));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
	}
}