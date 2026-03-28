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
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.net.URL;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class ExecutorsCtorTest {

  private ZContext ctx;

  @Before
  public void setup() {
    ctx = new ZContext(1);
  }

  @After
  public void cleanup() {
    ctx.close();
  }

  @Test
  public void logRpcExecutor_constructs_withInprocDealer() {
    IncomingMessageDispatcher disp = mock(IncomingMessageDispatcher.class);
    OutboundMessageGateway gw = mock(OutboundMessageGateway.class);
    CustomClassloader cl =
        new CustomClassloader(new URL[] {}, Thread.currentThread().getContextClassLoader());
    MessageBuilder mb = new MessageBuilder(UUID.randomUUID());
    LogRpcExecutor exec =
        new LogRpcExecutor(
            "2", // pool
            ctx,
            "inproc://dealer.exec",
            mb,
            disp,
            gw,
            cl,
            UUID.randomUUID());
    assertThat(exec, notNullValue());
    // No threads started yet
  }

  @Test
  public void socketRpcExecutor_constructs_withInprocDealers() {
    IncomingMessageDispatcher disp = mock(IncomingMessageDispatcher.class);
    OutboundMessageGateway gw = mock(OutboundMessageGateway.class);
    CustomClassloader cl =
        new CustomClassloader(new URL[] {}, Thread.currentThread().getContextClassLoader());
    MessageBuilder mb = new MessageBuilder(UUID.randomUUID());
    SocketRpcExecutor exec =
        new SocketRpcExecutor(
            "1",
            ctx,
            EnumSet.noneOf(RunOptions.class),
            "inproc://rpc",
            "inproc://json",
            mb,
            disp,
            gw,
            cl,
            UUID.randomUUID());
    assertThat(exec, notNullValue());
  }
}
