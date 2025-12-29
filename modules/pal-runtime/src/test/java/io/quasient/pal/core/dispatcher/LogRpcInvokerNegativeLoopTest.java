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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.types.MessageFormatType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class LogRpcInvokerNegativeLoopTest {

  private ZContext context;
  private ZMQ.Socket dealer;
  private IncomingMessageDispatcher dispatcher;
  private LogRpcInvoker invoker;
  private Thread t;
  private String address;

  @Before
  public void setUp() {
    try {
      context = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to ZMQ sandbox", t);
      return;
    }
    address = "inproc://log.dealer." + UUID.randomUUID();
    dealer = context.createSocket(SocketType.DEALER);
    dealer.bind(address);
    dispatcher = mock(IncomingMessageDispatcher.class);
    invoker =
        new LogRpcInvoker(
            new ThreadGroup("svc"),
            "log-inv-neg",
            context,
            new MessageBuilder(UUID.randomUUID()),
            address,
            dispatcher,
            mock(OutboundMessageGateway.class),
            UUID.randomUUID());
    t = new Thread(invoker, "log-invoker");
    t.start();
  }

  @After
  public void tearDown() throws Exception {
    if (t != null) {
      t.interrupt();
      t.join(1500);
    }
    if (context != null) {
      context.close();
    }
  }

  @Test
  public void invalidJson_noDispatch() throws Exception {
    // invalid JSON payload
    byte[] body = "{\"jsonrpc\":\"2.0\",\"id\":\"x\"".getBytes(StandardCharsets.UTF_8);
    InboundLogMsg m = new InboundLogMsg(1L, MessageFormatType.JSON, new RecordHeaders(), body);
    m.send(dealer);
    TimeUnit.MILLISECONDS.sleep(50);
    verify(dispatcher, times(0)).incomingCall(Mockito.any(), Mockito.any(), Mockito.any());
  }

  @Test
  public void binaryParseError_noDispatch() throws Exception {
    byte[] body = new byte[] {1, 2, 3, 4, 5}; // not a valid colfer payload
    InboundLogMsg m = new InboundLogMsg(2L, MessageFormatType.BINARY, new RecordHeaders(), body);
    m.send(dealer);
    TimeUnit.MILLISECONDS.sleep(50);
    verify(dispatcher, times(0)).incomingCall(Mockito.any(), Mockito.any(), Mockito.any());
  }
}
