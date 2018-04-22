package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

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
		if (word.matches(WORD_REGEX)) {
			wordsCollected.add(word);
		} else {
			throw new IllegalArgumentException("Not a word: " + word);
		}
	}

	void addWords(String... words) {
		Arrays.stream(words).filter(w -> w.matches(WORD_REGEX)).forEach(w -> wordsCollected.add(w));
	}
}

/**
 * TODO:
 * - with remoteArgs
 */
@RunWith(MockitoJUnitRunner.class)
public class VoidInstanceMethodDispatcherTest extends AbstractDispatcherTest {

	private Dispatcher dispatcher = new VoidInstanceMethodDispatcher(peerUuid, messageBuilder,
		dispatcherConnector, objectService);


	private Class targetClass = ClassForVoidInstanceMethodTest.class;

	@Test
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
		assertEquals(Void.getInstance(), returned);
		assertEquals(2, target.wordsCollected.size());
	}

	@Test
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
		assertEquals(Void.getInstance(), returned);
		assertEquals(1, target.wordsCollected.size());
	}

	@Test
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
		assertEquals(Void.getInstance(), returned);
		assertEquals(4, target.wordsCollected.size());
	}

	@Test
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
	}
}