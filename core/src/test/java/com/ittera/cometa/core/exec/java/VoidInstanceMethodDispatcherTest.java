package com.ittera.cometa.core.exec.java;

import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static com.ittera.cometa.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static com.ittera.cometa.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.MethodSignature;
import com.ittera.cometa.common.lang.reflect.Signature;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

// auxiliary class
class ClassForVoidInstanceMethodTest {
  public List<String> wordsCollected = new ArrayList<>();
  private static final String WORD_REGEX = "^\\w+$";

  ClassForVoidInstanceMethodTest() {}

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

  void addWords(int n) {
    for (int i = 0; i < n; i++) {
      addWord("again");
    }
  }

  void addWords(String... words) {
    Arrays.stream(words).filter(w -> w.matches(WORD_REGEX)).forEach(w -> wordsCollected.add(w));
  }

  void addWordList(List<String> wordList) {
    wordsCollected.addAll(wordList);
  }
}

@RunWith(MockitoJUnitRunner.class)
public class VoidInstanceMethodDispatcherTest extends AbstractMethodDispatcherTest {

  private Dispatcher dispatcher =
      new InstanceMethodDispatcher(peerUuid, messageBuilder, dispatcherConnector, objectStore);

  private Class targetClass = ClassForVoidInstanceMethodTest.class;

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    String methodName = "addHelloWorld";
    Class[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {};

    // dispatch
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.wordsCollected.size(), is(2));
  }

  @Test
  @Override
  public void dispatchIncoming_noArgs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addHelloWorld";
    Class[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(2));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withArgs_ok() throws Throwable {

    // signature
    String methodName = "addWord";
    Class[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {"hello"};

    // dispatch
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.wordsCollected.size(), is(1));
  }

  @Test
  @Override
  public void dispatchIncoming_withArgs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWord";
    Class[] parameterTypes = {String.class};
    Object[] args = {"hello"};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(1));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_withPrimitiveArgs_ok() throws Throwable {
    // signature
    String methodName = "addWords";
    Class[] parameterTypes = {int.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    int numberOfWordsToAdd = 5;
    Object[] args = {numberOfWordsToAdd};

    // dispatch
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.wordsCollected.size(), is(numberOfWordsToAdd));
  }

  @Test
  @Override
  public void dispatchIncoming_withPrimitiveArgs_ok() {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWords";
    Class[] parameterTypes = {int.class};
    int numberOfWordsToAdd = 15;
    Object[] args = {numberOfWordsToAdd};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(numberOfWordsToAdd));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withObjectRefArgs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWordList";
    Class[] parameterTypes = {List.class};
    List<String> wordList = Arrays.asList("the", "truth", "is", "out", "there");
    ObjectRef listObjRef = objectStore.storeObject(wordList);
    Object[] args = {null};
    ObjectRef[] argObjRefs = {listObjRef};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(2L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(wordList.size()));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatchIncoming_withNullArgs_ok() {
    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWord";
    Class[] parameterTypes = {List.class};
    Object[] args = {null};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(0));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_varargs_ok() throws Throwable {

    // signature
    String methodName = "addWords";
    Class[] parameterTypes = {String[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    String[] words = {"hey", "there", "!", "whats", "up", "?"};
    Object[] args = {words};

    // dispatch
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    Object returned = dispatcher.dispatch(ctxt, this, target, args);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledTwice();
    assertThat(returned, is(Void.getInstance()));
    assertThat(target.wordsCollected.size(), is(4));
  }

  @Test
  @Override
  public void dispatchIncoming_varargs_ok() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWords";
    Class[] parameterTypes = {String[].class};
    String[] words = {"hey", "there", "!", "whats", "up", "?"};
    Object[] args = {words};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertTrue(replyMsg.getReturnValue().getIsVoid());
    assertThat(target.wordsCollected.size(), is(4));
    assertThat(replyMsg.getReturnValue(), hasDeclaringClass(targetClass));
    assertThat(
        replyMsg.getReturnValue(), allOf(comesFromClass(targetClass), comesFrom(methodName)));
  }

  @Test
  @Override
  public void dispatch_throwsException_exceptionThrown() throws Throwable {

    // signature
    String methodName = "addWord";
    Class[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(null, -1, targetClass, signature);

    // args
    Object[] args = {","};

    // dispatch
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    try {
      Object returned = dispatcher.dispatch(ctxt, this, target, args);
      fail("Should have failed with an IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // all good
    }
    verifyDispatcherConnectorSendExecMessageCalledTwice();
  }

  @Test
  @Override
  public void dispatchIncoming_throwsException_exceptionThrown() {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectStore.storeObject(target);

    String methodName = "addWord";
    Class[] parameterTypes = {String.class};
    Object[] args = {","};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            target,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getFollowingUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectStore.size(), is(1L));
    assertFalse(replyMsg.hasReturnValue());
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.IllegalArgumentException"));
  }
}
