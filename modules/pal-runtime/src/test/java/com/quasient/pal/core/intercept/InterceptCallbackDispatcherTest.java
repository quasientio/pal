/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * Unit tests for {@link InterceptCallbackDispatcher}.
 *
 * <p>These tests use real ZeroMQ with inproc:// endpoints to test actual socket behavior, including
 * socket caching and REQ-REP patterns. The directory is mocked to control peer addresses.
 */
@SuppressWarnings("DoNotMock")
public class InterceptCallbackDispatcherTest extends ZmqEnabledTest {

  private ZContext context;
  private ExecutorService executorService;
  private UUID peerUuid;
  private MessageBuilder messageBuilder;
  private PalDirectory directory;
  private InterceptCallbackDispatcher dispatcher;

  @Before
  public void setUp() {
    context = createContext();
    executorService = Executors.newCachedThreadPool();
    peerUuid = UUID.randomUUID();
    messageBuilder = new MessageBuilder();

    // Mock directory
    directory = mock(PalDirectory.class);
    DirectoryConnectionProvider directoryProvider = mock(DirectoryConnectionProvider.class);
    when(directoryProvider.get()).thenReturn(Optional.of(directory));

    dispatcher =
        new InterceptCallbackDispatcher(peerUuid, context, messageBuilder, directoryProvider);
  }

  @After
  public void tearDown() throws Exception {
    if (dispatcher != null) {
      dispatcher.cleanup();
    }
    executorService.shutdownNow();
    executorService.awaitTermination(3, TimeUnit.SECONDS);
    closeContext(context);
  }

  @Test
  public void sendBeforeCallbacks_noRemoteIntercepts_doesNothing() throws Exception {
    // Create result with no remote intercepts
    InterceptCheckResult result =
        new InterceptCheckResult(Collections.emptyList(), Collections.emptyList());
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Should complete without error and not interact with directory
    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);

    // Verify no directory lookups occurred
    verify(directory, times(0)).getPeer(ArgumentMatchers.any());
  }

  @Test
  public void sendBeforeCallbacks_syncIntercept_sendsAndReceivesResponse() throws Exception {
    // Setup: Create stub server that echoes responses
    String endpoint = "inproc://sync-callback-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messageReceived = new CountDownLatch(1);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messageReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory to return our test endpoint
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);
    when(directory.peerExists(remotePeerUuid)).thenReturn(true);

    // Create intercept result with sync BEFORE intercept
    InterceptMessage interceptMsg = new InterceptMessage();
    interceptMsg.peerUuid = remotePeerUuid.toString();
    interceptMsg.interceptType = InterceptType.BEFORE.toByte();
    interceptMsg.callbackClass = "com.example.Test";
    interceptMsg.callbackMethod = "callback";

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg), Collections.emptyList());

    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Execute
    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);

    // Verify server received the callback
    boolean received = messageReceived.await(2, TimeUnit.SECONDS);
    assertThat("Server should have received callback", received, is(true));
    assertThat(server.receivedMessages.size(), is(1));

    server.requestStop();
  }

  @Test
  public void sendBeforeCallbacks_asyncIntercept_sendsWithoutBlocking() throws Exception {
    // Setup: Create stub server
    String endpoint = "inproc://async-callback-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messageReceived = new CountDownLatch(1);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messageReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);

    // Create intercept result with async BEFORE intercept
    InterceptMessage interceptMsg = new InterceptMessage();
    interceptMsg.peerUuid = remotePeerUuid.toString();
    interceptMsg.interceptType = InterceptType.BEFORE_ASYNC.toByte();
    interceptMsg.callbackClass = "com.example.Test";
    interceptMsg.callbackMethod = "callback";

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg), Collections.emptyList());

    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Execute - should return quickly without waiting
    long startTime = System.currentTimeMillis();
    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);
    long elapsed = System.currentTimeMillis() - startTime;

    // Should complete quickly (not wait for response)
    assertThat("Async callback should return quickly", elapsed < 100, is(true));

    // DEALER socket allows fire-and-forget; response is sent by server but caller doesn't wait
    // The socket can be safely reused for subsequent async callbacks

    server.requestStop();
  }

  @Test
  public void sendBeforeCallbacks_multipleAsyncToSamePeer_reusesDealerSocket() throws Exception {
    // Setup: Create stub server
    String endpoint = "inproc://multi-async-callback-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messagesReceived = new CountDownLatch(2);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messagesReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);

    // Create two async BEFORE intercepts to the same peer
    InterceptMessage interceptMsg1 = new InterceptMessage();
    interceptMsg1.peerUuid = remotePeerUuid.toString();
    interceptMsg1.interceptType = InterceptType.BEFORE_ASYNC.toByte();
    interceptMsg1.callbackClass = "com.example.Test";
    interceptMsg1.callbackMethod = "callback1";

    InterceptMessage interceptMsg2 = new InterceptMessage();
    interceptMsg2.peerUuid = remotePeerUuid.toString();
    interceptMsg2.interceptType = InterceptType.BEFORE_ASYNC.toByte();
    interceptMsg2.callbackClass = "com.example.Test";
    interceptMsg2.callbackMethod = "callback2";

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg1, interceptMsg2), Collections.emptyList());

    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Execute - should send both callbacks quickly without waiting
    long startTime = System.currentTimeMillis();
    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);
    long elapsed = System.currentTimeMillis() - startTime;

    // Should complete quickly (not wait for responses)
    assertThat("Multiple async callbacks should return quickly", elapsed < 100, is(true));

    // Verify both messages were received by server
    boolean received = messagesReceived.await(2, TimeUnit.SECONDS);
    assertThat("Server should receive both async callbacks", received, is(true));
    assertThat(server.receivedMessages.size(), is(2));

    // Verify directory.getPeer() was called only once (DEALER socket cached and reused)
    verify(directory, times(1)).getPeer(remotePeerUuid);

    server.requestStop();
  }

  @Test
  public void sendBeforeCallbacks_socketCaching_reusesSameSocket() throws Exception {
    // Setup: Create stub server
    String endpoint = "inproc://socket-caching-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messagesReceived = new CountDownLatch(2);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messagesReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);
    when(directory.peerExists(remotePeerUuid)).thenReturn(true);

    // Create intercept for sync callback
    InterceptMessage interceptMsg = new InterceptMessage();
    interceptMsg.peerUuid = remotePeerUuid.toString();
    interceptMsg.interceptType = InterceptType.BEFORE.toByte();
    interceptMsg.callbackClass = "com.example.Test";
    interceptMsg.callbackMethod = "callback";

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg), Collections.emptyList());

    ExecMessage execMessage1 = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ExecMessage execMessage2 = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.Integer");

    // Send two callbacks to same peer
    dispatcher.sendBeforeCallbacks(result, execMessage1, new Object[0]);
    dispatcher.sendBeforeCallbacks(result, execMessage2, new Object[0]);

    // Verify both received
    boolean received = messagesReceived.await(2, TimeUnit.SECONDS);
    assertThat("Server should receive both messages", received, is(true));
    assertThat(server.receivedMessages.size(), is(2));

    // Verify directory.getPeer() was called only once (socket cached)
    verify(directory, times(1)).getPeer(remotePeerUuid);

    server.requestStop();
  }

  @Test
  public void sendBeforeCallbacks_multiplePeers_createsMultipleSockets() throws Exception {
    // Setup: Create two stub servers
    String endpoint1 = "inproc://multi-peer-test-1";
    String endpoint2 = "inproc://multi-peer-test-2";
    UUID remotePeer1 = UUID.randomUUID();
    UUID remotePeer2 = UUID.randomUUID();

    CountDownLatch server1Ready = new CountDownLatch(1);
    CountDownLatch server2Ready = new CountDownLatch(1);
    CountDownLatch server1Received = new CountDownLatch(1);
    CountDownLatch server2Received = new CountDownLatch(1);

    StubCallbackServer server1 =
        new StubCallbackServer(context, endpoint1, server1Ready, server1Received);
    StubCallbackServer server2 =
        new StubCallbackServer(context, endpoint2, server2Ready, server2Received);

    executorService.execute(server1);
    executorService.execute(server2);
    server1Ready.await(1, TimeUnit.SECONDS);
    server2Ready.await(1, TimeUnit.SECONDS);

    // Mock directory for both peers
    PeerInfo peerInfo1 = new PeerInfo(remotePeer1);
    peerInfo1.setZmqRpcAddress(endpoint1);
    PeerInfo peerInfo2 = new PeerInfo(remotePeer2);
    peerInfo2.setZmqRpcAddress(endpoint2);
    when(directory.getPeer(remotePeer1)).thenReturn(peerInfo1);
    when(directory.getPeer(remotePeer2)).thenReturn(peerInfo2);
    when(directory.peerExists(remotePeer1)).thenReturn(true);
    when(directory.peerExists(remotePeer2)).thenReturn(true);

    // Create intercepts for both peers
    InterceptMessage intercept1 = new InterceptMessage();
    intercept1.peerUuid = remotePeer1.toString();
    intercept1.interceptType = InterceptType.BEFORE.toByte();

    InterceptMessage intercept2 = new InterceptMessage();
    intercept2.peerUuid = remotePeer2.toString();
    intercept2.interceptType = InterceptType.BEFORE.toByte();

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(intercept1, intercept2), Collections.emptyList());

    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Send callbacks to both peers
    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);

    // Verify both servers received
    assertThat(server1Received.await(2, TimeUnit.SECONDS), is(true));
    assertThat(server2Received.await(2, TimeUnit.SECONDS), is(true));
    assertThat(server1.receivedMessages.size(), is(1));
    assertThat(server2.receivedMessages.size(), is(1));

    server1.requestStop();
    server2.requestStop();
  }

  @Test
  public void sendAfterCallbacks_afterIntercept_sendsWithAfterType() throws Exception {
    // Setup stub server
    String endpoint = "inproc://after-intercept-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messageReceived = new CountDownLatch(1);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messageReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);
    when(directory.peerExists(remotePeerUuid)).thenReturn(true);

    // Create AFTER intercept
    InterceptMessage interceptMsg = new InterceptMessage();
    interceptMsg.peerUuid = remotePeerUuid.toString();
    interceptMsg.interceptType = InterceptType.AFTER.toByte();
    interceptMsg.callbackClass = "com.example.Test";
    interceptMsg.callbackMethod = "callback";

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg), Collections.emptyList());

    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    // Execute - sendAfterCallbacks with no return value, no exception
    dispatcher.sendAfterCallbacks(result, execMessage, null, false, null);

    // Verify received
    boolean received = messageReceived.await(2, TimeUnit.SECONDS);
    assertThat(received, is(true));
    assertThat(server.receivedMessages.size(), is(1));

    server.requestStop();
  }

  @Test
  public void cleanup_closesThreadLocalSockets() throws Exception {
    // Setup stub server
    String endpoint = "inproc://cleanup-test";
    UUID remotePeerUuid = UUID.randomUUID();
    CountDownLatch serverReady = new CountDownLatch(1);
    CountDownLatch messageReceived = new CountDownLatch(1);
    StubCallbackServer server =
        new StubCallbackServer(context, endpoint, serverReady, messageReceived);
    executorService.execute(server);
    serverReady.await(1, TimeUnit.SECONDS);

    // Mock directory
    PeerInfo peerInfo = new PeerInfo(remotePeerUuid);
    peerInfo.setZmqRpcAddress(endpoint);
    when(directory.getPeer(remotePeerUuid)).thenReturn(peerInfo);
    when(directory.peerExists(remotePeerUuid)).thenReturn(true);

    // Send a callback to create a cached socket
    InterceptMessage interceptMsg = new InterceptMessage();
    interceptMsg.peerUuid = remotePeerUuid.toString();
    interceptMsg.interceptType = InterceptType.BEFORE.toByte();

    InterceptCheckResult result =
        new InterceptCheckResult(List.of(interceptMsg), Collections.emptyList());
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    dispatcher.sendBeforeCallbacks(result, execMessage, new Object[0]);
    messageReceived.await(1, TimeUnit.SECONDS);

    // Cleanup should close sockets without error
    dispatcher.cleanup();

    // After cleanup, sending another callback should create a new socket
    // (We can't easily verify socket closure, but we verify no errors occur)

    server.requestStop();
  }

  /**
   * Stub server that receives callback messages and sends replies.
   *
   * <p>Uses ROUTER socket to accept connections from both REQ clients (sync callbacks) and DEALER
   * clients (async callbacks). ROUTER sockets handle the envelope routing automatically.
   */
  private static class StubCallbackServer implements Runnable {
    private final ZContext context;
    private final String endpoint;
    private final CountDownLatch readyLatch;
    private final CountDownLatch receivedLatch;
    private final List<Message> receivedMessages = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = true;

    StubCallbackServer(
        ZContext context,
        String endpoint,
        CountDownLatch readyLatch,
        CountDownLatch receivedLatch) {
      this.context = context;
      this.endpoint = endpoint;
      this.readyLatch = readyLatch;
      this.receivedLatch = receivedLatch;
    }

    @Override
    public void run() {
      Socket router = context.createSocket(SocketType.ROUTER);
      router.bind(endpoint);
      router.setReceiveTimeOut(100); // 100ms timeout for polling
      readyLatch.countDown();

      while (running) {
        try {
          // ROUTER receives: [identity, empty delimiter, payload]
          // Use recv() without parameter to respect socket's configured timeout
          byte[] identity = router.recv();
          if (identity != null) {
            byte[] empty = router.recv();
            byte[] request = router.recv();

            // Parse and store received message
            Message msg = new Message();
            try {
              msg.unmarshal(request, 0);
              receivedMessages.add(msg);
              receivedLatch.countDown();
            } catch (Exception e) {
              // Ignore parse errors in test
            }

            // Send reply: [identity, empty delimiter, payload]
            // This maintains validity for REQ clients; DEALER clients can ignore it
            Message response = new Message();
            response.messageType = msg.messageType;
            byte[] responseBytes = new byte[response.marshalFit()];
            response.marshal(responseBytes, 0);

            router.sendMore(identity);
            router.sendMore(empty);
            router.send(responseBytes, 0);
          }
        } catch (ZMQException e) {
          // Expected during context/socket closure in teardown
          if (e.getErrorCode() == 4) {
            // EINTR - Interrupted function, happens during cleanup
            break;
          }
          throw e;
        }
      }

      router.close();
    }

    void requestStop() {
      running = false;
    }
  }
}
