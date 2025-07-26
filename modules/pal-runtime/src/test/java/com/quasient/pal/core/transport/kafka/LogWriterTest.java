/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

/*
 * Copyright (C) 2025 Quasient Inc.
 * Business Source License 1.1 – see LICENSE.
 */
package com.quasient.pal.core.transport.kafka;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.internal.concurrent.MpscKind;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.kafka.KafkaKeySerializer;
import com.quasient.pal.serdes.kafka.KafkaMessageSerializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Cluster;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/** Queue-based tests for {@link LogWriter}. */
public class LogWriterTest extends ZmqEnabledTest {

  // ── constants & helpers ───────────────────────────────────────────────
  private static final UUID PEER_ID = UUID.randomUUID();
  private static final LogInfo WAL_INFO = new LogInfo("test_app", "localhost:9092");

  private static OutboundMsg wrap(
      MessageType type, ExecPhase phase, List<InternalHeader> hdrs, Message body) {
    String msgId =
        body.getExecMessage() != null
            ? body.getExecMessage().getMessageId()
            : body.getInterceptMessage().getMessageId();
    String respId = body.getExecMessage() != null ? body.getExecMessage().getResponseToId() : null;

    return new OutboundMsg(type, phase, hdrs, msgId, respId, body);
  }

  private static String idOf(Message m) {
    if (m.getExecMessage() != null) return m.getExecMessage().getMessageId();
    if (m.getInterceptMessage() != null) return m.getInterceptMessage().getMessageId();
    throw new IllegalArgumentException(format("Unsupported message type: %s", m));
  }

  // ── test fixtures ─────────────────────────────────────────────────────
  private ZContext zmqCtx;
  private HwmMessageQueue<OutboundMsg> walQueue;
  private MockProducer<String, byte[]> mockProducer;
  private LogWriter logWriter;
  private ServiceManager manager;
  private final ThreadGroup threadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder builder = new MessageBuilder();

  private OutboundMsg dummy() {
    // minimal valid body to satisfy OutboundMsg ctor
    ExecMessage msg = builder.buildEmptyConstructor(PEER_ID, "java.lang.String");
    return new OutboundMsg(MessageType.UNKNOWN, ExecPhase.UNDEFINED, null, "DUMMY", null, msg);
  }

  @Before
  public void setUp() {
    zmqCtx = createContext();

    walQueue = HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);
    AtomicBoolean walFailed = new AtomicBoolean(false);

    mockProducer =
        new MockProducer<>(
            Cluster.empty(), true, null, new KafkaKeySerializer(), new KafkaMessageSerializer());

    logWriter =
        new LogWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "LogWriterTest-Service",
            walQueue,
            walFailed,
            /* offset.pub */ "inproc://offsets",
            props -> mockProducer);

    logWriter.writeToLog(WAL_INFO, /* publishOffsets */ false);

    Set<Service> services = new HashSet<>(Collections.singletonList(logWriter));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqCtx);
  }

  @After
  public void tearDown() throws InterruptedException {
    closeContext(zmqCtx);
    manager.stopAsync().awaitStopped();
  }

  @Test
  public void noPublishedMessages() {
    assertThat(logWriter.isRunning(), is(true));
    // enqueue nothing → producer history must stay empty
    assertThat(mockProducer.history().isEmpty(), is(true));
  }

  @Test
  public void publishedMixedMessages() throws Exception {
    int execCnt = 15, interceptCnt = 5;
    List<Message> bodies = new ArrayList<>();

    for (int i = 0; i < execCnt; i++) {
      bodies.add(builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String")));
    }
    for (int i = 0; i < interceptCnt; i++) {
      bodies.add(
          builder.wrap(
              builder.buildInterceptMessage(
                  PEER_ID,
                  InterceptType.BEFORE,
                  "java.io.PrintStream",
                  "println",
                  Collections.emptyList(),
                  getClass().getName(),
                  "callback")));
    }

    // enqueue
    bodies.forEach(
        m ->
            walQueue.offer(
                wrap(
                    m.getExecMessage() != null
                        ? MessageType.EXEC_CONSTRUCTOR
                        : MessageType.INTERCEPT_MESSAGE,
                    m.getExecMessage() != null ? ExecPhase.BEFORE : ExecPhase.UNDEFINED,
                    null,
                    m)));

    Thread.sleep(100);

    List<String> produced =
        mockProducer.history().stream()
            .map(
                rec -> {
                  Message m = new Message();
                  m.unmarshal(rec.value(), 0);
                  return idOf(m);
                })
            .collect(Collectors.toList());

    List<String> sent = bodies.stream().map(LogWriterTest::idOf).collect(Collectors.toList());

    assertThat(mockProducer.history().size(), is(execCnt + interceptCnt));
    assertThat(produced, is(sent));
  }

  @Test
  public void publishedMessagesWithHeader() throws Exception {
    int cnt = 5;
    List<Message> bodies = new ArrayList<>();
    for (int i = 0; i < cnt; i++) {
      bodies.add(builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String")));
    }

    List<InternalHeader> hdrs = Collections.singletonList(builder.buildWriteAheadHeader(PEER_ID));
    bodies.forEach(
        m -> walQueue.offer(wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, hdrs, m)));

    Thread.sleep(100);

    List<String> produced =
        mockProducer.history().stream()
            .map(
                rec -> {
                  Message m = new Message();
                  m.unmarshal(rec.value(), 0);
                  return idOf(m);
                })
            .collect(Collectors.toList());

    List<String> sent = bodies.stream().map(LogWriterTest::idOf).collect(Collectors.toList());

    assertThat(mockProducer.history().size(), is(cnt));
    assertThat(produced, is(sent));
  }

  // ── extra check: queue overflow (bounded variant) ─────────────────────
  @Test
  public void boundedQueueOverflowStopsProducer() {
    int initial = 4;
    int max = 8;

    MessagePassingQueue<OutboundMsg> tiny = new MpscChunkedArrayQueue<>(initial, max);
    for (int i = 0; i < max; i++) {
      assertThat(tiny.offer(dummy()), is(true));
    }
    assertThat(tiny.offer(dummy()), is(false));
  }

  // ── extra check: failing producer flips walFailed flag ────────────────
  @Test
  public void kafkaExceptionSetsWalFailed() throws Exception {
    // Custom producer that always fails
    class FailingProducer extends MockProducer<String, byte[]> {
      FailingProducer() {
        super(Cluster.empty(), true, null, new KafkaKeySerializer(), new KafkaMessageSerializer());
      }

      @Override
      public synchronized Future<RecordMetadata> send(ProducerRecord<String, byte[]> record) {
        throw new RuntimeException("boom"); // <─ immediate failure
      }

      @Override
      public synchronized Future<RecordMetadata> send(
          ProducerRecord<String, byte[]> record, Callback cb) {
        throw new RuntimeException("boom"); // not used, but safe
      }
    }

    // ── fresh, private queue & flag ────────────────────────────────
    HwmMessageQueue<OutboundMsg> methodLocalQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);

    AtomicBoolean methodLocalWalFailed = new AtomicBoolean(false);

    LogWriter failingLogWriter =
        new LogWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "FailingWriter",
            methodLocalQueue,
            methodLocalWalFailed,
            "inproc://offsets",
            p -> new FailingProducer());
    failingLogWriter.writeToLog(WAL_INFO, false);

    Thread worker = new Thread(failingLogWriter::run);
    worker.start();

    // enqueue one dummy message
    methodLocalQueue.offer(dummy());

    // wait up to 1 s for the flag to flip
    long deadline = System.currentTimeMillis() + 1000;
    while (!methodLocalWalFailed.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    assertThat("walFailed must be true after fatal send", methodLocalWalFailed.get(), is(true));

    worker.join();
  }
}
