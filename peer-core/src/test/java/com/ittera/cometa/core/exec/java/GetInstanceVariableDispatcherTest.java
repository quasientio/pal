package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import static com.ittera.cometa.core.ExecMessageMatchers.HasDeclaringClassOf.*;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromClass.*;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromReflectable.*;

import org.junit.*;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.junit.runner.RunWith;

import org.mockito.junit.MockitoJUnitRunner;

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
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.someShort));
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() throws Exception {

		String fieldName = "someShort";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		short returned = (short) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(target.someShort));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.bytes));
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() throws Exception {

		String fieldName = "bytes";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		byte[] returned = (byte[]) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(target.bytes));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.someInteger));
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() throws Exception {

		String fieldName = "someInteger";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Integer returned = (Integer) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(target.someInteger));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.aString));
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() throws Exception {

		String fieldName = "aString";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		String returned = (String) Unwrapper.unwrapObject(replyMsg.getReturnValue().getObject());
		assertThat(returned, is(target.aString));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.anObject));
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() throws Exception {

		String fieldName = "anObject";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
		assertThat(returned, is(target.anObject));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
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
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(nullValue()));
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aNullClass";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.objects));
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
		assertThat(returned, is(target.objects));
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
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, null);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledTwice();
		assertThat(returned, is(target.lastError));
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";

		// create and store new instance
		ClassForGetFieldTest target = new ClassForGetFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		ExecMessage incomingMessage = messageBuilder.buildGetObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorSendExecMessageCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.getReturnValue().getIsVoid());
		Object returned = objectService.lookupObject(ObjectRef.from(replyMsg.getReturnValue().getObject().getRef()));
		assertThat(returned, is(target.lastError));
		assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
	}
}