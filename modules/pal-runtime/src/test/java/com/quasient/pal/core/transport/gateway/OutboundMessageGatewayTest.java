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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.intercept.InterceptMatcher;
import com.quasient.pal.core.internal.messages.SessionCommandMsg;
import com.quasient.pal.core.internal.messages.SessionResponseMsg;
import com.quasient.pal.core.service.RunOptions;
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
import org.jctools.queues.MessagePassingQueue;
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
  private InterceptMatcher matcher;

  private MessagePassingQueue<OutboundMsg> walQueueMock;
  private MessagePassingQueue<OutboundMsg> pubQueueMock;
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
    PalDirectory dir = mock(PalDirectory.class);
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

    // ── JCTools queue mocks
    walQueueMock = mock(MessagePassingQueue.class);
    pubQueueMock = mock(MessagePassingQueue.class);
    when(walQueueMock.offer(any())).thenReturn(true);
    when(pubQueueMock.offer(any())).thenReturn(true);

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
            walQueueMock,
            walFailed,
            pubQueueMock,
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
  public void sendMessagesToSessionService() {
    initGateway(false, false, false);

    SessionCommandMsg cmd1 =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("123"));
    SessionResponseMsg resp1 = gateway.sendMessageToSessionService(cmd1);

    SessionCommandMsg cmd2 =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("456"));
    SessionResponseMsg resp2 = gateway.sendMessageToSessionService(cmd2);

    // gateway got OK back
    assertThat(resp1.getStatus(), is(SessionStatusType.OK));
    assertThat(resp2.getStatus(), is(SessionStatusType.OK));

    // stub recorded the calls
    assertThat(sessionStub.messagesReceived, is(List.of(cmd1, cmd2)));
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

    verify(pubQueueMock, times(withPub ? 1 : 0)).offer(any());
    verify(walQueueMock, times(withWal ? 1 : 0)).offer(any());
    verifyNoMoreInteractions(pubQueueMock, walQueueMock);
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

    verify(pubQueueMock, times(withPub ? n : 0)).offer(any());
    verify(walQueueMock, times(withWal ? n : 0)).offer(any());
    verifyNoMoreInteractions(pubQueueMock, walQueueMock);
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
}
