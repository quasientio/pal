package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.common.lang.reflect.Signature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import static com.ittera.cometa.concentrator.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.concentrator.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.concentrator.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;

import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import static org.hamcrest.core.IsInstanceOf.instanceOf;

import org.mockito.junit.MockitoJUnitRunner;

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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_primitive_ok() {

		String fieldName = "someShort";
		String fieldClassName = "short.class";
		short newFieldValue = 987;

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.someShort, is(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_primitiveArray_ok() {

		String fieldName = "bytes";
		String fieldClassName = "byte[].class";
		byte[] newFieldValue = "this is just a test".getBytes();

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.bytes, is(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_wrapper_ok() {

		String fieldName = "someBoolean";
		String fieldClassName = "Boolean.class";
		Boolean newFieldValue = true;

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		assertFalse(ClassForPutStaticTest.someBoolean);
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.someBoolean, is(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_string_ok() {

		String fieldName = "aString";
		String fieldClassName = "String.class";
		String newFieldValue = "abnormally";

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			fieldClassName, newFieldValue);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.aString, is(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(newFieldValue, instanceOf(LinkedList.class));
		assertThat(ClassForPutStaticTest.aList, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_object_ok() {

		String fieldName = "aList";
		LinkedList newFieldValue = new LinkedList();
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.aList, sameInstance(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		assertThat(ClassForPutStaticTest.aList, notNullValue());
		Object returned = dispatcher.dispatch(ctxt, this, null, args);

		// expect
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.aList, is(nullValue()));
	}

	@Override
	@Test
	public void dispatchIncoming_nullObject_ok() {

		String fieldName = "aList";
		LinkedList newFieldValue = null;

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName,
			"List.class", newFieldValue);

		// dispatch
		assertThat(ClassForPutStaticTest.aList, notNullValue());
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(0));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.aList, is(nullValue()));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.objects, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_objectArray_ok() {

		String fieldName = "objects";
		Object[] newFieldValue = {1, "a", false, 9283.95d};
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.objects, sameInstance(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
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
		verifyDispatcherConnectorCalledTwice();
		assertThat(returned, is(Void.getInstance()));
		assertThat(ClassForPutStaticTest.lastError, is(newFieldValue));
	}

	@Override
	@Test
	public void dispatchIncoming_throwable_ok() {

		String fieldName = "lastError";
		Exception newFieldValue = new Exception("not working");
		ObjectRef valueObjRef = objectService.storeObject(newFieldValue);

		ExecMessage incomingMessage = messageBuilder.buildPutStatic(peerUuid, targetClass.getName(), fieldName, valueObjRef);

		// dispatch
		ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherConnectorCalledOnce();
		assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
		assertThat(objectService.size(), is(1));
		assertFalse(replyMsg.hasReturnValue());
		assertThat(replyMsg.getStaticFieldPutDone().getField().getName(), is(fieldName));
		assertThat(ClassForPutStaticTest.lastError, sameInstance(newFieldValue));
		assertThat(replyMsg.getStaticFieldPutDone(), hasDeclaringClass(targetClass));
		assertThat(replyMsg.getStaticFieldPutDone(), allOf(comesFromClass(targetClass), comesFrom(fieldName)));
		assertThat(replyMsg.getStaticFieldPutDone().getStaticFieldPutUuid(), notNullValue());
	}
}