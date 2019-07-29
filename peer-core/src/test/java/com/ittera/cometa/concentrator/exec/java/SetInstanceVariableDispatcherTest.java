package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;

import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Arrays;

// auxiliary class
class ClassForPutFieldTest {
	short someShort = 4;
	byte[] bytes;
	Long aLong = 8238l;
	String aString = "I am a normal string";
	java.util.List aList = new java.util.ArrayList();
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.someShort, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";
		short newFieldValue = 987;
		String fieldClassName = "short.class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.someShort, is(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.bytes, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";
		byte[] newFieldValue = "bytes".getBytes();
		String fieldClassName = "byte[].class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.bytes, is(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.aLong, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "aLong";
		Long newFieldValue = 100000L;
		String fieldClassName = "Long.class";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.aLong, is(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.aString, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";
		String fieldClassName = "String.class";
		String newFieldValue = "to string or not to";

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, fieldClassName, newFieldValue);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.aString, is(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		List newFieldValue = Arrays.asList(938, 3038, 948, 394);
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.aList, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "aList";
		List newFieldValue = Arrays.asList(938, 3038, 948, 394);
		ObjectRef newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.aList, sameInstance(newFieldValue));
		assertEquals(newFieldValue, target.aList);

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		List newFieldValue = null;
		Object[] args = {newFieldValue};
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		assertThat(target.aList, notNullValue());
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.aList, is(nullValue()));
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aList";
		List newFieldValue = null;

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, "List.class", newFieldValue);

		// dispatch
		assertThat(target.aList, notNullValue());
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.aList, is(nullValue()));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.objects, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";
		Object[] newFieldValue = {1, "a", false};
		ObjectRef newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.objects, sameInstance(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(target.lastError, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";
		Error newFieldValue = new Error("uuh ooooh");
		ObjectRef newValueObjRef = objectService.storeObject(newFieldValue);

		// create and store new instance
		ClassForPutFieldTest target = new ClassForPutFieldTest();
		ObjectRef targetObjRef = objectService.storeObject(target);

		DataMessage incomingMessage = messageBuilder.buildPutObject(peerUuid, targetClass.getName(), fieldName,
			targetObjRef, newValueObjRef);

		// dispatch
		DataMessage replyMsg = ((DataMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(2));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getName(), is(fieldName));
		assertThat(target.lastError, sameInstance(newFieldValue));

		assertThat(replyMsg.getInstanceFieldPutDone().getClass_().getName(), is(targetClass.getName()));
		assertThat(replyMsg.getInstanceFieldPutDone().getField().getRepr(),
			allOf(containsString(targetClass.getName()), containsString(fieldName)));
		assertThat(replyMsg.getInstanceFieldPutDone().getInstanceFieldPutUuid(), notNullValue());
	}
}