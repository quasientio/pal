/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.events.InterceptEvent;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InterceptEventMsg;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

public class InterceptInformerTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private ExecutorService execService;
  private InterceptInformer interceptInformer;
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private DirectoryConnectionProvider directoryConnectionProvider;
  private PalDirectory palDirectory;
  private List<InterceptMessage> interceptRequestMessages;
  private List<String> requestsToUnregister;
  private static final String INTERCEPT_REG_ADDRESS = "inproc://intercepts.reg";

  private class InterceptsStub implements Runnable {
    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(INTERCEPT_REG_ADDRESS);
      while (!Thread.interrupted()) {
        try {
          InterceptEventMsg interceptEventMsg = InterceptEventMsg.receive(repSocket, true);
          assert interceptEventMsg != null;
          if (interceptEventMsg.getType().equals(InterceptEventMsg.Type.REGISTER)) {
            InterceptMessage interceptMessage = new InterceptMessage();
            interceptMessage.unmarshal(interceptEventMsg.getBody(), 0);
            interceptRequestMessages.add(interceptMessage);
          } else { // Type.UNREGISTER
            requestsToUnregister.add(interceptEventMsg.getInterceptMessageId());
          }
          repSocket.send("0");
        } catch (ZMQException e) {
          break;
        } catch (Exception e) {
          logger.debug("Exception caught in receive loop", e);
          break;
        }
      }
    }
  }

  @Before
  public void setup() {
    context = createContext();
    execService = Executors.newCachedThreadPool();
    interceptRequestMessages = new ArrayList<>();
    requestsToUnregister = new ArrayList<>();
    palDirectory = mock(PalDirectory.class);
    directoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(directoryConnectionProvider.get()).thenReturn(Optional.of(palDirectory));
  }

  @After
  public void cleanup() throws Exception {
    interceptRequestMessages.clear();
    requestsToUnregister.clear();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    Mockito.reset(palDirectory, directoryConnectionProvider);
  }

  @Test
  public void interceptRequestFromRemotePeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));
  }

  @Test
  public void unregisterRequestFromRemotePeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));

    // now unregister the request
    interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_REMOVED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            null);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that unregister messages were sent
    assertThat(requestsToUnregister.size(), is(1));
  }

  /**
   * Tests that intercepts targeting this peer (local intercepts) ARE registered.
   *
   * <p>Previously, intercepts where the interceptable peer == this peer were ignored as
   * "self-produced". This was changed to support local intercepts where the callback peer == this
   * peer. Now all intercepts are registered regardless of the target peer.
   */
  @Test
  public void interceptRequestFromThisPeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid, // this peer (self) - local intercept
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            peerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept message WAS sent (local intercepts are now registered)
    assertThat(interceptRequestMessages.size(), is(1));
  }

  // ========== registerAllInterceptsInDirectory Tests ==========

  /**
   * Tests that registerAllInterceptsInDirectory retrieves intercepts from all peers and sends them.
   */
  @Test
  public void registerAllInterceptsInDirectory_sendsAllIntercepts() throws Exception {
    // Setup mock directory with multiple peers and intercepts
    UUID peer1Uuid = UUID.randomUUID();
    UUID peer2Uuid = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo();
    peer1.setUuid(peer1Uuid);
    PeerInfo peer2 = new PeerInfo();
    peer2.setUuid(peer2Uuid);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peer1);
    peers.add(peer2);

    InterceptRequest<?> intercept1 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer1Uuid,
            InterceptType.BEFORE,
            "com.example.Class1",
            "org.callback.Handler",
            "handle1",
            new InterceptableMethodCall("method1", null));

    InterceptRequest<?> intercept2 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer2Uuid,
            InterceptType.AFTER,
            "com.example.Class2",
            "org.callback.Handler",
            "handle2",
            new InterceptableMethodCall("method2", null));

    when(palDirectory.listPeers()).thenReturn(peers);
    when(palDirectory.listInterceptsForPeer(peer1Uuid)).thenReturn(Set.of(intercept1));
    when(palDirectory.listInterceptsForPeer(peer2Uuid)).thenReturn(Set.of(intercept2));

    // Start intercepts stub
    execService.execute(new InterceptsStub());

    // Create informer and call registerAllInterceptsInDirectory
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    interceptInformer.registerAllInterceptsInDirectory();

    // Give some time for async processing
    Thread.sleep(100);

    // Verify both intercepts were sent
    assertThat(interceptRequestMessages.size(), is(2));
  }

  /** Tests that registerAllInterceptsInDirectory handles empty peer list gracefully. */
  @Test
  public void registerAllInterceptsInDirectory_emptyPeerList_noMessages() throws Exception {
    when(palDirectory.listPeers()).thenReturn(new HashSet<>());

    // Start intercepts stub
    execService.execute(new InterceptsStub());

    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    interceptInformer.registerAllInterceptsInDirectory();

    // Give some time for async processing
    Thread.sleep(50);

    // Verify no intercepts were sent
    assertThat(interceptRequestMessages.size(), is(0));
  }

  /** Tests that registerAllInterceptsInDirectory continues processing even if one peer fails. */
  @Test
  public void registerAllInterceptsInDirectory_peerError_continuesProcessing() throws Exception {
    UUID peer1Uuid = UUID.randomUUID();
    UUID peer2Uuid = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo();
    peer1.setUuid(peer1Uuid);
    PeerInfo peer2 = new PeerInfo();
    peer2.setUuid(peer2Uuid);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peer1);
    peers.add(peer2);

    InterceptRequest<?> intercept2 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer2Uuid,
            InterceptType.BEFORE,
            "com.example.Class",
            "org.callback.Handler",
            "handle",
            new InterceptableMethodCall("method", null));

    when(palDirectory.listPeers()).thenReturn(peers);
    when(palDirectory.listInterceptsForPeer(peer1Uuid))
        .thenThrow(new RuntimeException("Peer 1 error"));
    when(palDirectory.listInterceptsForPeer(peer2Uuid)).thenReturn(Set.of(intercept2));

    // Start intercepts stub
    execService.execute(new InterceptsStub());

    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);
    interceptInformer.registerAllInterceptsInDirectory();

    // Give some time for async processing
    Thread.sleep(100);

    // Verify peer2's intercept was still sent despite peer1 error
    assertThat(interceptRequestMessages.size(), is(1));
  }

  /** Tests that registerAllInterceptsInDirectory handles directory error gracefully. */
  @Test
  public void registerAllInterceptsInDirectory_directoryError_logsAndReturns() throws Exception {
    when(palDirectory.listPeers()).thenThrow(new RuntimeException("Directory error"));

    // Start intercepts stub
    execService.execute(new InterceptsStub());

    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);

    // Should not throw, just log error and return
    interceptInformer.registerAllInterceptsInDirectory();

    // Give some time
    Thread.sleep(50);

    // Verify no intercepts were sent
    assertThat(interceptRequestMessages.size(), is(0));
  }

  // ========== closeThreadLocalSocket Tests ==========

  /** Tests that closeThreadLocalSocket can be called safely even when socket was never created. */
  @Test
  public void closeThreadLocalSocket_noSocketCreated_doesNothing() {
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);

    // Should not throw when no socket was created
    interceptInformer.closeThreadLocalSocket();
  }

  /** Tests that closeThreadLocalSocket properly closes the socket after use. */
  @Test
  public void closeThreadLocalSocket_afterUse_closesSocket() {
    // Start intercepts stub
    execService.execute(new InterceptsStub());

    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "com.example.Class",
            "org.callback.Handler",
            "handle",
            new InterceptableMethodCall("method", null));

    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);

    // Send an event to create the socket
    InterceptEvent event =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/peer/intercept",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            interceptRequest);
    interceptInformer.interceptEvent(event);

    // Now close the socket
    interceptInformer.closeThreadLocalSocket();

    // Verify message was sent before closing
    assertThat(interceptRequestMessages.size(), is(1));
  }

  // ========== interceptEvent Error Handling Tests ==========

  /**
   * Tests that interceptEvent throws NullPointerException when interceptRequest is null for
   * INTERCEPT_ADDED.
   */
  @Test(expected = NullPointerException.class)
  public void interceptEvent_addedWithNullRequest_throwsNpe() {
    execService.execute(new InterceptsStub());

    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, INTERCEPT_REG_ADDRESS);

    // Create event with null interceptRequest
    InterceptEvent event =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/peer/intercept",
            UUID.randomUUID(),
            UUID.randomUUID().toString(),
            null); // null interceptRequest

    interceptInformer.interceptEvent(event);
  }
}
