/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core.exec.java;

import static net.ittera.pal.core.ExecMessageMatchers.ComesFromClass.comesFromClass;
import static net.ittera.pal.core.ExecMessageMatchers.ComesFromReflectable.comesFrom;
import static net.ittera.pal.core.ExecMessageMatchers.HasDeclaringClassOf.hasDeclaringClass;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.junit.Ignore;
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

  private final Dispatcher dispatcher =
      new InstanceMethodDispatcher(
          peerUuid, messageBuilder, dispatcherConnector, reflectionHelper, objectLookupStore);

  private final Class<?> targetClass = ClassForVoidInstanceMethodTest.class;

  private final String sourceFilename = "NotARealClass.java";

  @Test
  @Override
  public void dispatch_noArgs_ok() throws Throwable {

    // signature
    String methodName = "addHelloWorld";
    Class<?>[] parameterTypes = {};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addHelloWorld";
    Class<?>[] parameterTypes = {};
    ObjectRef[] argObjRefs = {};
    Object[] args = {};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
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
    Class<?>[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {"hello"};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();

    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
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
    Class<?>[] parameterTypes = {int.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {int.class};
    int numberOfWordsToAdd = 15;
    Object[] args = {numberOfWordsToAdd};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWordList";
    Class<?>[] parameterTypes = {List.class};
    List<String> wordList = Arrays.asList("the", "truth", "is", "out", "there");
    ObjectRef listObjRef = objectLookupStore.storeObject(wordList);
    Object[] args = {null};
    ObjectRef[] argObjRefs = {listObjRef};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(2L));
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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {List.class};
    Object[] args = {null};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
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
    Class<?>[] parameterTypes = {String[].class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWords";
    Class<?>[] parameterTypes = {String[].class};
    String[] words = {"hey", "there", "!", "whats", "up", "?"};
    Object[] args = {words};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
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
    Class<?>[] parameterTypes = {String.class};
    Signature signature =
        new MethodSignature(targetClass.getDeclaredMethod(methodName, parameterTypes));

    // ctxt
    Context ctxt = new Context(sourceFilename, -1, targetClass, signature);

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
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    String methodName = "addWord";
    Class<?>[] parameterTypes = {String.class};
    Object[] args = {","};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.IllegalArgumentException"));
  }

  @Ignore
  @Test
  @Override
  public void dispatchIncoming_throwsAmbiguousCallException_exceptionThrown() throws Exception {}

  @Override
  @Test
  public void dispatchIncoming_throwsNoSuchMethodException_exceptionThrown() throws Exception {

    // create and store new instance
    ClassForVoidInstanceMethodTest target = new ClassForVoidInstanceMethodTest();
    ObjectRef targetObjRef = objectLookupStore.storeObject(target);

    // we use a method name that exists but with wrong parameter types
    String methodName = "addWord";
    Class<?>[] parameterTypes = {Integer.class};
    Object[] args = {"489"};
    ObjectRef[] argObjRefs = {null};

    ExecMessage incomingMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            targetClass.getName(),
            methodName,
            targetObjRef,
            toNames(parameterTypes),
            args,
            argObjRefs);

    // dispatch
    ExecMessage replyMsg = ((ExecMessageDispatcher) dispatcher).dispatchIncoming(incomingMessage);

    // expect
    verifyDispatcherConnectorSendExecMessageCalledOnce();
    assertThat(replyMsg.getResponseToUuid(), is(incomingMessage.getMessageUuid()));
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(replyMsg.getReturnValue(), is(nullValue()));
    assertThat(
        replyMsg.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.NoSuchMethodException"));
  }
}
