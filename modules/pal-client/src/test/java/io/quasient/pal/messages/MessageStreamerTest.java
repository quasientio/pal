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
package io.quasient.pal.messages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

public class MessageStreamerTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private static final int EXECUTOR_THREADS = 2;

  private static ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(1000);
    ctxt.setSndHWM(1000);
    return ctxt;
  }

  public static int findOpenPort() throws IOException {
    try (ServerSocket tmpSocket = new ServerSocket(0, 0)) {
      return tmpSocket.getLocalPort();
    }
  }

  private ExecutorService getExecutor() {
    return Executors.newFixedThreadPool(
        EXECUTOR_THREADS,
        r -> {
          Thread thread = new Thread(r);
          thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
          return thread;
        });
  }

  @Test
  public void stream() throws Exception {

    final String host = "localhost";
    final int port;
    try {
      port = findOpenPort();
    } catch (Exception e) {
      // Sandbox may forbid opening sockets; skip this network-dependent test
      Assume.assumeNoException("Skipping network-dependent test due to sandbox", e);
      return;
    }

    ZContext context = createContext();
    String address = String.format("tcp://%s:%d", host, port);
    logger.debug("Will use address: {}", address);
    MessageBuilder msgBuilder = new MessageBuilder();
    ExecutorService executor = getExecutor();
    CountDownLatch latch = new CountDownLatch(1);
    // start publisher, which simulates MessagePublisher
    Runnable publisher =
        () -> {
          ZMQ.Socket socket = context.createSocket(SocketType.PUB);
          socket.bind(address);
          int sentMessages = 0;
          ExecMessage msg = msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.lang.String");
          while (latch.getCount() > 0) {
            OutboundMsg outboundMsg =
                new OutboundMsg(
                    MessageType.EXEC_CONSTRUCTOR,
                    ExecPhase.BEFORE,
                    new ArrayList<>(),
                    UUID.randomUUID().toString(),
                    null,
                    msgBuilder.wrap(msg));
            outboundMsg.send(socket);
            sentMessages++;
          }
          logger.debug("Sent {} messages", sentMessages);
          socket.close();
        };

    final MessageStreamer messageStreamer = new MessageStreamer(host, port).connect();
    Runnable streamer =
        () -> {
          logger.debug("Stream connected, now reading...");
          messageStreamer
              .getStream()
              .limit(1)
              .forEach(m -> latch.countDown()); // signal that we got a message
        };

    // start publisher
    Future<?> publisherFuture = executor.submit(publisher);

    // start streamer
    Future<?> streamerFuture = executor.submit(streamer);

    // wait for the futures to complete to let any thrown exceptions propagate
    streamerFuture.get();
    publisherFuture.get();

    // stop
    executor.shutdown();

    @SuppressWarnings("unused")
    boolean result = executor.awaitTermination(2, TimeUnit.SECONDS);

    context.close();
    logger.debug("Stream received {} messages", messageStreamer.getReceivedMessagesCount());
  }

  // ===== Edge Case Tests =====
  // These tests target error handling paths to improve coverage of MessageStreamer.

  /**
   * Tests that calling close() multiple times on a connected MessageStreamer does not throw an
   * exception.
   *
   * <p>This verifies that the close() method is idempotent and can be safely called multiple times
   * without causing errors or unexpected behavior.
   */
  @Test
  public void close_calledMultipleTimes_noException() throws Exception {
    // Given: A connected MessageStreamer
    final int port;
    try {
      port = findOpenPort();
    } catch (Exception e) {
      Assume.assumeNoException("Skipping network-dependent test due to sandbox", e);
      return;
    }

    MessageStreamer streamer = new MessageStreamer("localhost", port);
    try {
      streamer.connect();

      // When: close() is called twice
      streamer.close();
      streamer.close();

      // Then: No exception is thrown (test passes if we reach this point)
    } finally {
      // Cleanup is already done via close() calls above
    }
  }

  /**
   * Tests that calling close() on an unconnected MessageStreamer does not throw an exception.
   *
   * <p>This verifies that close() is safe to call even when connect() has never been called,
   * treating it as a no-op rather than throwing a NullPointerException.
   */
  @Test
  public void close_calledBeforeConnect_noException() {
    // Given: An unconnected MessageStreamer (connect() never called)
    MessageStreamer streamer = new MessageStreamer("localhost", 5555);

    // When: close() is called
    // Then: No exception is thrown; it should be a safe no-op
    streamer.close();
  }

  /**
   * Tests that getReceivedMessagesCount() returns zero when no messages have been received.
   *
   * <p>This verifies the initial state of the message counter after connection is established but
   * before any messages flow through the stream.
   */
  @Test
  public void getReceivedMessagesCount_noMessages_zero() throws Exception {
    // Given: A connected MessageStreamer with no messages received
    final int port;
    try {
      port = findOpenPort();
    } catch (Exception e) {
      Assume.assumeNoException("Skipping network-dependent test due to sandbox", e);
      return;
    }

    MessageStreamer streamer = new MessageStreamer("localhost", port);
    try {
      streamer.connect();

      // When: getReceivedMessagesCount() is called
      long count = streamer.getReceivedMessagesCount();

      // Then: Returns 0
      assertEquals("Initial message count should be zero", 0L, count);
    } finally {
      streamer.close();
    }
  }

  /**
   * Tests that attempting to connect with an invalid port throws an appropriate exception.
   *
   * <p>This verifies that the connect() method properly validates the port number and fails
   * gracefully when given an invalid value like -1.
   */
  @Test
  public void connect_invalidPort_throwsException() {
    // Given: A MessageStreamer configured with invalid port (-1)
    MessageStreamer streamer = new MessageStreamer("localhost", -1);

    try {
      // When: connect() is called
      streamer.connect();

      // Then: An appropriate exception is thrown
      fail("Expected an exception for invalid port");
    } catch (IllegalArgumentException | ZMQException e) {
      // Expected - ZMQ should reject invalid port
      logger.debug("Got expected exception for invalid port: {}", e.getMessage());
    } finally {
      streamer.close();
    }
  }

  /**
   * Tests that getStream() behaves correctly after the streamer has been closed.
   *
   * <p>This verifies the behavior when attempting to consume messages from a stream after the
   * underlying connection has been terminated. The stream should either terminate gracefully or
   * throw an appropriate exception.
   */
  @Test
  public void getStream_afterClose_behavesCorrectly() throws Exception {
    // Given: A MessageStreamer that was connected and then closed
    final int port;
    try {
      port = findOpenPort();
    } catch (Exception e) {
      Assume.assumeNoException("Skipping network-dependent test due to sandbox", e);
      return;
    }

    MessageStreamer streamer = new MessageStreamer("localhost", port);
    streamer.connect();
    streamer.close();

    // When: getStream() is called and an attempt is made to consume from it
    // Then: The stream should return null elements (ZMQ exceptions are caught in getNext())
    Stream<Message> stream = streamer.getStream();

    // Use iterator to handle null values (findFirst() throws NPE on null)
    Iterator<Message> iterator = stream.limit(1).iterator();
    assertTrue("Stream should have an element", iterator.hasNext());
    Message message = iterator.next();

    // The stream should return null (getNext returns null when socket is closed/errors occur)
    assertNull("After close, stream element should be null", message);
  }
}
