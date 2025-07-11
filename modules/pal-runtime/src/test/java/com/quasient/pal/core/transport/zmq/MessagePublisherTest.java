/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.zmq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.InternalHeaderType;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscUnboundedArrayQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * Tests the queue-driven {@link MessagePublisher}.
 *
 * <p>Producers push {@link OutboundMsg}s directly into a JCTools queue; the subscriber socket
 * verifies everything was published.
 */
public class MessagePublisherTest extends ZmqEnabledTest {

  // ── constants ──────────────────────────────────────────────────────────
  private static final String OUT_PUB_ADDRESS = "inproc://pub";

  // ── helpers & fixtures ─────────────────────────────────────────────────
  private final UUID peerUuid = UUID.randomUUID();
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private ZContext context;
  private ServiceManager manager;
  private Socket subSocket;
  private MessagePassingQueue<OutboundMsg> pubQueue;
  private InternalHeader writeAheadHeader;

  @Before
  public void setup() throws Exception {
    writeAheadHeader = msgBuilder.buildWriteAheadHeader(peerUuid);
    context = createContext();

    // shared producer→consumer queue
    pubQueue = new MpscUnboundedArrayQueue<>(1 << 10);

    MessagePublisher publisher =
        new MessagePublisher(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            new ThreadGroup("mp-tests"),
            "MessagePublisherTest-Service",
            pubQueue,
            OUT_PUB_ADDRESS);

    // start via Guava ServiceManager
    manager = new ServiceManager(Set.of(publisher));
    manager.startAsync().awaitHealthy();
    collectGoSignals(1, context);
    assertThat(publisher.isRunning(), is(true));

    // SUB socket to capture what the publisher emits
    subSocket = context.createSocket(SocketType.SUB);
    subSocket.connect(OUT_PUB_ADDRESS);
    subSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);
  }

  @After
  public void cleanup() throws Exception {
    if (subSocket != null) subSocket.close();
    manager.stopAsync().awaitStopped(1, TimeUnit.SECONDS);
    closeContext(context);
  }

  // ── test 1 – single ExecMessage without headers ────────────────────────
  @Test
  public void sendExecMessage() {
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    OutboundMsg out =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            null,
            msg.getMessageId(),
            null,
            msgBuilder.wrap(msg));

    assertThat(pubQueue.offer(out), is(true)); // enqueue

    OutboundMsg published = OutboundMsg.receive(subSocket, true);
    assertThat(published, notNullValue());
    assertThat(published, is(out)); // byte-wise equality

    Message unmarshalled = new Message();
    unmarshalled.unmarshal(published.getBody(), 0);
    assertThat(unmarshalled.getExecMessage(), is(msg));
  }

  // ── test 2 – ExecMessage with WRITE_AHEAD header ───────────────────────
  @Test
  public void sendExecMessageWithHeaders() {
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    OutboundMsg out =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR,
            ExecPhase.BEFORE,
            List.of(writeAheadHeader),
            msg.getMessageId(),
            null,
            msgBuilder.wrap(msg));

    assertThat(pubQueue.offer(out), is(true));

    OutboundMsg published = OutboundMsg.receive(subSocket, true);
    assertThat(published, notNullValue());
    assertThat(published, is(out));

    // verify header & body
    assertThat(published.getHeaders(), notNullValue());
    InternalHeader h = published.getHeaders().get(0);
    assertThat(h.getHeaderType(), is(InternalHeaderType.WRITE_AHEAD.toByte()));
    assertThat(h.getValue(), is(peerUuid.toString()));

    Message unmarshalled = new Message();
    unmarshalled.unmarshal(published.getBody(), 0);
    assertThat(unmarshalled.getExecMessage(), is(msg));
  }

  // ── test 3 – burst of ExecMessages keeps order ─────────────────────────
  @Test
  public void sendManyExecMessages() {
    int count = 15;
    List<ExecMessage> sent = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      sent.add(msg);
      pubQueue.offer(
          new OutboundMsg(
              MessageType.EXEC_CONSTRUCTOR,
              ExecPhase.BEFORE,
              null,
              msg.getMessageId(),
              null,
              msgBuilder.wrap(msg)));
    }

    List<ExecMessage> received = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      OutboundMsg published = OutboundMsg.receive(subSocket, true);
      assertThat(published, notNullValue());
      Message m = new Message();
      m.unmarshal(published.getBody(), 0);
      received.add(m.getExecMessage());
    }

    assertThat(received, is(sent)); // order preserved
  }

  // ── extra sanity: queue offer() should never block/throw ───────────────
  @Test
  public void queueAcceptsBurstOf1000() {
    for (int i = 0; i < 1000; i++) {
      assertThat(pubQueue.offer(dummyMsg(i)), is(true));
    }
  }

  private OutboundMsg dummyMsg(int i) {
    ExecMessage msgBody = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    return new OutboundMsg(
        MessageType.UNKNOWN, ExecPhase.UNDEFINED, null, "DUMMY-" + i, null, msgBody);
  }
}
