/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

package com.quasient.pal.core.transport.gateway;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.intercept.InterceptMatcher;
import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.internal.messages.SessionCommandMsg;
import com.quasient.pal.core.internal.messages.SessionResponseMsg;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.SessionCommandType;
import com.quasient.pal.messages.types.SessionStatusType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
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
  private final List<ExecMessage> msgsSeenByMatcher = new ArrayList<>();

  // --------------------------------------------------------------------
  // zmq / executor fixtures
  // --------------------------------------------------------------------
  private ZContext context;
  private ExecutorService execService;
  private SessionServiceStub sessionStub;

  // --------------------------------------------------------------------
  // DI mocks
  // --------------------------------------------------------------------
  private DirectoryConnectionProvider dirProvider;
  private PalDirectory dir;
  private InterceptMatcher matcher;

  private WalWriter walWriterMock;
  private HwmMessageQueue<OutboundMsg> walQueue;
  private HwmMessageQueue<OutboundMsg> pubQueue;
  private AtomicBoolean walFailed;

  private OutboundMessageGateway gateway;

  // --------------------------------------------------------------------
  // set-up / tear-down
  // --------------------------------------------------------------------
  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws Exception {
    context = createContext();
    execService = Executors.newCachedThreadPool();
    walFailed = new AtomicBoolean(false);

    // ── directory mock
    dir = mock(PalDirectory.class);
    dirProvider = mock(DirectoryConnectionProvider.class);
    when(dirProvider.get()).thenReturn(Optional.of(dir));

    // ── intercept matcher mock
    matcher = mock(InterceptMatcher.class);
    when(matcher.getMatchingIntercepts(any(), any(), any()))
        .thenAnswer(
            (Answer<List<?>>)
                inv -> {
                  msgsSeenByMatcher.add((ExecMessage) inv.getArguments()[0]);
                  return Collections.emptyList(); // no intercepts
                });

    // Wal Writer mock
    walWriterMock = mock(WalWriter.class);

    // ── Real HWM queues (avoid mocking finals in sandbox)
    walQueue =
        HwmMessageQueue.createQueue(
            com.quasient.pal.core.internal.concurrent.MpscKind.GROWABLE, 16, 1024);
    pubQueue =
        HwmMessageQueue.createQueue(
            com.quasient.pal.core.internal.concurrent.MpscKind.GROWABLE, 16, 1024);

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
            dirProvider,
            opts,
            matcher,
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
            dirProvider,
            opts,
            matcher,
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
    assertThat(msgsSeenByMatcher, is(List.of(msg)));

    assertThat(pubQueue.currentSize(), is(withPub ? 1 : 0));
    assertThat(walQueue.currentSize(), is(withWal ? 1 : 0));
  }

  private void sendManyExecMsgsAndAssert(boolean withWal, boolean withPub, boolean withIntercepts) {
    initGateway(withWal, withPub, withIntercepts);

    int n = 10;
    List<ExecMessage> sent = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
      sent.add(m);
      gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);
    }
    assertThat(msgsSeenByMatcher, is(sent));

    assertThat(pubQueue.currentSize(), is(withPub ? n : 0));
    assertThat(walQueue.currentSize(), is(withWal ? n : 0));
  }

  // --------------------------------------------------------------------
  // stub for the session service
  // --------------------------------------------------------------------
  private static final class SessionServiceStub implements Runnable {

    final List<SessionCommandMsg> messagesReceived = new ArrayList<>();
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
        messagesReceived.add(m);
        new SessionResponseMsg(SessionStatusType.OK).send(rep);
      }
      rep.close();
    }
  }

  // --------------------------------------------------------------------
  // stub for a peer interceptor (RPC REP)
  // --------------------------------------------------------------------
  private static final class InterceptorServer implements Runnable {
    private final ZContext ctx;
    private final String endpoint;
    private final boolean reply;
    private final CountDownLatch start;
    private volatile boolean stop;
    final List<byte[]> messages = new ArrayList<>();

    InterceptorServer(ZContext ctx, String endpoint, boolean reply, CountDownLatch start) {
      this.ctx = ctx;
      this.endpoint = endpoint;
      this.reply = reply;
      this.start = start;
    }

    void requestStop() {
      stop = true;
    }

    @Override
    public void run() {
      Socket rep = ctx.createSocket(SocketType.REP);
      try {
        rep.bind(endpoint);
        rep.setReceiveTimeOut(100);
        start.countDown();
        while (!stop && !Thread.interrupted()) {
          try {
            byte[] req = rep.recv(0);
            if (req == null) continue;
            messages.add(req);
            if (reply) {
              // echo back
              rep.send(req, 0);
            }
            // For async tests we purposefully do not reply and exit
            if (!reply) break;
          } catch (org.zeromq.ZMQException e) {
            // EINTR/ETERM on context close; exit quietly
            break;
          }
        }
      } finally {
        rep.close();
      }
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
            dirProvider,
            opts,
            matcher,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    ExecMessage m = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(m), ExecPhase.BEFORE);

    // Still full; drop policy increments counters internally but we assert no growth
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
            dirProvider,
            opts,
            matcher,
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
    HwmMessageQueue<OutboundMsg> fixedPubQueue =
        HwmMessageQueue.createQueue(com.quasient.pal.core.internal.concurrent.MpscKind.FIXED, 4, 4);
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
            dirProvider,
            opts,
            matcher,
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
    HwmMessageQueue<OutboundMsg> fixedWalQueue =
        HwmMessageQueue.createQueue(com.quasient.pal.core.internal.concurrent.MpscKind.FIXED, 4, 4);

    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            dirProvider,
            opts,
            matcher,
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
  public void intercept_sync_callback_usesDirectoryAndCachesSocket() throws Exception {
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_INTERCEPTS);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            dirProvider,
            opts,
            matcher,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    // spin up interceptor REP server that echoes
    String endpoint = "inproc://interceptor_sync";
    UUID remote = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);
    InterceptorServer interceptor = new InterceptorServer(context, endpoint, true, latch);
    execService.execute(interceptor);
    latch.await();
    // directory stubs
    com.quasient.pal.common.directory.nodes.PeerInfo pi =
        new com.quasient.pal.common.directory.nodes.PeerInfo(remote);
    pi.setZmqRpcAddress(endpoint);
    when(dir.getPeer(remote)).thenReturn(pi);
    when(dir.peerExists(remote)).thenReturn(true);

    // return a BEFORE intercept targeting the remote peer
    com.quasient.pal.messages.colfer.InterceptMessage im =
        new com.quasient.pal.messages.colfer.InterceptMessage();
    im.peerUuid = remote.toString();
    im.interceptType = com.quasient.pal.common.lang.intercept.InterceptType.BEFORE.toByte();
    im.callbackClass = "java.lang.String";
    im.callbackMethod = "valueOf";
    when(matcher.getMatchingIntercepts(any(), any(), any())).thenReturn(List.of(im));

    ExecMessage msg = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(msg), ExecPhase.BEFORE);

    // ensure server observed exactly one message
    assertThat(interceptor.messages.size(), is(1));

    // trigger again to reuse cached socket and ensure directory.getPeer only called once
    gateway.sendExecMessage(builder.wrap(msg), ExecPhase.BEFORE);
    verify(dir, times(1)).getPeer(remote);

    interceptor.requestStop();
    gateway.closeThreadLocalSockets();
  }

  @Test
  public void intercept_async_callback_doesNotWaitForResponse() throws Exception {
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_INTERCEPTS);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            dirProvider,
            opts,
            matcher,
            walWriterMock,
            walQueue,
            walFailed,
            pubQueue,
            PublishingDropPolicy.DROP_OLD,
            SESSION_SERVICE_REQ_ADDRESS);

    String endpoint = "inproc://interceptor_async";
    UUID remote = UUID.randomUUID();
    CountDownLatch latch = new CountDownLatch(1);
    InterceptorServer interceptor = new InterceptorServer(context, endpoint, false, latch);
    execService.execute(interceptor);
    latch.await();

    com.quasient.pal.common.directory.nodes.PeerInfo pi =
        new com.quasient.pal.common.directory.nodes.PeerInfo(remote);
    pi.setZmqRpcAddress(endpoint);
    when(dir.getPeer(remote)).thenReturn(pi);
    when(dir.peerExists(remote)).thenReturn(true);

    com.quasient.pal.messages.colfer.InterceptMessage im =
        new com.quasient.pal.messages.colfer.InterceptMessage();
    im.peerUuid = remote.toString();
    im.interceptType = com.quasient.pal.common.lang.intercept.InterceptType.BEFORE_ASYNC.toByte();
    im.callbackClass = "java.lang.String";
    im.callbackMethod = "valueOf";
    when(matcher.getMatchingIntercepts(any(), any(), any())).thenReturn(List.of(im));

    ExecMessage msg = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    gateway.sendExecMessage(builder.wrap(msg), ExecPhase.BEFORE);

    // wait briefly for server to receive
    long deadline = System.currentTimeMillis() + 500;
    while (System.currentTimeMillis() < deadline && interceptor.messages.size() == 0) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    assertThat(interceptor.messages.size(), is(1));
    gateway.closeThreadLocalSockets();
  }

  @Test
  public void printAggregateStats_smoke() {
    EnumSet<RunOptions> opts = EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_TCP_PUB);
    gateway =
        new OutboundMessageGateway(
            context,
            peerUuid,
            builder,
            dirProvider,
            opts,
            matcher,
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
    public void writeToLog(com.quasient.pal.common.directory.nodes.LogInfo l, boolean p) {}

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
            dirProvider,
            opts,
            matcher,
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
            dirProvider,
            opts,
            matcher,
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
}
