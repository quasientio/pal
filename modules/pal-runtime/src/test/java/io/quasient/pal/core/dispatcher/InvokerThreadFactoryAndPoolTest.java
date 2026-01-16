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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import io.quasient.pal.core.dispatcher.thread.InvokerThreadFactory;
import io.quasient.pal.core.dispatcher.thread.ThreadPool;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class InvokerThreadFactoryAndPoolTest {

  private ZContext ctx;

  @Before
  public void setup() {
    ctx = new ZContext(1);
  }

  @After
  public void cleanup() {
    ctx.close();
  }

  private static final class TestInvoker extends AbstractMessageInvokerThread {
    TestInvoker(
        ThreadGroup group,
        String name,
        ZContext zmqContext,
        MessageBuilder messageBuilder,
        IncomingMessageDispatcher dispatcher,
        OutboundMessageGateway gateway,
        UUID peerUuid) {
      super(group, name, zmqContext, messageBuilder, dispatcher, gateway, peerUuid);
    }

    @Override
    public void run() {}
  }

  private static final class TestFactory extends InvokerThreadFactory {
    public void testInit(
        ZContext ctx,
        MessageBuilder mb,
        IncomingMessageDispatcher disp,
        OutboundMessageGateway gw,
        MessageChannelType ch,
        ClassLoader cl,
        UUID id) {
      init(ctx, mb, disp, gw, ch, cl, id);
    }

    @Override
    protected AbstractMessageInvokerThread createInvokerThread(String threadName) {
      return new TestInvoker(
          threadGroup,
          threadName,
          zmqContext,
          messageBuilder,
          incomingMessageDispatcher,
          outboundMessageGateway,
          peerUuid);
    }
  }

  @Test
  public void factory_createsNamedThreads_andTracksCreatedList() {
    var factory = new TestFactory();
    var dispatcher = mock(IncomingMessageDispatcher.class);
    var gateway = mock(OutboundMessageGateway.class);
    var mb = new MessageBuilder(UUID.randomUUID());
    factory.testInit(
        ctx,
        mb,
        dispatcher,
        gateway,
        MessageChannelType.ZMQ_SOCKET_RPC,
        Thread.currentThread().getContextClassLoader(),
        UUID.randomUUID());

    Thread t = factory.newThread(() -> {});
    assertThat(t, notNullValue());
    assertThat(t.getName(), containsString("ZMQ_SOCKET_RPC Executor"));
    assertThat(t.getThreadGroup().getName(), is("ZMQ_SOCKET_RPC Executor Group"));
    // not daemon per factory
    assertThat(t.isDaemon(), is(false));
    List<Thread> created = factory.getCreatedThreads();
    assertThat(created.size(), is(1));
    assertThat(created.get(0).getName(), is(t.getName()));
  }

  @Test
  public void threadPool_startsAndShutsDownThreads() throws Exception {
    var factory = new TestFactory();
    var dispatcher = mock(IncomingMessageDispatcher.class);
    var gateway = mock(OutboundMessageGateway.class);
    var mb = new MessageBuilder(UUID.randomUUID());
    factory.testInit(
        ctx,
        mb,
        dispatcher,
        gateway,
        MessageChannelType.LOG_RPC,
        Thread.currentThread().getContextClassLoader(),
        UUID.randomUUID());

    ThreadPool pool = new ThreadPool(3, factory);
    pool.startAllThreads();
    assertThat(factory.getCreatedThreads().size(), is(3));
    // Signal shutdown
    pool.shutdown();
  }
}
