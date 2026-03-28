/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.zeromq;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * Unit test demonstrating that ZeroMQ REQ-ROUTER pattern does NOT support fire-and-forget
 * messaging.
 *
 * <p>This test demonstrates why REQ sockets cannot be used for async callbacks. REQ sockets have
 * strict request-reply semantics: after sending a message, the socket enters a "waiting for reply"
 * state and will reject attempts to send another message until a reply is received.
 *
 * <p>This is why the async callback mechanism uses DEALER-ROUTER instead of REQ-ROUTER. DEALER
 * sockets do not have this restriction and support fire-and-forget messaging.
 */
public class ReqRouterBlockingTest {

  /** ZeroMQ context for managing sockets. */
  private ZContext context;

  /** ROUTER socket (receiver side). */
  private ZMQ.Socket routerSocket;

  /** REQ socket (sender side). */
  private ZMQ.Socket reqSocket;

  /** Test address for socket binding and connection. */
  private static final String TEST_ADDRESS = "tcp://127.0.0.1:7998";

  /** Sets up ZeroMQ sockets before each test. */
  @Before
  public void setUp() throws InterruptedException {
    context = new ZContext();

    // Create ROUTER socket (receiver)
    routerSocket = context.createSocket(SocketType.ROUTER);
    routerSocket.bind(TEST_ADDRESS);

    // Create REQ socket (sender)
    reqSocket = context.createSocket(SocketType.REQ);
    reqSocket.connect(TEST_ADDRESS);

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
   * Tests that REQ socket CAN send one message and receive reply (normal REQ-REP flow).
   *
   * <p>This demonstrates the normal REQ-REP pattern works fine for request-reply scenarios.
   */
  @Test
  public void testReqCanSendOneMessageAndReceiveReply() throws Exception {
    String message = "Request message";

    // Send message from REQ socket
    boolean sent = reqSocket.send(message.getBytes(ZMQ.CHARSET), 0);
    assertThat("Message should be sent successfully", sent, is(true));

    // Receive on ROUTER socket
    // REQ sockets send: identity + empty delimiter + message
    byte[] identity = routerSocket.recv(0);
    assertThat("Identity frame should not be null", identity, is(notNullValue()));

    byte[] delimiter = routerSocket.recv(0);
    assertThat("Delimiter frame should not be null", delimiter, is(notNullValue()));

    byte[] messageBytes = routerSocket.recv(0);
    assertThat("Message frame should not be null", messageBytes, is(notNullValue()));

    String receivedMessage = new String(messageBytes, ZMQ.CHARSET);
    assertThat("Received message should match sent message", receivedMessage, is(message));

    // Send reply from ROUTER (must include delimiter frame)
    routerSocket.send(identity, ZMQ.SNDMORE);
    routerSocket.send(new byte[0], ZMQ.SNDMORE); // empty delimiter
    routerSocket.send("Reply".getBytes(ZMQ.CHARSET), 0);

    // Receive reply on REQ socket
    byte[] replyBytes = reqSocket.recv(0);
    assertThat("Reply should not be null", replyBytes, is(notNullValue()));

    String reply = new String(replyBytes, ZMQ.CHARSET);
    assertThat("Reply should match", reply, is("Reply"));
  }

  /**
   * Tests that REQ socket CANNOT send a second message without first receiving a reply.
   *
   * <p>This demonstrates why REQ-ROUTER cannot be used for fire-and-forget async callbacks. After
   * sending one message, the REQ socket enters a "waiting for reply" state. Attempting to send
   * another message without receiving a reply first will throw an exception (ZMQException:
   * Operation cannot be accomplished in current state).
   */
  @Test(expected = ZMQException.class)
  public void testReqCannotSendSecondMessageWithoutReply() throws Exception {
    // Send first message from REQ socket
    boolean firstSent = reqSocket.send("Message 1".getBytes(ZMQ.CHARSET), 0);
    assertThat("First message should be sent successfully", firstSent, is(true));

    // Attempt to send second message immediately (without receiving reply)
    // This should throw ZMQException because REQ socket is in "waiting for reply" state
    reqSocket.send("Message 2".getBytes(ZMQ.CHARSET), ZMQ.DONTWAIT);
  }
}
