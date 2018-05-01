package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.*;

import static org.junit.Assert.*;

import org.junit.runner.RunWith;

import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

// auxiliary class
class ClassForVoidInstanceMethodTest {
	public List wordsCollected = new ArrayList<String>();
	private static final String WORD_REGEX = "^\\w+$";

	ClassForVoidInstanceMethodTest() {
	}

	void addHelloWorld() {
		wordsCollected.add("Hello");
		wordsCollected.add("World");
	}

	void addWord(String word) {
		if (word == null) {
			return;
		}

		if (word.matches(WORD_REGEX)) {
			wordsCollected.add(word);
		} else {
			throw new IllegalArgumentException("Not a word: " + word);
		}
	}

	void addWords(String... words) {
		Arrays.stream(words).filter(w -> w.matches(WORD_REGEX)).forEach(w -> wordsCollected.add(w));
	}

	void addWordList(List<String> wordList) {
		wordsCollected.addAll(wordList);
	}
}

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class VoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {

	private Dispatcher dispatcher = new InstanceMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector,
		objectService);

	private Class targetClass = ClassForVoidInstanceMethodTest.class;

	@Test
	@Override
	public void dispatch_noArgs_ok() throws Throwable {

		// signature
		String methodName = "addHelloWorld";
		Class[] parameterTypes = new Class[]{};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = {};

		// dispatch
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(2, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatchIncoming_noArgs_ok() {

		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addHelloWorld";
		Class[] parameterTypes = new Class[]{};
		String[] argObjRefs = {};
		Object[] args = {};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(2, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatch_withArgs_ok() throws Throwable {

		// signature
		String methodName = "addWord";
		Class[] parameterTypes = new Class[]{String.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{"hello"};

		// dispatch
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(1, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatchIncoming_withArgs_ok() {

		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addWord";
		Class[] parameterTypes = new Class[]{String.class};
		Object[] args = {"hello"};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(1, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatchIncoming_withObjectRefArgs_ok() {

		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addWordList";
		Class[] parameterTypes = new Class[]{List.class};
		List<String> wordList = Arrays.asList("the", "truth", "is", "out", "there");
		String listObjRef = objectService.storeObject(wordList);
		Object[] args = {null};
		String[] argObjRefs = {listObjRef};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(wordList.size(), target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatchIncoming_withNullArgs_ok() {
		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addWord";
		Class[] parameterTypes = new Class[]{List.class};
		Object[] args = {null};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(1, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(0, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatch_varargs_ok() throws Throwable {

		// signature
		String methodName = "addWords";
		Class[] parameterTypes = new Class[]{String[].class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		String[] words = {"hey", "there", "!", "whats", "up", "?"};
		Object[] args = {words};

		// dispatch
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		Object returned = dispatcher.dispatch(ctxt, this, target, args);

		// expect
		verifyDispatcherCalledTwice();
		assertEquals(Void.getInstance(), returned);
		assertEquals(4, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatchIncoming_varargs_ok() {

		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addWords";
		Class[] parameterTypes = new Class[]{String[].class};
		String[] words = {"hey", "there", "!", "whats", "up", "?"};
		Object[] args = {words};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertTrue(doneMessage.getReturnValue().getIsVoid());
		assertEquals(4, target.wordsCollected.size());
	}

	@Test
	@Override
	public void dispatch_throwsException_exceptionThrown() throws Throwable {

		// signature
		String methodName = "addWord";
		Class[] parameterTypes = new Class[]{String.class};
		Signature signature = new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

		// ctxt
		Context ctxt = new Context(null, -1, targetClass, signature);

		// args
		Object[] args = new Object[]{","};

		// dispatch
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		try {
			Object returned = dispatcher.dispatch(ctxt, this, target, args);
			fail("Should have failed with an IllegalArgumentException");
		} catch (IllegalArgumentException iae) {
			// all good
		}
		verifyDispatcherCalledTwice();
	}

	@Test
	@Override
	public void dispatchIncoming_throwsException_exceptionThrown() {

		// create and store new instance
		ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
		String targetObjRef = objectService.storeObject(target);

		String methodName = "addWord";
		Class[] parameterTypes = new Class[]{String.class};
		Object[] args = {","};
		String[] argObjRefs = {null};

		DataMessage incomingMessage = messageBuilder.buildInstanceMethod(peerUuid, targetClass.getName(), methodName,
			targetObjRef, toNames(parameterTypes), args, argObjRefs);

		// dispatch
		DataMessage doneMessage = dispatcher.dispatchIncoming(incomingMessage);

		// expect
		verifyDispatcherCalledOnce();
		assertTrue(doneMessage.getFollowingUuid().equals(incomingMessage.getMessageUuid()));
		assertEquals(args.length + 1, objectService.size());
		assertFalse(doneMessage.getReturnValue().getIsVoid());
		assertTrue(doneMessage.hasRaisedThrowable());
		assertEquals("java.lang.reflect.InvocationTargetException",
			doneMessage.getRaisedThrowable().getThrowable().getType());
		assertEquals("java.lang.IllegalArgumentException",
			doneMessage.getRaisedThrowable().getThrowable().getCause().getType());
	}
}