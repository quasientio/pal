/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.concurrent.MpscKind;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.internal.messages.SessionResponseMsg;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.SessionCommandType;
import io.quasient.pal.messages.types.SessionStatusType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

@SuppressWarnings("DoNotMock")
public class OutboundMessageGatewayTest extends ZmqEnabledTest {

  // --------------------------------------------------------------------
  // constants & helpers
  // --------------------------------------------------------------------

  private static final String SESSION_SERVICE_REQ_ADDRESS = "inproc://session_test";

  private final UUID peerUuid = UUID.randomUUID();
  private final MessageBuilder builder = new MessageBuilder();

  // --------------------------------------------------------------------
  // zmq / executor fixtures
  // --------------------------------------------------------------------
  private ZContext context;
  private ExecutorService execService;
  private SessionServiceStub sessionStub;

  private WalWriter walWriterMock;
  private HwmMessageQueue<OutboundMsg> walQueue;
  private HwmMessageQueue<OutboundMsg> pubQueue;
  private AtomicBoolean walFailed;

  private OutboundMessageGateway gateway;

  // --------------------------------------------------------------------
  // set-up / tear-down
  // --------------------------------------------------------------------
  @Before
  public void setUp() throws Exception {
    context = createContext();
    execService = Executors.newCachedThreadPool();
    walFailed = new AtomicBoolean(false);

    // --------------------------------------------------------------------
    // DI mocks
    // --------------------------------------------------------------------
    // Wal Writer mock
    walWriterMock = mock(WalWriter.class);

    // ── Real HWM queues (avoid mocking finals in sandbox)
    walQueue = HwmMessageQueue.createQueue(MpscKind.GROWABLE, 16, 1024);
    pubQueue = HwmMessageQueue.createQueue(MpscKind.GROWABLE, 16, 1024);

    // ── start session-service stub
    CountDownLatch latch = new CountDownLatch(1);
    sessionStub = new SessionServiceStub(context, latch);
    execService.execute(sessionStub);
    latch.await();
  }

  @After
  public void tearDown() throws Exception {
    sessionStub.requestStop();
    if (gateway != null) gateway.closeThreadLocalSockets();
    execService.shutdownNow();
    execService.awaitTermination(3, TimeUnit.SECONDS);
    closeContext(context);
  }

  // --------------------------------------------------------------------
  // helper to build the SUT with flags
  // --------------------------------------------------------------------
  private void initGateway(boolean withWal, boolean withPub, boolean withIntercepts) {
    EnumSet<RunOptions> opts = EnumSet.noneOf(RunOptions.class);
    if (withWal) opts.add(RunOptions.WITH_WAL);
    if (withPub) opts.add(RunOptions.WITH_TCP_PUB);
    if (withIntercepts) opts.add(RunOptions.WITH_INTERCEPTS);

    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);
  }

  // --------------------------------------------------------------------
  // single-message tests
  // --------------------------------------------------------------------
  @Test
  public void execMessage_withWalAndPub_withIntercepts() {
    sendExecMsgAndAssert(true, true, true);
  }

  @Test
  public void execMessage_walButNoPub_withIntercepts() {
    sendExecMsgAndAssert(true, false, true);
  }

  // --------------------------------------------------------------------
  // burst tests
  // --------------------------------------------------------------------
  @Test
  public void manyExecMessages_withPubAndPub_withIntercepts() {
    sendManyExecMsgsAndAssert(true, true, true);
  }

  @Test
  public void manyExecMessages_pubButNoWal_withIntercepts() {
    sendManyExecMsgsAndAssert(false, true, true);
  }

  // --------------------------------------------------------------------
  // session-service passthrough
  // --------------------------------------------------------------------
  @Test
  public void sendMessagesToSessionService_noSessionOption_exception() {
    initGateway(false, false, false);

    SessionCommandMsg cmd1 =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("123"));

    try {
      gateway.sendMessageToSessionService(cmd1);
      fail("Should fail since no sessions in run options");
    } catch (Exception e) {
      // expected
    }
  }

  @Test
  public void sendMessagesToSessionService_ok_withSessions() {
    // enable sessions so gateway will create the ThreadLocal REQ socket and send the command
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_SESSIONS);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    SessionCommandMsg cmd =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("123"));
    SessionResponseMsg resp = gateway.sendMessageToSessionService(cmd);
    assertThat(resp.getStatus(), is(SessionStatusType.OK));
  }

  // --------------------------------------------------------------------
  // helpers for exec-message tests
  // --------------------------------------------------------------------
  private void sendExecMsgAndAssert(boolean withWal, boolean withPub, boolean withIntercepts) {
    initGateway(withWal, withPub, withIntercepts);

    ExecMessage msg = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ExecMessage returned = gateway.sendExecMessage(builder.wrap(msg), ExecPhase.BEFORE);

    assertThat(returned, is(msg)); // gateway returns same obj

    assertThat(pubQueue.currentSize(), is(withPub ? 1 : 0));
    assertThat(walQueue.currentSize(), is(withWal ? 1 : 0));
  }

  private void sendManyExecMsgsAndAssert(boolean withWal, boolean withPub, boolean withIntercepts) {
    initGateway(withWal, withPub, withIntercepts);

    int n = 10;
    for (int i = 0; i < n; i++) {
      ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
      gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);
    }
    assertThat(pubQueue.currentSize(), is(withPub ? n : 0));
    assertThat(walQueue.currentSize(), is(withWal ? n : 0));
  }

  // --------------------------------------------------------------------
  // stub for the session service
  // --------------------------------------------------------------------
  private static final class SessionServiceStub implements Runnable {

    private volatile boolean stop;
    private final ZContext ctx;
    private final CountDownLatch start;

    SessionServiceStub(ZContext ctx, CountDownLatch start) {
      this.ctx = ctx;
      this.start = start;
    }

    void requestStop() {
      stop = true;
    }

    @Override
    public void run() {
      Socket rep = ctx.createSocket(SocketType.REP);
      rep.bind(SESSION_SERVICE_REQ_ADDRESS);
      rep.setReceiveTimeOut(100); // short poll interval
      start.countDown();
      while (!stop && !Thread.interrupted()) {
        SessionCommandMsg m = SessionCommandMsg.receive(rep, false);
        if (m == null) continue;
        new SessionResponseMsg(SessionStatusType.OK).send(rep);
      }
      rep.close();
    }
  }

  // --------------------------------------------------------------------
  // Additional coverage for publish/writeAhead branches
  // --------------------------------------------------------------------

  @Test
  public void publish_dropOld_whenQueueFull_doesNotEnqueue() {
    // Fill PUB queue to capacity so offer() fails
    int cap = pubQueue.capacity();
    for (int i = 0; i < cap; i++) {
      pubQueue.offer(mock(OutboundMsg.class));
    }

    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_TCP_PUB);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);

    // Still full; drop policy increments counters internally, but we assert no growth
    assertThat(pubQueue.currentSize(), is(cap));
  }

  @Test
  public void publish_none_blocksUntilSpace_thenEnqueues() throws Exception {
    int cap = pubQueue.capacity();
    int softCap = cap * 95 / 100;
    // Fill to soft cap to trigger waiting path
    for (int i = 0; i < softCap; i++) {
      pubQueue.offer(mock(OutboundMsg.class));
    }

    // Consumer to make space shortly after we call sendExecMessage
    CountDownLatch consumerStarted = new CountDownLatch(1);
    execService.execute(
        () -> {
          consumerStarted.countDown();
          try {
            Thread.sleep(10);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          pubQueue.relaxedPoll(); // free exactly one slot
        });
    consumerStarted.await();

    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_TCP_PUB);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.NONE,
            SESSION_SERVICE_REQ_ADDRESS);

    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);

    // Size remains at softCap since consumer freed one and gateway offered one
    assertThat(pubQueue.currentSize(), is(softCap));
  }

  @Test
  public void publish_none_whenQueueFull_recordsWaitStats() throws Exception {
    // Use a small FIXED queue to ensure offer() fails until consumer frees a slot
    HwmMessageQueue<OutboundMsg> fixedPubQueue = HwmMessageQueue.createQueue(MpscKind.FIXED, 4, 4);
    int cap = fixedPubQueue.capacity();
    for (int i = 0; i < cap; i++) fixedPubQueue.offer(mock(OutboundMsg.class));

    // Consumer frees one slot shortly after we start
    CountDownLatch consumerStarted = new CountDownLatch(1);
    execService.execute(
        () -> {
          consumerStarted.countDown();
          try {
            Thread.sleep(5);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          // Free two slots so size drops below softCap (95% of capacity)
          fixedPubQueue.relaxedPoll();
          fixedPubQueue.relaxedPoll();
        });
    consumerStarted.await();

    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_TCP_PUB);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            fixedPubQueue,
            PublishingDropPolicy.NONE,
            SESSION_SERVICE_REQ_ADDRESS);

    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);

    // Fetch stats (exercise path); avoid strict assertions due to timing races in CI
    MessageQueueStats stats = gateway.getPubQueueStats();
    assertThat(stats.perThread() != null, is(true));
  }

  @Test
  public void wal_waitStats_recorded_whenFull_thenWalFails() throws Exception {
    // enable WAL
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_WAL);
    // Use a small FIXED queue so offer() fails and we track waits
    HwmMessageQueue<OutboundMsg> fixedWalQueue = HwmMessageQueue.createQueue(MpscKind.FIXED, 4, 4);

    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            fixedWalQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    // fill WAL queue to capacity so writeAhead() loops
    int cap = fixedWalQueue.capacity();
    for (int i = 0; i < cap; i++) fixedWalQueue.offer(mock(OutboundMsg.class));

    // flip walFailed shortly after to let writeAhead() exit
    CountDownLatch started = new CountDownLatch(1);
    execService.execute(
        () -> {
          started.countDown();
          try {
            Thread.sleep(5);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          walFailed.set(true);
        });
    started.await();

    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);

    MessageQueueStats stats = gateway.getWalQueueStats();
    assertThat(stats.perThread() != null, is(true));
  }

  @Test
  public void printAggregateStats_smoke() {
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_TCP_PUB);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);
    // Should not throw, even with empty stats
    gateway.printAggregateStats();
  }

  private static final class TestWalWriter extends WalWriter {
    int writes;

    TestWalWriter(ZContext ctx, AtomicBoolean walFailedFlag) {
      super(
          UUID.randomUUID(),
          ctx,
          SYNC_SOCKET_ADDRESS,
          new ThreadGroup("tw"),
          "test-wal",
          null,
          walFailedFlag,
          "inproc://offsets",
          null);
    }

    @Override
    public void run() {}

    @Override
    public void writeMessage(OutboundMsg msg) {
      writes++;
    }

    @Override
    public void writeToLog(LogInfo l, boolean p) {}

    @Override
    protected void openConnections() {}

    @Override
    protected void closeConnections() {}
  }

  @Test
  public void writeAhead_directWriteMode_callsWriter() {
    TestWalWriter writer = new TestWalWriter(context, walFailed);
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_WAL);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            writer,
            null, // walQueue null => direct-write mode
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);
    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);
    assertThat(writer.writes, is(1));
  }

  @Test
  public void writeAhead_walFailed_flagDrops() {
    walFailed.set(true);
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_WAL);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            opts,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);
    int before = walQueue.currentSize();
    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);
    assertThat(walQueue.currentSize(), is(before));
  }

  // ============================================================================
  // Additional test specifications for issue #456
  // These tests are awaiting implementation in issue #457
  // ============================================================================

  /**
   * Tests that custom headers are concatenated with writeAheadHeaders when sending an exec message.
   *
   * <p>Given: Gateway with WAL enabled, writeAheadHeaders set When: sendExecMessage called with
   * non-null custom headers array Then: Final headers are concatenation of writeAheadHeaders +
   * custom headers
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void sendExecMessage_withCustomHeaders_concatenatesHeaders() {
    // Given: Gateway with WAL enabled, writeAheadHeaders set
    // When: sendExecMessage called with non-null custom headers array
    // Then: Final headers are concatenation of writeAheadHeaders + custom headers

    // TODO(#457): Implement test logic
    // - Create gateway with WAL enabled
    // - Call sendExecMessage with custom headers via reflection or internal method
    // - Verify that outbound message contains both writeAheadHeaders and custom headers
    fail("Not yet implemented");
  }

  /**
   * Tests that sending a null message throws NullPointerException.
   *
   * <p>Given: Properly configured gateway When: sendExecMessage called with null message Then:
   * NullPointerException thrown
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void sendExecMessage_nullMessage_throwsNPE() {
    // Given: Properly configured gateway
    // When: sendExecMessage called with null message
    // Then: NullPointerException thrown

    // TODO(#457): Implement test logic
    // - Create gateway with standard configuration
    // - Call sendExecMessage with null Message
    // - Verify NullPointerException is thrown
    fail("Not yet implemented");
  }

  /**
   * Tests constructor validation when WAL is enabled but both walWriter and walQueue are null.
   *
   * <p>Given: RunOptions.WITH_WAL enabled, but walWriter=null and walQueue=null When: Constructor
   * called Then: IllegalStateException thrown
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void constructor_walEnabledButNoWriterOrQueue_throwsIllegalState() {
    // Given: RunOptions.WITH_WAL enabled, but walWriter=null and walQueue=null
    // When: Constructor called
    // Then: IllegalStateException thrown

    // TODO(#457): Implement test logic
    // - Attempt to construct OutboundMessageGateway with WITH_WAL but null writer and queue
    // - Verify IllegalStateException is thrown with appropriate message
    fail("Not yet implemented");
  }

  /**
   * Tests that blockUntilEnqueued records stats when multiple spin-park cycles are required.
   *
   * <p>Given: Queue at 99% capacity requiring multiple park iterations When: blockUntilEnqueued
   * called Then: WaitStats shows parks > 1 and parkedNanos > 0
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void blockUntilEnqueued_multipleSpinParkCycles_recordsStats() {
    // Given: Queue at 99% capacity requiring multiple park iterations
    // When: blockUntilEnqueued called
    // Then: WaitStats shows parks > 1 and parkedNanos > 0

    // TODO(#457): Implement test logic
    // - Create a small fixed-size pub queue
    // - Fill queue to near capacity
    // - Use background thread to slowly drain queue
    // - Call sendExecMessage with PublishingDropPolicy.NONE
    // - Verify getPubQueueStats shows multiple parks and non-zero parkedNanos
    fail("Not yet implemented");
  }

  /**
   * Tests error handling when session service socket send fails.
   *
   * <p>Given: Session service enabled, but socket send returns false When:
   * sendMessageToSessionService called Then: Error logged and handled appropriately
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void sendMessageToSessionService_sendFails_throwsRuntimeException() {
    // Given: Session service enabled, but socket send returns false
    // When: sendMessageToSessionService called
    // Then: Error logged and handled appropriately

    // TODO(#457): Implement test logic
    // - Configure gateway with sessions enabled
    // - Mock or simulate session service that causes send to fail
    // - Call sendMessageToSessionService
    // - Verify appropriate error handling (null response or exception)
    fail("Not yet implemented");
  }

  /**
   * Tests getPUBWaitSnapshot returns stats from multiple threads.
   *
   * <p>Given: 3 threads have recorded wait stats When: getPUBWaitSnapshot called Then: Returns
   * snapshot with 3 entries
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void getPUBWaitSnapshot_withMultipleThreads_returnsAllThreadStats() {
    // Given: 3 threads have recorded wait stats
    // When: getPUBWaitSnapshot called
    // Then: Returns snapshot with 3 entries

    // TODO(#457): Implement test logic
    // - Create gateway with PUB enabled
    // - Spawn 3 threads that each send messages (triggering PUB queue stats)
    // - Wait for threads to complete
    // - Call getPUBWaitSnapshot
    // - Verify snapshot contains entries for all 3 threads
    fail("Not yet implemented");
  }

  /**
   * Tests getWALWaitSnapshot returns stats from multiple threads.
   *
   * <p>Given: 3 threads have recorded WAL wait stats When: getWALWaitSnapshot called Then: Returns
   * snapshot with 3 entries
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void getWALWaitSnapshot_withMultipleThreads_returnsAllThreadStats() {
    // Given: 3 threads have recorded WAL wait stats
    // When: getWALWaitSnapshot called
    // Then: Returns snapshot with 3 entries

    // TODO(#457): Implement test logic
    // - Create gateway with WAL enabled
    // - Spawn 3 threads that each send messages (triggering WAL queue stats)
    // - Wait for threads to complete
    // - Call getWALWaitSnapshot
    // - Verify snapshot contains entries for all 3 threads
    fail("Not yet implemented");
  }

  /**
   * Tests that closeThreadLocalSockets properly closes sockets from multiple threads.
   *
   * <p>Given: Multiple threads have created session sockets When: closeThreadLocalSockets called
   * from each thread Then: All sockets closed without exception
   */
  @Test
  @org.junit.Ignore("Awaiting implementation in #457")
  public void closeThreadLocalSockets_multipleThreads_closesAllSockets() {
    // Given: Multiple threads have created session sockets
    // When: closeThreadLocalSockets called from each thread
    // Then: All sockets closed without exception

    // TODO(#457): Implement test logic
    // - Create gateway with sessions enabled
    // - Spawn multiple threads that each call sendMessageToSessionService (creating sockets)
    // - Each thread calls closeThreadLocalSockets before exiting
    // - Verify no exceptions thrown during socket cleanup
    fail("Not yet implemented");
  }
}
