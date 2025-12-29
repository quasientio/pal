/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
