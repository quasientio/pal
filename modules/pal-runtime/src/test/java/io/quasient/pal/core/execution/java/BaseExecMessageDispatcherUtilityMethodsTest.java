/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for utility methods in BaseExecMessageDispatcher.
 *
 * <p>These tests focus on the helper methods that can be tested in isolation without requiring the
 * full dispatch flow or complex mocking of intercept infrastructure.
 */
public class BaseExecMessageDispatcherUtilityMethodsTest {

  private static final UUID PEER_UUID = UUID.randomUUID();

  private ObjectLookupStore objectLookupStore;
  private MessageBuilder messageBuilder;
  private TestableDispatcher dispatcher;

  @Before
  public void setUp() throws Exception {
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createAsyncManaged();
    messageBuilder = new MessageBuilder(PEER_UUID);
    dispatcher = new TestableDispatcher();

    // Inject dependencies via reflection
    setField(dispatcher, "peerUuid", PEER_UUID);
    setField(dispatcher, "objectLookupStore", objectLookupStore);
    setField(dispatcher, "messageBuilder", messageBuilder);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = AbstractDispatcher.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  // ===== Tests for generateObjectRef() =====

  @Test
  public void generateObjectRef_sameObject_returnsSameRef() {
    Object obj = new Object();
    ObjectRef ref1 = dispatcher.generateObjectRef(obj);
    ObjectRef ref2 = dispatcher.generateObjectRef(obj);

    assertThat(ref1, equalTo(ref2));
  }

  @Test
  public void generateObjectRef_differentObjects_returnsDifferentRefs() {
    Object obj1 = new Object();
    Object obj2 = new Object();
    ObjectRef ref1 = dispatcher.generateObjectRef(obj1);
    ObjectRef ref2 = dispatcher.generateObjectRef(obj2);

    assertThat(ref1, not(equalTo(ref2)));
  }

  @Test
  public void generateObjectRef_identityHashCodeBased_returnsExpectedRef() {
    Object obj = new Object();
    int expectedHash = System.identityHashCode(obj);
    ObjectRef ref = dispatcher.generateObjectRef(obj);

    assertThat(ref.getRef(), is(expectedHash));
  }

  // ===== Tests for storeObject() =====

  @Test
  public void storeObject_nullInput_returnsNull() {
    ObjectRef ref = dispatcher.storeObject(null);

    assertThat(ref, nullValue());
    assertThat(objectLookupStore.size(), is(0L));
  }

  @Test
  public void storeObject_nonNullInput_storesAndReturnsRef() {
    String obj = "test-object";
    ObjectRef ref = dispatcher.storeObject(obj);

    assertThat(ref, notNullValue());
    assertThat(objectLookupStore.size(), is(1L));
    assertThat(objectLookupStore.lookupObject(ref), sameInstance(obj));
  }

  @Test
  public void storeObject_sameObjectTwice_returnsSameRef() {
    Object obj = new Object();
    ObjectRef ref1 = dispatcher.storeObject(obj);
    ObjectRef ref2 = dispatcher.storeObject(obj);

    assertThat(ref1, equalTo(ref2));
    assertThat(objectLookupStore.size(), is(1L));
  }

  // ===== Tests for wrapAfterExecThrowableMessage() =====

  @Test
  public void wrapAfterExecThrowableMessage_exceptionWhileLoading_wrapsLoading() throws Exception {
    String responseToId = "test-msg-id-123";
    Method method = String.class.getDeclaredMethod("toString");
    Throwable loadingException = new ClassNotFoundException("Class not found");

    ExecMessage result =
        dispatcher.wrapAfterExecThrowableMessage(responseToId, method, loadingException, null);

    assertThat(result, notNullValue());
    assertThat(result.getResponseToId(), is(responseToId));
    assertThat(result.getRaisedThrowable(), notNullValue());
  }

  @Test
  public void wrapAfterExecThrowableMessage_exceptionWhileInvoking_wrapsInvoking()
      throws Exception {
    String responseToId = "test-msg-id-456";
    Method method = String.class.getDeclaredMethod("length");
    Throwable invokingException = new IllegalArgumentException("Bad argument");

    ExecMessage result =
        dispatcher.wrapAfterExecThrowableMessage(responseToId, method, null, invokingException);

    assertThat(result, notNullValue());
    assertThat(result.getResponseToId(), is(responseToId));
    assertThat(result.getRaisedThrowable(), notNullValue());
  }

  @Test
  public void wrapAfterExecThrowableMessage_bothExceptions_prefersLoading() throws Exception {
    String messageId = "test-msg-id-789";
    Method method = String.class.getDeclaredMethod("toString");
    Throwable loadingException = new NoSuchMethodException("Method not found");
    Throwable invokingException = new IllegalStateException("Invocation failed");

    ExecMessage result =
        dispatcher.wrapAfterExecThrowableMessage(
            messageId, method, loadingException, invokingException);

    assertThat(result, notNullValue());
    // The implementation prefers exceptionWhileLoading if both are present
  }

  // ===== Tests for isFieldPutOperation() via reflection =====

  @Test
  public void isFieldPutOperation_execPutField_returnsTrue() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod("isFieldPutOperation", MessageType.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(dispatcher, MessageType.EXEC_PUT_FIELD);

    assertThat(result, is(true));
  }

  @Test
  public void isFieldPutOperation_execPutStatic_returnsTrue() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod("isFieldPutOperation", MessageType.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(dispatcher, MessageType.EXEC_PUT_STATIC);

    assertThat(result, is(true));
  }

  @Test
  public void isFieldPutOperation_execGetField_returnsFalse() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod("isFieldPutOperation", MessageType.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(dispatcher, MessageType.EXEC_GET_FIELD);

    assertThat(result, is(false));
  }

  @Test
  public void isFieldPutOperation_execInstanceMethod_returnsFalse() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod("isFieldPutOperation", MessageType.class);
    method.setAccessible(true);

    boolean result = (boolean) method.invoke(dispatcher, MessageType.EXEC_INSTANCE_METHOD);

    assertThat(result, is(false));
  }

  // ===== Tests for applyArgMutations() via reflection =====

  @Test
  public void applyArgMutations_singleMutation_applied() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "applyArgMutations", List.class, List.class, Map.class);
    method.setAccessible(true);

    List<MessageArgument> currentArgs = new ArrayList<>();
    currentArgs.add(new MessageArgument("old1", true));
    currentArgs.add(new MessageArgument("old2", false));
    List<MessageArgument> originalArgs = new ArrayList<>(currentArgs);
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(0, "new1");

    @SuppressWarnings("unchecked")
    List<MessageArgument> result =
        (List<MessageArgument>) method.invoke(dispatcher, currentArgs, originalArgs, mutations);

    assertThat(result, hasSize(2));
    assertThat(result.get(0).object(), is("new1"));
    assertThat(result.get(0).byReference(), is(true)); // preserved from original
    assertThat(result.get(1).object(), is("old2"));
  }

  @Test
  public void applyArgMutations_multipleMutations_allApplied() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "applyArgMutations", List.class, List.class, Map.class);
    method.setAccessible(true);

    List<MessageArgument> currentArgs = new ArrayList<>();
    currentArgs.add(new MessageArgument("arg0", true));
    currentArgs.add(new MessageArgument("arg1", false));
    currentArgs.add(new MessageArgument("arg2", true));
    List<MessageArgument> originalArgs = new ArrayList<>(currentArgs);
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(0, "mutated0");
    mutations.put(2, "mutated2");

    @SuppressWarnings("unchecked")
    List<MessageArgument> result =
        (List<MessageArgument>) method.invoke(dispatcher, currentArgs, originalArgs, mutations);

    assertThat(result, hasSize(3));
    assertThat(result.get(0).object(), is("mutated0"));
    assertThat(result.get(1).object(), is("arg1"));
    assertThat(result.get(2).object(), is("mutated2"));
  }

  @Test
  public void applyArgMutations_outOfBounds_ignored() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "applyArgMutations", List.class, List.class, Map.class);
    method.setAccessible(true);

    List<MessageArgument> currentArgs = new ArrayList<>();
    currentArgs.add(new MessageArgument("arg0", true));
    List<MessageArgument> originalArgs = new ArrayList<>(currentArgs);
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(5, "ignored");

    @SuppressWarnings("unchecked")
    List<MessageArgument> result =
        (List<MessageArgument>) method.invoke(dispatcher, currentArgs, originalArgs, mutations);

    assertThat(result, hasSize(1));
    assertThat(result.get(0).object(), is("arg0"));
  }

  @Test
  public void applyArgMutations_negativeIndex_ignored() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "applyArgMutations", List.class, List.class, Map.class);
    method.setAccessible(true);

    List<MessageArgument> currentArgs = new ArrayList<>();
    currentArgs.add(new MessageArgument("arg0", false));
    List<MessageArgument> originalArgs = new ArrayList<>(currentArgs);
    Map<Integer, Object> mutations = new HashMap<>();
    mutations.put(-1, "ignored");

    @SuppressWarnings("unchecked")
    List<MessageArgument> result =
        (List<MessageArgument>) method.invoke(dispatcher, currentArgs, originalArgs, mutations);

    assertThat(result, hasSize(1));
    assertThat(result.get(0).object(), is("arg0"));
  }

  @Test
  public void applyArgMutations_emptyMutations_returnsUnchangedCopy() throws Exception {
    Method method =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "applyArgMutations", List.class, List.class, Map.class);
    method.setAccessible(true);

    List<MessageArgument> currentArgs = new ArrayList<>();
    currentArgs.add(new MessageArgument("arg0", true));
    List<MessageArgument> originalArgs = new ArrayList<>(currentArgs);
    Map<Integer, Object> mutations = new HashMap<>();

    @SuppressWarnings("unchecked")
    List<MessageArgument> result =
        (List<MessageArgument>) method.invoke(dispatcher, currentArgs, originalArgs, mutations);

    assertThat(result, hasSize(1));
    assertThat(result.get(0).object(), is("arg0"));
    assertThat(result, not(sameInstance(currentArgs))); // New list created
  }

  // ===== Tests for getClassNameFromPjp() via reflection =====

  @Test
  public void getClassNameFromPjp_method_returnsDeclaringClassName() throws Exception {
    Method privateMethod =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "getClassNameFromPjp", ProceedingJoinPoint.class);
    privateMethod.setAccessible(true);

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    Signature sig = mock(MethodSignature.class);
    when(pjp.getSignature()).thenReturn(sig);
    when(sig.getDeclaringTypeName()).thenReturn("java.lang.String");

    String result = (String) privateMethod.invoke(dispatcher, pjp);

    assertThat(result, is("java.lang.String"));
  }

  // ===== Tests for getMethodNameFromPjp() via reflection =====

  @Test
  public void getMethodNameFromPjp_method_returnsMethodName() throws Exception {
    Method privateMethod =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "getMethodNameFromPjp", ProceedingJoinPoint.class);
    privateMethod.setAccessible(true);

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    MethodSignature sig = mock(MethodSignature.class);
    when(pjp.getSignature()).thenReturn(sig);
    when(sig.getName()).thenReturn("toString");

    String result = (String) privateMethod.invoke(dispatcher, pjp);

    assertThat(result, is("toString"));
  }

  @Test
  public void getMethodNameFromPjp_constructor_returnsNew() throws Exception {
    Method privateMethod =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "getMethodNameFromPjp", ProceedingJoinPoint.class);
    privateMethod.setAccessible(true);

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    ConstructorSignature sig = mock(ConstructorSignature.class);
    when(pjp.getSignature()).thenReturn(sig);

    String result = (String) privateMethod.invoke(dispatcher, pjp);

    assertThat(result, is("new"));
  }

  @Test
  public void getMethodNameFromPjp_field_returnsFieldName() throws Exception {
    Method privateMethod =
        BaseExecMessageDispatcher.class.getDeclaredMethod(
            "getMethodNameFromPjp", ProceedingJoinPoint.class);
    privateMethod.setAccessible(true);

    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    FieldSignature sig = mock(FieldSignature.class);
    when(pjp.getSignature()).thenReturn(sig);
    when(sig.getName()).thenReturn("myField");

    String result = (String) privateMethod.invoke(dispatcher, pjp);

    assertThat(result, is("myField"));
  }

  // Note: Tests for getParamTypesFromPjp() and getParamTypesForTracking() were removed in #689.
  // These private methods were replaced by ParamTypeExtractor, which is tested in
  // ParamTypeExtractorTest.

  // ===== Testable dispatcher implementation =====

  /**
   * A testable implementation of BaseExecMessageDispatcher for unit testing.
   *
   * <p>This implementation exposes protected methods and provides minimal concrete implementations
   * of abstract methods.
   */
  static class TestableDispatcher extends BaseExecMessageDispatcher {

    /**
     * Exposes the protected generateObjectRef method for testing.
     *
     * @param object the object for which to generate a reference
     * @return a new ObjectRef instance
     */
    @Override
    public ObjectRef generateObjectRef(Object object) {
      return super.generateObjectRef(object);
    }

    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt,
        Object sender,
        Object target,
        Object[] args,
        boolean includeDeclaredExceptions) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      return new ExecMessage();
    }

    @Override
    protected Object invokeIncoming(
        AccessibleObject accessibleObject,
        Object target,
        List<MessageArgument> args,
        Object value) {
      return null;
    }

    @Override
    protected boolean returnsVoid(AccessibleObject accessibleObject) {
      return false;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return false;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Parameter> getParameterList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }
}
