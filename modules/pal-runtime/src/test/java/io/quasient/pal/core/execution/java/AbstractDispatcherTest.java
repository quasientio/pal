/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.runtime.Dispatcher;
import io.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import io.quasient.pal.core.rpc.policy.RpcPolicyChecker;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;

public abstract class AbstractDispatcherTest {

  protected UUID peerUuid = UUID.randomUUID();

  protected ObjectLookupStore objectLookupStore =
      ConcurrentHashMapObjectLookupStore.createSyncManaged();

  protected MessageBuilder messageBuilder = new MessageBuilder();

  protected ReflectionHelper reflectionHelper = new ReflectionHelper();

  /** Permissive RPC policy checker that allows all operations (for test use). */
  protected RpcPolicyChecker rpcPolicyChecker =
      new RpcPolicyChecker(new RpcPolicy(List.of(), RpcPolicyAction.ALLOW));

  protected Set<RunOptions> runOptions = EnumSet.noneOf(RunOptions.class);
  protected OutboundMessageGateway outboundMessageGateway;
  protected Dispatcher dispatcher;

  protected AbstractDispatcherTest() {}

  /**
   * Wires the RPC policy checker into a dispatcher. Call this after creating each dispatcher in
   * subclass setUp() methods.
   */
  protected void wireRpcPolicyChecker(Dispatcher d) {
    ((AbstractDispatcher) d).setRpcPolicyChecker(rpcPolicyChecker);
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledTimes(int n) {
    verify(outboundMessageGateway, times(n)).sendExecMessage(any(), any());
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledTwice() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(2);
  }

  protected void verifyDispatcherConnectorSendExecMessageCalledOnce() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(1);
  }

  protected void verifyDispatcherConnectorSendExecMessageNeverCalled() {
    verifyDispatcherConnectorSendExecMessageCalledTimes(0);
  }

  protected String[] toNames(Class<?>[] types) {
    return Arrays.stream(types).map(Class::getName).toList().toArray(new String[types.length]);
  }

  @Before
  public void setUp() {
    assertThat(objectLookupStore.size(), is(0L));
    outboundMessageGateway = mock(OutboundMessageGateway.class);
    when(outboundMessageGateway.sendExecMessage(any(), any()))
        .thenAnswer(
            invocation -> {
              Object arg = invocation.getArgument(0);
              if (arg instanceof Message messageArg) {
                return messageArg.getExecMessage();
              } else {
                throw new IllegalArgumentException(
                    "Expected Message, got " + arg.getClass().getName());
              }
            });
    when(outboundMessageGateway.sendMessageToSessionService(any())).thenReturn(null);
  }

  @After
  public void resetStuff() {
    objectLookupStore.clear();
    assertThat(objectLookupStore.size(), is(0L));
    Mockito.reset(outboundMessageGateway);
  }

  // Stubs for accessibility tests
  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_publicAccessibleObject_noException() throws Throwable;

  @SuppressWarnings("unused")
  public abstract void
      dispatchIncoming_packagePrivateAccessibleObject_reflectiveOperationException()
          throws Throwable;

  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_protectedAccessibleObject_reflectiveOperationException()
      throws Throwable;

  @SuppressWarnings("unused")
  public abstract void dispatchIncoming_privateAccessibleObject_reflectiveOperationException()
      throws Throwable;
}
