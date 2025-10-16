/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.chronicle;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycle;
import net.openhft.chronicle.queue.RollCycles;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MpscChunkedArrayQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/** ChronicleQueue-based tests for {@link ChronicleWalWriter}. */
public class ChronicleWalWriterTest extends ZmqEnabledTest {

  private static final UUID PEER_ID = UUID.randomUUID();
  private static final LogInfo WAL_INFO = new LogInfo("test_app", "n/a"); // bootstrapServers unused

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

  // ── fixtures ───────────────────────────────────────────────
  private ZContext zmqCtx;
  private HwmMessageQueue<OutboundMsg> walQueue;
  private ChronicleWalWriter chronicleWalWriter;
  private ServiceManager manager;
  private final ThreadGroup threadGroup = new ThreadGroup("services-thread-group");
  private final MessageBuilder builder = new MessageBuilder();
  private Path baseDir;

  private OutboundMsg dummy() {
    ExecMessage msg = builder.buildEmptyConstructor(PEER_ID, "java.lang.String");
    return new OutboundMsg(MessageType.UNKNOWN, ExecPhase.UNDEFINED, null, "DUMMY", null, msg);
  }

  @Before
  public void setUp() throws Exception {
    zmqCtx = createContext();
    baseDir = Files.createTempDirectory("chronicle-wal-test");

    walQueue = HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);
    AtomicBoolean walFailed = new AtomicBoolean(false);

    ChronicleQueueFactory factory = new DefaultChronicleQueueFactory();

    chronicleWalWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "ChronicleWalWriterTest-Service",
            walQueue,
            walFailed,
            /* offset.pub */ "inproc://offsets",
            null, // use default
            baseDir,
            "TEN_MINUTELY",
            null, // use default
            null, // use default
            factory);

    chronicleWalWriter.writeToLog(WAL_INFO, /* publishOffsets */ false);

    Set<Service> services = new HashSet<>(Collections.singletonList(chronicleWalWriter));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqCtx);
  }

  @After
  public void tearDown() throws Exception {
    if (manager != null) {
      manager.stopAsync().awaitStopped();
    }
    closeContext(zmqCtx);
  }

  @Test
  public void writeToLog_calledTwice_illegalStateException() {

    LogInfo WAL_INFO1 = new LogInfo("test_app", "n/a");
    LogInfo WAL_INFO2 = new LogInfo("test_app", "n/a");

    ChronicleWalWriter walWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "ChronicleWalWriterTest-Service",
            walQueue,
            new AtomicBoolean(false),
            /* offset.pub */ "inproc://offsets",
            null, // use default
            baseDir,
            "TEN_MINUTELY",
            null, // use default
            null, // use default
            new DefaultChronicleQueueFactory());

    walWriter.writeToLog(WAL_INFO1, /* publishOffsets */ false);
    try {
      // cannot call twice
      walWriter.writeToLog(WAL_INFO2, /* publishOffsets */ false);
      fail("Should have thrown a IllegalStateException");
    } catch (IllegalStateException e) {
      // expected
    }
  }

  @Test
  public void noPublishedMessages() {
    assertThat(chronicleWalWriter.isRunning(), is(true));
    // enqueue nothing → queue must be empty
    List<String> ids = readAllIds();
    assertThat(ids.isEmpty(), is(true));
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

    List<String> produced = readAllIds();
    List<String> sent =
        bodies.stream().map(ChronicleWalWriterTest::idOf).collect(Collectors.toList());

    assertThat(produced.size(), is(execCnt + interceptCnt));
    assertThat(produced, is(sent));
  }

  @Test
  public void chronicleExceptionSetsWalFailed() throws Exception {
    // Mocked queue/appender that always throws on write
    class FailingFactory implements ChronicleQueueFactory {
      final ChronicleQueue queue = mock(ChronicleQueue.class);
      final ExcerptAppender appender = mock(ExcerptAppender.class);

      FailingFactory() {
        when(queue.createAppender()).thenReturn(appender);
        doThrow(new RuntimeException("boom")).when(appender).writingDocument();
      }

      @Override
      public ChronicleQueue create(Path path, RollCycle rc, int indexSpacing, int blockSize) {
        return queue;
      }

      @Override
      public ChronicleQueue createReadOnly(Path path) {
        return queue;
      }
    }

    HwmMessageQueue<OutboundMsg> localQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);
    AtomicBoolean localWalFailed = new AtomicBoolean(false);
    Path tmpDir = Files.createTempDirectory("chronicle-fail");

    ChronicleWalWriter failingWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "FailingChronicleWriter",
            localQueue,
            localWalFailed,
            "inproc://offsets",
            null, // use default
            tmpDir,
            "TEN_MINUTELY",
            null, // use default
            null, // use default
            new FailingFactory());

    failingWriter.writeToLog(WAL_INFO, false);

    Thread worker = new Thread(failingWriter::run);
    worker.start();

    // enqueue one dummy message → should trigger exception
    localQueue.offer(dummy());

    long deadline = System.currentTimeMillis() + 1000;
    while (!localWalFailed.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }

    assertThat("walFailed must be true after fatal append", localWalFailed.get(), is(true));
    worker.join();
  }

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

  // ───────────────────────── helpers ─────────────────────────

  private List<String> readAllIds() {
    Path qPath = baseDir.resolve(WAL_INFO.getName());
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {

      List<String> ids = new ArrayList<>();
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        // WAL now stores: [type][bodyLen][body]; ID is inside the body
        Message m = new Message();
        m.unmarshal(om.getBody(), 0);
        ids.add(idOf(m));
      }
      return ids;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
