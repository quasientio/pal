/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.chronicle;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.concurrent.MpscKind;
import io.quasient.pal.core.transport.WalWriterStats;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

  // ==================== Test Specifications for Issue #545 ====================
  // The following tests are specifications awaiting implementation in issue #546.

  /**
   * Tests that writeMessage writes directly to the Chronicle log using thread-local appender.
   *
   * <p>Given: ChronicleWalWriter configured in direct-write (queueless) mode with open log When:
   * writeMessage(OutboundMsg) called from multiple threads Then: Messages are written to Chronicle
   * queue via thread-local appenders
   *
   * <p>This test verifies the direct-write code path where producer threads bypass the internal
   * queue and write directly using per-thread appenders. It complements the existing
   * directWrite_writesAndFlushOnClose test by testing concurrent access scenarios.
   */
  @Test
  public void testWriteMessage_writesDirectlyToLog() throws Exception {
    // Given: ChronicleWalWriter configured in direct-write mode (walQueue = null)
    Path directWriteDir = Files.createTempDirectory("chronicle-direct-write-test");
    LogInfo directWriteLog = new LogInfo("direct_write_log", "n/a");
    AtomicBoolean localWalFailed = new AtomicBoolean(false);

    ChronicleWalWriter directWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "DirectWriteTest-Service",
            /* walQueue */ null, // direct-write mode
            localWalFailed,
            "inproc://direct-offsets",
            "true", // flushOnClose
            directWriteDir,
            "TEN_MINUTELY",
            null,
            null,
            new DefaultChronicleQueueFactory());

    directWriter.writeToLog(directWriteLog, false);

    Set<Service> directServices = new HashSet<>(Collections.singletonList(directWriter));
    ServiceManager directManager = new ServiceManager(directServices);
    directManager.startAsync().awaitHealthy();
    collectGoSignals(directServices.size(), zmqCtx);

    // When: writeMessage() is called from multiple concurrent threads
    int numThreads = 4;
    int messagesPerThread = 5;
    List<String> allMessageIds = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch latch = new CountDownLatch(numThreads);
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);

    for (int t = 0; t < numThreads; t++) {
      @SuppressWarnings("unused")
      var unused =
          executor.submit(
              () -> {
                try {
                  for (int i = 0; i < messagesPerThread; i++) {
                    Message m =
                        builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String"));
                    String msgId = m.getExecMessage().getMessageId();
                    allMessageIds.add(msgId);
                    OutboundMsg outMsg =
                        new OutboundMsg(
                            MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, msgId, null, m);
                    directWriter.writeMessage(outMsg);
                  }
                } finally {
                  latch.countDown();
                }
              });
    }

    latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    // Stop service to trigger flush
    directManager.stopAsync().awaitStopped();

    // Then: Verify all messages appear in the Chronicle queue
    Path qPath = directWriteDir.resolve(directWriteLog.getName());
    List<String> writtenIds = new ArrayList<>();
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        Message m = new Message();
        m.unmarshal(om.getBody(), 0);
        writtenIds.add(idOf(m));
      }
    }

    assertThat(writtenIds.size(), is(numThreads * messagesPerThread));
    // Verify all message IDs are present (order may vary due to concurrent writes)
    assertThat(new HashSet<>(writtenIds), is(new HashSet<>(allMessageIds)));
  }

  /**
   * Tests that run() processes messages from the internal queue and writes them to Chronicle.
   *
   * <p>Given: ChronicleWalWriter with messages in internal HwmMessageQueue When: run() executes in
   * consumer thread Then: All messages are processed and written to Chronicle log
   *
   * <p>This test verifies the queue-based processing path where the consumer thread drains the
   * queue and appends messages to Chronicle. It complements publishedMixedMessages by focusing on
   * the run() method behavior rather than message content verification.
   */
  @Test
  public void testRun_processesMessagesFromQueue() throws Exception {
    // Given: ChronicleWalWriter with messages enqueued in walQueue
    Path queueTestDir = Files.createTempDirectory("chronicle-queue-test");
    LogInfo queueTestLog = new LogInfo("queue_test_log", "n/a");
    AtomicBoolean localWalFailed = new AtomicBoolean(false);
    HwmMessageQueue<OutboundMsg> localQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);

    ChronicleWalWriter queueWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "QueueProcessTest-Service",
            localQueue,
            localWalFailed,
            "inproc://queue-test-offsets",
            null, // use default flushOnClose
            queueTestDir,
            "TEN_MINUTELY",
            null,
            null,
            new DefaultChronicleQueueFactory());

    queueWriter.writeToLog(queueTestLog, false);

    // Enqueue messages before starting the service
    int numMessages = 10;
    List<String> enqueuedIds = new ArrayList<>();
    for (int i = 0; i < numMessages; i++) {
      Message m = builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String"));
      String msgId = m.getExecMessage().getMessageId();
      enqueuedIds.add(msgId);
      OutboundMsg outMsg = wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, m);
      localQueue.offer(outMsg);
    }

    // When: run() executes (service starts and processes queue)
    Set<Service> queueServices = new HashSet<>(Collections.singletonList(queueWriter));
    ServiceManager queueManager = new ServiceManager(queueServices);
    queueManager.startAsync().awaitHealthy();
    collectGoSignals(queueServices.size(), zmqCtx);

    // Wait for processing to complete (queue should drain quickly)
    Thread.sleep(200);

    // Verify counters
    WalWriterStats stats = queueWriter.getLiveStats();
    assertThat(
        "Should have received all messages", stats.messagesReceived(), is((long) numMessages));
    assertThat("Should have written all messages", stats.messagesWritten(), is((long) numMessages));

    // Stop service
    queueManager.stopAsync().awaitStopped();

    // Then: Verify queue is empty and messages appear in Chronicle
    assertThat("Queue should be empty", localQueue.isEmpty(), is(true));

    Path qPath = queueTestDir.resolve(queueTestLog.getName());
    List<String> writtenIds = new ArrayList<>();
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        Message m = new Message();
        m.unmarshal(om.getBody(), 0);
        writtenIds.add(idOf(m));
      }
    }

    assertThat(writtenIds.size(), is(numMessages));
    assertThat(writtenIds, is(enqueuedIds));
  }

  /**
   * Tests that run() handles thread interruption gracefully and exits cleanly.
   *
   * <p>Given: Running ChronicleWalWriter actively processing messages When: Thread is interrupted
   * Then: Writer exits gracefully with resources cleaned up properly
   *
   * <p>This test verifies the graceful shutdown path when the service thread is interrupted,
   * ensuring no message loss (when flushOnClose is true) and proper resource cleanup.
   */
  @Test
  public void testRun_handlesInterruptionGracefully() throws Exception {
    // Given: ChronicleWalWriter running and processing messages
    Path interruptTestDir = Files.createTempDirectory("chronicle-interrupt-test");
    LogInfo interruptTestLog = new LogInfo("interrupt_test_log", "n/a");
    AtomicBoolean localWalFailed = new AtomicBoolean(false);
    HwmMessageQueue<OutboundMsg> localQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);

    ChronicleWalWriter interruptWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "InterruptTest-Service",
            localQueue,
            localWalFailed,
            "inproc://interrupt-test-offsets",
            "true", // flushOnClose enabled
            interruptTestDir,
            "TEN_MINUTELY",
            null,
            null,
            new DefaultChronicleQueueFactory());

    interruptWriter.writeToLog(interruptTestLog, false);

    // Start service
    Set<Service> interruptServices = new HashSet<>(Collections.singletonList(interruptWriter));
    ServiceManager interruptManager = new ServiceManager(interruptServices);
    interruptManager.startAsync().awaitHealthy();
    collectGoSignals(interruptServices.size(), zmqCtx);

    // Enqueue messages while service is running
    int numMessages = 5;
    List<String> enqueuedIds = new ArrayList<>();
    for (int i = 0; i < numMessages; i++) {
      Message m = builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String"));
      String msgId = m.getExecMessage().getMessageId();
      enqueuedIds.add(msgId);
      OutboundMsg outMsg = wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, m);
      localQueue.offer(outMsg);
    }

    // Brief wait for some processing
    Thread.sleep(50);

    // When: Request graceful shutdown (which triggers interruption handling)
    interruptManager.stopAsync().awaitStopped(10, TimeUnit.SECONDS);

    // Then: Verify service stops without throwing
    assertThat("Service should have stopped", interruptWriter.isRunning(), is(false));

    // Verify queued messages were flushed to Chronicle before exit
    Path qPath = interruptTestDir.resolve(interruptTestLog.getName());
    List<String> writtenIds = new ArrayList<>();
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        Message m = new Message();
        m.unmarshal(om.getBody(), 0);
        writtenIds.add(idOf(m));
      }
    }

    // With flushOnClose=true, all messages should be written
    assertThat(writtenIds.size(), is(numMessages));
    assertThat(writtenIds, is(enqueuedIds));
  }

  /**
   * Tests that closeConnections properly closes all resources including appenders and queue.
   *
   * <p>Given: ChronicleWalWriter with open Chronicle queue, appender(s), and optional offset
   * publisher When: closeConnections is called Then: All resources (appender, queue, ZMQ socket,
   * Disruptor) are closed properly
   *
   * <p>This test verifies resource cleanup during shutdown, ensuring no resource leaks for
   * Chronicle queue handles, ZMQ sockets, and Disruptor instances.
   */
  @Test
  public void testCloseConnections_closesAllResources() throws Exception {
    // Given: ChronicleWalWriter with open connections (queue, appender, offset publisher)
    Path closeTestDir = Files.createTempDirectory("chronicle-close-test");
    LogInfo closeTestLog = new LogInfo("close_test_log", "n/a");
    AtomicBoolean localWalFailed = new AtomicBoolean(false);
    HwmMessageQueue<OutboundMsg> localQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);

    ChronicleWalWriter closeWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "CloseTest-Service",
            localQueue,
            localWalFailed,
            "inproc://close-test-offsets",
            "true",
            closeTestDir,
            "TEN_MINUTELY",
            null,
            null,
            new DefaultChronicleQueueFactory());

    // Enable offset publishing by passing true to writeToLog
    closeWriter.writeToLog(closeTestLog, true);

    // Start service
    Set<Service> closeServices = new HashSet<>(Collections.singletonList(closeWriter));
    ServiceManager closeManager = new ServiceManager(closeServices);
    closeManager.startAsync().awaitHealthy();
    collectGoSignals(closeServices.size(), zmqCtx);

    // Process some messages
    for (int i = 0; i < 3; i++) {
      Message m = builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String"));
      OutboundMsg outMsg = wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, m);
      localQueue.offer(outMsg);
    }

    Thread.sleep(100);

    // When: closeConnections() is called (via service stop)
    closeManager.stopAsync().awaitStopped(10, TimeUnit.SECONDS);

    // Then: Verify all resources are closed (no exceptions, service is terminated)
    assertThat("Service should have stopped", closeWriter.isRunning(), is(false));
    assertThat("Service should be terminated", closeWriter.state(), is(Service.State.TERMINATED));

    // Verify Chronicle queue can be reopened (queue was properly closed)
    Path qPath = closeTestDir.resolve(closeTestLog.getName());
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      int count = 0;
      while (OutboundMsg.readNext(tailer) != null) {
        count++;
      }
      assertThat("Messages should be readable after close", count, is(3));
    }
  }

  /**
   * Tests that writeMessageUsingAppender handles various message types correctly.
   *
   * <p>Given: Different OutboundMsg types (EXEC_CONSTRUCTOR, EXEC_METHOD, INTERCEPT_MESSAGE, etc.)
   * When: writeMessageUsingAppender is called for each type Then: Each message type is serialized
   * and written correctly to Chronicle
   *
   * <p>This test verifies that the appender correctly handles all supported message types, ensuring
   * proper serialization for each MessageType variant.
   */
  @Test
  public void testWriteMessageUsingAppender_handlesVariousMessageTypes() throws Exception {
    // Given: OutboundMsg instances of different MessageTypes
    Path msgTypesTestDir = Files.createTempDirectory("chronicle-msgtypes-test");
    LogInfo msgTypesTestLog = new LogInfo("msgtypes_test_log", "n/a");
    AtomicBoolean localWalFailed = new AtomicBoolean(false);
    HwmMessageQueue<OutboundMsg> localQueue =
        HwmMessageQueue.createQueue(MpscKind.CHUNKED, 1 << 10, 1 << 20);

    ChronicleWalWriter msgTypesWriter =
        new ChronicleWalWriter(
            PEER_ID,
            zmqCtx,
            SYNC_SOCKET_ADDRESS,
            threadGroup,
            "MsgTypesTest-Service",
            localQueue,
            localWalFailed,
            "inproc://msgtypes-test-offsets",
            null,
            msgTypesTestDir,
            "TEN_MINUTELY",
            null,
            null,
            new DefaultChronicleQueueFactory());

    msgTypesWriter.writeToLog(msgTypesTestLog, false);

    // Start service
    Set<Service> msgTypesServices = new HashSet<>(Collections.singletonList(msgTypesWriter));
    ServiceManager msgTypesManager = new ServiceManager(msgTypesServices);
    msgTypesManager.startAsync().awaitHealthy();
    collectGoSignals(msgTypesServices.size(), zmqCtx);

    // Create OutboundMsg for each supported MessageType
    // Note: We use the simpler message types that are easier to construct in tests
    List<OutboundMsg> messages = new ArrayList<>();
    List<MessageType> expectedTypes = new ArrayList<>();

    // 1. EXEC_CONSTRUCTOR
    ExecMessage constructorMsg = builder.buildEmptyConstructor(PEER_ID, "java.lang.String");
    messages.add(
        wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, builder.wrap(constructorMsg)));
    expectedTypes.add(MessageType.EXEC_CONSTRUCTOR);

    // 2. Another EXEC_CONSTRUCTOR with different class
    ExecMessage constructorMsg2 = builder.buildEmptyConstructor(PEER_ID, "java.lang.Integer");
    messages.add(
        wrap(MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, builder.wrap(constructorMsg2)));
    expectedTypes.add(MessageType.EXEC_CONSTRUCTOR);

    // 3. EXEC_GET_STATIC (get class/static variable)
    ExecMessage getStaticMsg = builder.buildGetStatic(PEER_ID, "java.lang.System", "out");
    messages.add(
        wrap(MessageType.EXEC_GET_STATIC, ExecPhase.BEFORE, null, builder.wrap(getStaticMsg)));
    expectedTypes.add(MessageType.EXEC_GET_STATIC);

    // 4. EXEC_PUT_STATIC (set class/static variable)
    ExecMessage putStaticMsg =
        builder.buildPutStatic(PEER_ID, "java.lang.System", "out", "java.io.PrintStream", null);
    messages.add(
        wrap(MessageType.EXEC_PUT_STATIC, ExecPhase.BEFORE, null, builder.wrap(putStaticMsg)));
    expectedTypes.add(MessageType.EXEC_PUT_STATIC);

    // 5. INTERCEPT_MESSAGE
    InterceptMessage interceptMsg =
        builder.buildInterceptMessage(
            PEER_ID,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.emptyList(),
            getClass().getName(),
            "callback");
    messages.add(
        wrap(MessageType.INTERCEPT_MESSAGE, ExecPhase.UNDEFINED, null, builder.wrap(interceptMsg)));
    expectedTypes.add(MessageType.INTERCEPT_MESSAGE);

    // When: Enqueue each message
    for (OutboundMsg msg : messages) {
      localQueue.offer(msg);
    }

    // Wait for processing
    Thread.sleep(200);

    // Stop service
    msgTypesManager.stopAsync().awaitStopped();

    // Then: Read back from Chronicle and verify each message type is preserved
    Path qPath = msgTypesTestDir.resolve(msgTypesTestLog.getName());
    List<MessageType> readTypes = new ArrayList<>();
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        readTypes.add(om.getMessageType());
      }
    }

    assertThat("Should have read all message types", readTypes.size(), is(expectedTypes.size()));
    assertThat("Message types should match", readTypes, is(expectedTypes));
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
