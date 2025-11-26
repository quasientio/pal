/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.zeromq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * Unit test verifying ZeroMQ DEALER-ROUTER pattern behavior for async message sending.
 *
 * <p>This test verifies the assumption that DEALER sockets can send messages to ROUTER sockets
 * without waiting for responses (fire-and-forget), which is the foundation of the async callback
 * mechanism used for BEFORE_ASYNC and AFTER_ASYNC intercepts.
 *
 * <p><b>Important:</b> REQ-ROUTER would NOT work for this pattern because REQ sockets have strict
 * request-reply semantics (must receive reply before sending next message). DEALER-ROUTER is used
 * because DEALER sockets support fire-and-forget messaging.
 */
public class DealerRouterAsyncTest {

  /** ZeroMQ context for managing sockets. */
  private ZContext context;

  /** ROUTER socket (receiver side). */
  private ZMQ.Socket routerSocket;

  /** DEALER socket (sender side). */
  private ZMQ.Socket dealerSocket;

  /** Test address for socket binding and connection. */
  private static final String TEST_ADDRESS = "tcp://127.0.0.1:7999";

  /** Sets up ZeroMQ sockets before each test. */
  @Before
  public void setUp() throws InterruptedException {
    context = new ZContext();

    // Create ROUTER socket (receiver)
    routerSocket = context.createSocket(SocketType.ROUTER);
    routerSocket.bind(TEST_ADDRESS);

    // Create DEALER socket (sender)
    dealerSocket = context.createSocket(SocketType.DEALER);
    dealerSocket.connect(TEST_ADDRESS);

    // Give sockets time to establish connection
    Thread.sleep(100);
  }

  /** Closes ZeroMQ sockets after each test. */
  @After
  public void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  /**
   * Tests that DEALER socket can send multiple messages to ROUTER without waiting for responses.
   *
   * <p>This verifies the async messaging pattern used for intercept callbacks (BEFORE_ASYNC and
   * AFTER_ASYNC). The test sends 3 messages in rapid succession without waiting for any responses,
   * then verifies all messages are received by the ROUTER socket.
   */
  @Test
  public void testDealerCanSendMultipleMessagesWithoutWaitingForResponse() throws Exception {
    final int messageCount = 3;
    List<String> sentMessages = new ArrayList<>();

    // Send multiple messages from DEALER socket without waiting for responses
    for (int i = 0; i < messageCount; i++) {
      String message = "Message " + i;
      sentMessages.add(message);

      boolean sent = dealerSocket.send(message.getBytes(ZMQ.CHARSET), 0);
      assertThat("Message " + i + " should be sent successfully", sent, is(true));
    }

    // Give messages time to arrive
    Thread.sleep(100);

    // Receive messages on ROUTER socket
    List<String> receivedMessages = new ArrayList<>();
    for (int i = 0; i < messageCount; i++) {
      // ROUTER receives identity frame first
      byte[] identity = routerSocket.recv(ZMQ.DONTWAIT);
      assertThat("Identity frame " + i + " should not be null", identity, is(notNullValue()));

      // Then the actual message
      byte[] messageBytes = routerSocket.recv(ZMQ.DONTWAIT);
      assertThat("Message frame " + i + " should not be null", messageBytes, is(notNullValue()));

      String message = new String(messageBytes, ZMQ.CHARSET);
      receivedMessages.add(message);
    }

    // Verify all messages were received
    assertThat("Should receive all sent messages", receivedMessages.size(), is(messageCount));
    for (int i = 0; i < messageCount; i++) {
      assertThat(
          "Received message " + i + " should match sent message",
          receivedMessages.get(i),
          is(sentMessages.get(i)));
    }
  }
}
