/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.execution.java.ConstructorDispatcher;
import io.quasient.pal.core.execution.java.ExecMessageDispatcher;
import io.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for {@link RpcPolicyChecker} integration into the dispatch path via {@code
 * BaseExecMessageDispatcher.dispatchIncoming()}.
 *
 * <p>These tests verify that the policy check is correctly wired into the dispatch path, before any
 * reflective loading occurs, and that replay injection is exempt from policy enforcement.
 */
public class RpcPolicyDispatchIntegrationTest {

  /** Sample class for constructor invocation tests. */
  @SuppressWarnings("unused")
  public static class SampleClass {
    public SampleClass() {}
  }

  private UUID peerUuid;
  private MessageBuilder messageBuilder;
  private ObjectLookupStore objectLookupStore;
  private OutboundMessageGateway gateway;
  private ReflectionHelper reflectionHelper;
  private Set<RunOptions> runOptions;

  @Before
  public void setUp() {
    peerUuid = UUID.randomUUID();
    messageBuilder = new MessageBuilder();
    objectLookupStore = ConcurrentHashMapObjectLookupStore.createSyncManaged();
    gateway = mock(OutboundMessageGateway.class);
    when(gateway.sendExecMessage(any(), any()))
        .thenAnswer(
            invocation -> {
              Object arg = invocation.getArgument(0);
              if (arg instanceof Message messageArg) {
                return messageArg.getExecMessage();
              }
              throw new IllegalArgumentException(
                  "Expected Message, got " + arg.getClass().getName());
            });
    when(gateway.sendMessageToSessionService(any())).thenReturn(null);
    reflectionHelper = new ReflectionHelper();
    runOptions = EnumSet.of(RunOptions.WITH_TCP_PUB);
  }

  /**
   * Creates a ConstructorDispatcher wired with the given RpcPolicyChecker.
   *
   * @param checker the policy checker to wire
   * @return a fully wired dispatcher
   */
  private ExecMessageDispatcher createDispatcher(RpcPolicyChecker checker) throws Exception {
    ConstructorDispatcher dispatcher =
        new ConstructorDispatcher(
            peerUuid, runOptions, messageBuilder, gateway, reflectionHelper, objectLookupStore);
    // Wire the policy checker via reflection since AbstractDispatcher is package-private
    Field checkerField = findField(dispatcher.getClass(), "rpcPolicyChecker");
    checkerField.setAccessible(true);
    checkerField.set(dispatcher, checker);
    return dispatcher;
  }

  /**
   * Finds a field by name in the class hierarchy.
   *
   * @param clazz the class to search
   * @param fieldName the field name
   * @return the field
   * @throws NoSuchFieldException if the field is not found
   */
  private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }

  /**
   * Verifies that {@code dispatchIncoming()} throws {@link RpcAccessDeniedException} when the
   * policy denies the target operation, and that no reflective class/method loading occurs.
   */
  @Test
  public void shouldDenyWhenPolicyRejectsinDispatchIncoming() throws Exception {
    // Given: deny-all policy
    RpcPolicy denyAll = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(denyAll);
    ExecMessageDispatcher dispatcher = createDispatcher(checker);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, SampleClass.class.getName());

    // When/Then: dispatchIncoming throws RpcAccessDeniedException
    try {
      dispatcher.dispatchIncoming(incomingMessage, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      assertThat(e.getClassName(), is(SampleClass.class.getName()));
    }
  }

  /**
   * Verifies that {@code dispatchIncoming()} proceeds with normal execution (reflective loading and
   * invocation) when the policy allows the target operation.
   */
  @Test
  public void shouldAllowWhenPolicyAcceptsInDispatchIncoming() throws Exception {
    // Given: allow-all policy
    RpcPolicy allowAll = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    RpcPolicyChecker checker = new RpcPolicyChecker(allowAll);
    ExecMessageDispatcher dispatcher = createDispatcher(checker);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, SampleClass.class.getName());

    // When: dispatchIncoming is called
    ExecMessage response =
        dispatcher.dispatchIncoming(incomingMessage, MessageChannelType.WEBSOCKET_RPC);

    // Then: Normal execution proceeds, constructor is invoked successfully
    assertNotNull(response);
    assertThat(response.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(response.getRaisedThrowable());
    assertThat(response.getReturnValue(), is(notNullValue()));
  }

  /**
   * Verifies that messages arriving via the {@code REPLAY_INJECTION} channel bypass the policy
   * check entirely, even when a deny-all policy is configured.
   */
  @Test
  public void shouldSkipPolicyCheckForReplayInjection() throws Exception {
    // Given: deny-all policy
    RpcPolicy denyAll = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(denyAll);
    ExecMessageDispatcher dispatcher = createDispatcher(checker);

    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, SampleClass.class.getName());

    // When: dispatchIncoming called with REPLAY_INJECTION channel
    ExecMessage response =
        dispatcher.dispatchIncoming(incomingMessage, MessageChannelType.REPLAY_INJECTION);

    // Then: No RpcAccessDeniedException, execution proceeds normally
    assertNotNull(response);
    assertThat(response.getResponseToId(), is(incomingMessage.getMessageId()));
    assertNull(response.getRaisedThrowable());
    assertThat(response.getReturnValue(), is(notNullValue()));
  }

  /**
   * Verifies that the policy check occurs before any reflective loading (i.e., {@code
   * loadAccessibleObject()} is NOT called when the policy denies access), ensuring the check
   * short-circuits the dispatch path early.
   */
  @Test
  public void shouldCheckPolicyBeforeReflectiveLoading() throws Exception {
    // Given: deny-all policy AND a class name that doesn't exist (would throw if loaded)
    RpcPolicy denyAll = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyChecker checker = new RpcPolicyChecker(denyAll);
    ExecMessageDispatcher dispatcher = createDispatcher(checker);

    // Use a non-existent class - if loadAccessibleObject is called, it would throw
    // ClassNotFoundException. If the policy check short-circuits, we get RpcAccessDeniedException.
    ExecMessage incomingMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, "com.example.nonexistent.Denied");

    // When/Then: RpcAccessDeniedException is thrown (NOT ClassNotFoundException)
    try {
      dispatcher.dispatchIncoming(incomingMessage, MessageChannelType.ZMQ_SOCKET_RPC);
      fail("Expected RpcAccessDeniedException");
    } catch (RpcAccessDeniedException e) {
      // Policy check fired before reflective loading
      assertThat(e.getClassName(), is("com.example.nonexistent.Denied"));
    }
  }
}
