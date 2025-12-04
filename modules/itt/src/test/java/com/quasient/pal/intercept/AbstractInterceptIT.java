/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.cxn.IncomingMessageListener;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.rpc.binary.ExecMessageAssertions;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for intercept integration tests.
 *
 * <p>This class provides common infrastructure for Intercept integration tests, including ThinPeer
 * setup, intercept registration, and callback handling.
 */
public class AbstractInterceptIT extends AbstractIntegrationTest
    implements ExecMessageAssertions, IncomingMessageListener {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final long INTERCEPT_REGISTRATION_MAX_DELAY_MS = 100;
  private static final String RPC_ADDRESS = "tcp://localhost:7890";

  /**
   * Well-known UUID for the shared interceptable peer.
   *
   * <p>This UUID is shared between both test suites ({@link
   * com.quasient.pal.InterceptEndToEndTestSuite} and {@link
   * com.quasient.pal.InterceptFlowTestSuite}) since they both target the same interceptable peer.
   */
  protected static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  /**
   * Well-known UUID for the interceptor peer (end-to-end tests only).
   *
   * <p>This UUID is used by {@link com.quasient.pal.InterceptEndToEndTestSuite} for the peer that
   * handles intercept callbacks. It must match the UUID defined in InterceptEndToEndTestSuite.
   */
  protected static final UUID INTERCEPTOR_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000003");

  protected MessageBuilder messageBuilder;
  protected final UUID myPeerUuid = UUID.randomUUID();
  protected PeerInfo interceptablePeerInfo;

  protected DirectoryConnectionProvider directoryConnectionProvider;
  private PalDirectory palDirectory;

  // a ThinPeer to invoke methods/field ops that will trigger callbacks
  private ThinPeer thinPeer;

  // list to accumulate received callback messages
  private final List<Message> receivedCallbacks = new ArrayList<>();

  // lock for synchronizing access to receivedCallbacks
  private final Object callbackLock = new Object();

  /**
   * Called when a callback message is received by the ThinPeer.
   *
   * <p>This method is invoked by the ThinPeer's listener thread when an intercept callback arrives.
   * The message is accumulated in the receivedCallbacks list for later retrieval via {@link
   * #getCallbacks(int, long)}.
   *
   * @param message the received callback message
   */
  @Override
  public void onMessageReceived(Message message) {
    synchronized (callbackLock) {
      logger.debug("Received callback message: {}", colferToPrettyJson(message));
      receivedCallbacks.add(message);
      callbackLock.notifyAll(); // wake up any threads waiting for callbacks
    }
  }

  /**
   * Waits for and retrieves the specified number of callback messages.
   *
   * <p>This method blocks until the expected number of callbacks are received or the timeout
   * expires. If the timeout is reached before all callbacks arrive, an AssertionError is thrown.
   *
   * @param expectedNum the number of callbacks expected
   * @param maxWaitMs the maximum time to wait in milliseconds
   * @return a list of received callback messages
   * @throws AssertionError if the expected number of callbacks are not received within the timeout
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  protected List<Message> getCallbacks(int expectedNum, long maxWaitMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + maxWaitMs;
    synchronized (callbackLock) {
      while (receivedCallbacks.size() < expectedNum) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
          throw new AssertionError(
              String.format(
                  "Timeout waiting for callbacks. Expected %d, received %d",
                  expectedNum, receivedCallbacks.size()));
        }
        callbackLock.wait(remaining);
      }
      // return and clear the callbacks
      List<Message> callbacks = new ArrayList<>(receivedCallbacks);
      receivedCallbacks.clear();
      return callbacks;
    }
  }

  @Before
  public void setUpAbstractInterceptIT() throws Exception {
    logger.info("===== AbstractInterceptIT.setUpAbstractInterceptIT: STARTING =====");

    directoryConnectionProvider = new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
    this.palDirectory =
        directoryConnectionProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));
    // Use the well-known shared peer UUID instead of searching for a peer
    // Retry a few times to allow for directory registration delay
    for (int i = 0; i < 10; i++) {
      interceptablePeerInfo = palDirectory.getPeer(INTERCEPTABLE_PEER_UUID);
      if (interceptablePeerInfo != null) {
        break;
      }
      logger.debug("Waiting for peer to register in directory (attempt {})", i + 1);
      Thread.sleep(500);
    }
    if (interceptablePeerInfo == null) {
      throw new RuntimeException(
          "Shared intercept test peer not found in directory after 5 seconds");
    }
    this.thinPeer =
        new ThinPeer()
            .withUuid(myPeerUuid)
            .withName("InterceptTestClient")
            .withSelfRegistration(true)
            .withZmqRpcAddress(RPC_ADDRESS)
            .withInitialPeer(interceptablePeerInfo)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();
    this.messageBuilder = new MessageBuilder(myPeerUuid);

    // register this test class as a listener for incoming callback messages
    thinPeer.addMessageListener(this);
    logger.info("===== AbstractInterceptIT.setUpAbstractInterceptIT: COMPLETED =====");
  }

  protected InterceptRequest<InterceptableMethodCall> createMethodCallInterceptRequest(
      UUID uuid,
      InterceptType type,
      String classname,
      String callbackClass,
      String callbackMethod,
      InterceptableMethodCall interceptableMethodCall) {
    return new InterceptRequest<>(
        uuid, myPeerUuid, type, classname, callbackClass, callbackMethod, interceptableMethodCall);
  }

  protected InterceptRequest<InterceptableFieldOp> createFieldOpInterceptRequest(
      UUID uuid,
      UUID callbackPeerUuid,
      InterceptType type,
      String classname,
      String callbackClass,
      String callbackMethod,
      InterceptableFieldOp interceptableFieldOp) {
    return new InterceptRequest<>(
        uuid,
        callbackPeerUuid,
        type,
        classname,
        callbackClass,
        callbackMethod,
        interceptableFieldOp);
  }

  protected final void register(InterceptRequest<?> interceptRequest) throws Exception {
    palDirectory.createIntercept(interceptRequest);
  }

  protected ExecMessage invoke(ExecMessage execMessage) {
    return thinPeer.sendToPeer(execMessage);
  }

  protected ExecMessage invoke(ExecMessage execMessage, ThinPeer withThinPeer) {
    return withThinPeer.sendToPeer(execMessage);
  }

  /**
   * Deletes all intercepts registered for the specified callback peer UUID.
   *
   * <p>This method is useful for end-to-end tests that register intercepts with
   * INTERCEPTOR_PEER_UUID instead of myPeerUuid.
   *
   * @param callbackPeerUuid the UUID of the callback peer whose intercepts should be deleted
   * @throws Exception if there's an error deleting the intercepts
   */
  protected void deleteInterceptsForCallbackPeer(UUID callbackPeerUuid) throws Exception {
    logger.info("Deleting intercepts for callback peer: {}", callbackPeerUuid);
    palDirectory.deleteInterceptsForPeer(callbackPeerUuid);
  }

  @After
  public void tearDownAbstractInterceptIT() throws Exception {
    logger.info("===== AbstractInterceptIT.tearDownAbstractInterceptIT: STARTING =====");

    // Clean up intercepts registered for this test's ThinPeer (mechanism tests)
    logger.info("Deleting intercepts for myPeerUuid: {}", myPeerUuid);
    palDirectory.deleteInterceptsForPeer(myPeerUuid);

    // Clean up intercepts registered for INTERCEPTOR_PEER_UUID (end-to-end tests)
    logger.info("Deleting intercepts for INTERCEPTOR_PEER_UUID: {}", INTERCEPTOR_PEER_UUID);
    palDirectory.deleteInterceptsForPeer(INTERCEPTOR_PEER_UUID);

    logger.info("Removing message listener from thinPeer");
    thinPeer.removeMessageListener(this);
    logger.info("Closing thinPeer");
    thinPeer.close();
    logger.info("Closing palDirectory");
    palDirectory.close();
    logger.info("===== AbstractInterceptIT.tearDownAbstractInterceptIT: COMPLETED =====");
  }
}
