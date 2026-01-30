/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.lang.intercept.AroundSocketAccessor;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.execution.java.ClassMethodDispatcher;
import io.quasient.pal.core.execution.java.ConstructorDispatcher;
import io.quasient.pal.core.execution.java.GetClassVariableDispatcher;
import io.quasient.pal.core.execution.java.GetInstanceVariableDispatcher;
import io.quasient.pal.core.execution.java.InstanceMethodDispatcher;
import io.quasient.pal.core.execution.java.SetClassVariableDispatcher;
import io.quasient.pal.core.execution.java.SetInstanceVariableDispatcher;
import io.quasient.pal.core.intercept.IncomingInterceptCallbackDispatcher;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link IncomingMessageDispatcher} routing logic.
 *
 * <p>Tests all message type routing paths including execution messages, control messages, meta
 * messages, and intercept callbacks.
 */
public class IncomingMessageDispatcherRoutingTest {

  private IncomingMessageDispatcher dispatcher;
  private final UUID peer = UUID.randomUUID();
  private final MessageBuilder builder = new MessageBuilder(peer);

  // Mocks for all dispatchers
  private ConstructorDispatcher constructorDispatcher;
  private ClassMethodDispatcher classMethodDispatcher;
  private InstanceMethodDispatcher instanceMethodDispatcher;
  private GetClassVariableDispatcher getClassVariableDispatcher;
  private SetClassVariableDispatcher setClassVariableDispatcher;
  private GetInstanceVariableDispatcher getInstanceVariableDispatcher;
  private SetInstanceVariableDispatcher setInstanceVariableDispatcher;
  private ControlMessageDispatcher controlMessageDispatcher;
  private MetaMessageDispatcher metaMessageDispatcher;
  private IncomingInterceptCallbackDispatcher incomingInterceptCallbackDispatcher;

  /**
   * Sets a private field value on the target object using reflection.
   *
   * @param target the object to modify
   * @param field the field name
   * @param value the value to set
   * @throws Exception if reflection fails
   */
  private static void set(Object target, String field, Object value) throws Exception {
    Field f = target.getClass().getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  /** Sets up the dispatcher with all mocked dependencies. */
  @Before
  public void setup() throws Exception {
    dispatcher = new IncomingMessageDispatcher();

    // Create mocks for all dispatchers
    constructorDispatcher = mock(ConstructorDispatcher.class);
    classMethodDispatcher = mock(ClassMethodDispatcher.class);
    instanceMethodDispatcher = mock(InstanceMethodDispatcher.class);
    getClassVariableDispatcher = mock(GetClassVariableDispatcher.class);
    setClassVariableDispatcher = mock(SetClassVariableDispatcher.class);
    getInstanceVariableDispatcher = mock(GetInstanceVariableDispatcher.class);
    setInstanceVariableDispatcher = mock(SetInstanceVariableDispatcher.class);
    controlMessageDispatcher = mock(ControlMessageDispatcher.class);
    metaMessageDispatcher = mock(MetaMessageDispatcher.class);
    incomingInterceptCallbackDispatcher = mock(IncomingInterceptCallbackDispatcher.class);

    // Configure mocks to echo the input message
    when(constructorDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(classMethodDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(instanceMethodDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(getClassVariableDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(setClassVariableDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(getInstanceVariableDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));
    when(setInstanceVariableDispatcher.dispatchIncoming(any(), any()))
        .thenAnswer(inv -> (ExecMessage) inv.getArgument(0));

    // Inject all mocks
    set(dispatcher, "constructorDispatcher", constructorDispatcher);
    set(dispatcher, "classMethodDispatcher", classMethodDispatcher);
    set(dispatcher, "instanceMethodDispatcher", instanceMethodDispatcher);
    set(dispatcher, "getClassVariableDispatcher", getClassVariableDispatcher);
    set(dispatcher, "setClassVariableDispatcher", setClassVariableDispatcher);
    set(dispatcher, "getInstanceVariableDispatcher", getInstanceVariableDispatcher);
    set(dispatcher, "setInstanceVariableDispatcher", setInstanceVariableDispatcher);
    set(dispatcher, "controlMessageDispatcher", controlMessageDispatcher);
    set(dispatcher, "metaMessageDispatcher", metaMessageDispatcher);
    set(dispatcher, "incomingInterceptCallbackDispatcher", incomingInterceptCallbackDispatcher);
  }

  // ========== EXEC_CONSTRUCTOR ==========

  /** Tests that EXEC_CONSTRUCTOR messages route to the constructor dispatcher. */
  @Test
  public void incomingCall_constructor_routesToConstructorDispatcher() {
    ExecMessage req =
        builder.buildConstructor(
            peer,
            "java.lang.String",
            new String[] {"java.lang.String"},
            new Object[] {"test"},
            this,
            null);
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.LOG_RPC);
    assertThat(resp, is(req));
    verify(constructorDispatcher).dispatchIncoming(req, MessageChannelType.LOG_RPC);
  }

  // ========== EXEC_CLASS_METHOD ==========

  /** Tests that EXEC_CLASS_METHOD messages route to the class method dispatcher. */
  @Test
  public void incomingCall_classMethod_routesToClassMethodDispatcher() {
    ExecMessage req =
        builder.buildClassMethod(
            peer,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_CLASS_METHOD, MessageChannelType.CLI_RPC);
    assertThat(resp, is(req));
    verify(classMethodDispatcher).dispatchIncoming(req, MessageChannelType.CLI_RPC);
  }

  // ========== EXEC_INSTANCE_METHOD ==========

  /** Tests that EXEC_INSTANCE_METHOD messages route to the instance method dispatcher. */
  @Test
  public void incomingCall_instanceMethod_routesToInstanceMethodDispatcher() {
    ObjectRef targetRef = ObjectRef.from(1);
    ExecMessage req =
        builder.buildInstanceMethod(
            peer, "java.lang.String", "length", targetRef, new String[] {}, new Object[] {});
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_INSTANCE_METHOD, MessageChannelType.LOG_RPC);
    assertThat(resp, is(req));
    verify(instanceMethodDispatcher).dispatchIncoming(req, MessageChannelType.LOG_RPC);
  }

  // ========== EXEC_GET_STATIC ==========

  /** Tests that EXEC_GET_STATIC messages route to the get class variable dispatcher. */
  @Test
  public void incomingCall_getStatic_routesToGetClassVariableDispatcher() {
    ExecMessage req = builder.buildGetStatic(peer, "java.lang.Integer", "MAX_VALUE");
    ExecMessage resp =
        dispatcher.incomingCall(
            req, MessageType.EXEC_GET_STATIC, MessageChannelType.ZMQ_SOCKET_RPC);
    assertThat(resp, is(req));
    verify(getClassVariableDispatcher).dispatchIncoming(req, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  // ========== EXEC_PUT_STATIC ==========

  /** Tests that EXEC_PUT_STATIC messages route to the set class variable dispatcher. */
  @Test
  public void incomingCall_putStatic_routesToSetClassVariableDispatcher() {
    ExecMessage req =
        builder.buildPutStatic(
            peer, "com.example.Config", "DEBUG", "java.lang.Boolean", Boolean.TRUE);
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_PUT_STATIC, MessageChannelType.CLI_RPC);
    assertThat(resp, is(req));
    verify(setClassVariableDispatcher).dispatchIncoming(req, MessageChannelType.CLI_RPC);
  }

  // ========== EXEC_GET_FIELD ==========

  /** Tests that EXEC_GET_FIELD messages route to the get instance variable dispatcher. */
  @Test
  public void incomingCall_getField_routesToGetInstanceVariableDispatcher() {
    ObjectRef targetRef = ObjectRef.from(1);
    ExecMessage req = builder.buildGetObject(peer, "java.lang.String", "value", targetRef);
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_GET_FIELD, MessageChannelType.LOG_RPC);
    assertThat(resp, is(req));
    verify(getInstanceVariableDispatcher).dispatchIncoming(req, MessageChannelType.LOG_RPC);
  }

  // ========== EXEC_PUT_FIELD ==========

  /** Tests that EXEC_PUT_FIELD messages route to the set instance variable dispatcher. */
  @Test
  public void incomingCall_putField_routesToSetInstanceVariableDispatcher() {
    ObjectRef targetRef = ObjectRef.from(2);
    ExecMessage req =
        builder.buildPutObject(
            peer, "com.example.Person", "name", targetRef, "java.lang.String", "Alice");
    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_PUT_FIELD, MessageChannelType.ZMQ_SOCKET_RPC);
    assertThat(resp, is(req));
    verify(setInstanceVariableDispatcher).dispatchIncoming(req, MessageChannelType.ZMQ_SOCKET_RPC);
  }

  // ========== UNSUPPORTED MESSAGE TYPE ==========

  /** Tests that unsupported message types throw UnsupportedMessageException. */
  @Test(expected = UnsupportedMessageException.class)
  public void incomingCall_unsupportedType_throwsUnsupportedMessageException() {
    ExecMessage req =
        builder.buildClassMethod(
            peer,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    // CONTROL_MESSAGE_REQUEST is not a valid exec message type for incomingCall
    dispatcher.incomingCall(req, MessageType.CONTROL_MESSAGE_REQUEST, MessageChannelType.LOG_RPC);
  }

  /** Tests that the exception message includes helpful information. */
  @Test
  public void incomingCall_unsupportedType_exceptionContainsMessageDetails() {
    ExecMessage req =
        builder.buildClassMethod(
            peer,
            "java.lang.String",
            "valueOf",
            new String[] {"int"},
            this,
            null,
            new Object[] {1});
    try {
      dispatcher.incomingCall(req, MessageType.META_MESSAGE_REQUEST, MessageChannelType.LOG_RPC);
    } catch (UnsupportedMessageException e) {
      assertThat(e.getMessage(), containsString("no handler"));
      return;
    }
    throw new AssertionError("Expected UnsupportedMessageException");
  }

  // ========== CONTROL MESSAGE ==========

  /** Tests that control messages are delegated to the control message dispatcher. */
  @Test
  public void incomingControlMessage_delegatesToControlMessageDispatcher() {
    ControlMessage req = new ControlMessage();
    ControlMessage resp = new ControlMessage();
    when(controlMessageDispatcher.incomingControlMessage(req)).thenReturn(resp);

    ControlMessage result = dispatcher.incomingControlMessage(req);

    assertThat(result, is(resp));
    verify(controlMessageDispatcher).incomingControlMessage(req);
  }

  // ========== META MESSAGE ==========

  /** Tests that meta messages are delegated to the meta message dispatcher. */
  @Test
  public void incomingMetaMessage_delegatesToMetaMessageDispatcher() {
    MetaMessage req = new MetaMessage();
    MetaMessage resp = new MetaMessage();
    when(metaMessageDispatcher.incomingMetaMessage(req)).thenReturn(resp);

    MetaMessage result = dispatcher.incomingMetaMessage(req);

    assertThat(result, is(resp));
    verify(metaMessageDispatcher).incomingMetaMessage(req);
  }

  // ========== INTERCEPT CALLBACK ==========

  /** Tests that intercept callbacks are delegated to the intercept callback dispatcher. */
  @Test
  public void incomingInterceptCallback_delegatesToInterceptCallbackDispatcher() {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    InterceptCallbackResponseMessage resp = new InterceptCallbackResponseMessage();
    when(incomingInterceptCallbackDispatcher.handleCallback(req)).thenReturn(resp);

    InterceptCallbackResponseMessage result = dispatcher.incomingInterceptCallback(req);

    assertThat(result, is(resp));
    verify(incomingInterceptCallbackDispatcher).handleCallback(req);
  }

  // ========== AROUND INTERCEPT CALLBACK ==========

  /** Tests that AROUND intercept callbacks are delegated with socket accessor. */
  @Test
  public void incomingAroundInterceptCallback_delegatesToInterceptCallbackDispatcherWithSocket() {
    InterceptCallbackRequestMessage req = new InterceptCallbackRequestMessage();
    AroundSocketAccessor socketAccessor = mock(AroundSocketAccessor.class);
    InterceptCallbackResponseMessage resp = new InterceptCallbackResponseMessage();
    when(incomingInterceptCallbackDispatcher.handleAroundCallback(req, socketAccessor))
        .thenReturn(resp);

    InterceptCallbackResponseMessage result =
        dispatcher.incomingAroundInterceptCallback(req, socketAccessor);

    assertThat(result, is(resp));
    verify(incomingInterceptCallbackDispatcher).handleAroundCallback(req, socketAccessor);
  }

  // ========== CHANNEL TYPE PROPAGATION ==========

  /** Tests that the message channel type is correctly passed to dispatchers. */
  @Test
  public void incomingCall_channelTypePropagated_toDispatcher() {
    ExecMessage req =
        builder.buildConstructor(
            peer,
            "java.lang.String",
            new String[] {"java.lang.String"},
            new Object[] {"test"},
            this,
            null);

    // Test with different channel types
    dispatcher.incomingCall(req, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.LOG_RPC);
    verify(constructorDispatcher).dispatchIncoming(req, MessageChannelType.LOG_RPC);

    dispatcher.incomingCall(req, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.ZMQ_SOCKET_RPC);
    verify(constructorDispatcher).dispatchIncoming(req, MessageChannelType.ZMQ_SOCKET_RPC);

    dispatcher.incomingCall(req, MessageType.EXEC_CONSTRUCTOR, MessageChannelType.CLI_RPC);
    verify(constructorDispatcher).dispatchIncoming(req, MessageChannelType.CLI_RPC);
  }

  /** Tests that response from dispatcher is returned unchanged. */
  @Test
  public void incomingCall_dispatcherResponse_returnedUnchanged() {
    ExecMessage req = builder.buildGetStatic(peer, "java.lang.Integer", "MAX_VALUE");
    ExecMessage customResponse = new ExecMessage();

    when(getClassVariableDispatcher.dispatchIncoming(any(), any())).thenReturn(customResponse);

    ExecMessage resp =
        dispatcher.incomingCall(req, MessageType.EXEC_GET_STATIC, MessageChannelType.LOG_RPC);

    assertThat(resp, is(customResponse));
  }
}
