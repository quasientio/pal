/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

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

  // ===== Edge Case Test Specifications (Issue #425) =====
  // These tests target error handling paths to improve coverage of MessageStreamer.

  /**
   * Tests that calling close() multiple times on a connected MessageStreamer does not throw an
   * exception.
   *
   * <p>This verifies that the close() method is idempotent and can be safely called multiple times
   * without causing errors or unexpected behavior.
   */
  @Test
  @Ignore("Awaiting implementation in #426")
  public void close_calledMultipleTimes_noException() {
    // Given: A connected MessageStreamer
    // When: close() is called twice
    // Then: No exception is thrown

    // TODO(#426): Implement after #426 provides the implementation
    // Implementation should:
    // 1. Create a MessageStreamer with valid host/port
    // 2. Call connect() to establish connection
    // 3. Call close() first time (should succeed)
    // 4. Call close() second time (should not throw)
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Tests that calling close() on an unconnected MessageStreamer does not throw an exception.
   *
   * <p>This verifies that close() is safe to call even when connect() has never been called,
   * treating it as a no-op rather than throwing a NullPointerException.
   */
  @Test
  @Ignore("Awaiting implementation in #426")
  public void close_calledBeforeConnect_noException() {
    // Given: An unconnected MessageStreamer (connect() never called)
    // When: close() is called
    // Then: No exception is thrown; it should be a safe no-op

    // TODO(#426): Implement after #426 provides the implementation
    // Implementation should:
    // 1. Create a MessageStreamer with valid host/port
    // 2. Do NOT call connect()
    // 3. Call close() directly
    // 4. Verify no exception is thrown (may need null checks in close())
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Tests that getReceivedMessagesCount() returns zero when no messages have been received.
   *
   * <p>This verifies the initial state of the message counter after connection is established but
   * before any messages flow through the stream.
   */
  @Test
  @Ignore("Awaiting implementation in #426")
  public void getReceivedMessagesCount_noMessages_zero() {
    // Given: A connected MessageStreamer with no messages received
    // When: getReceivedMessagesCount() is called
    // Then: Returns 0

    // TODO(#426): Implement after #426 provides the implementation
    // Implementation should:
    // 1. Create a MessageStreamer with valid host/port
    // 2. Call connect() to establish connection
    // 3. Immediately call getReceivedMessagesCount() without consuming any messages
    // 4. Assert that the count is 0
    // 5. Clean up with close()
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Tests that attempting to connect with an invalid port throws an appropriate exception.
   *
   * <p>This verifies that the connect() method properly validates the port number and fails
   * gracefully when given an invalid value like -1.
   */
  @Test
  @Ignore("Awaiting implementation in #426")
  public void connect_invalidPort_throwsException() {
    // Given: A MessageStreamer configured with invalid port (-1)
    // When: connect() is called
    // Then: An appropriate exception is thrown (likely ZMQException or IllegalArgumentException)

    // TODO(#426): Implement after #426 provides the implementation
    // Implementation should:
    // 1. Create a MessageStreamer with host="localhost" and port=-1
    // 2. Attempt to call connect()
    // 3. Expect an exception to be thrown
    // 4. Optionally verify the exception type/message
    org.junit.Assert.fail("Not yet implemented");
  }

  /**
   * Tests that getStream() behaves correctly after the streamer has been closed.
   *
   * <p>This verifies the behavior when attempting to consume messages from a stream after the
   * underlying connection has been terminated. The stream should either terminate gracefully or
   * throw an appropriate exception.
   */
  @Test
  @Ignore("Awaiting implementation in #426")
  public void getStream_afterClose_behavesCorrectly() {
    // Given: A MessageStreamer that was connected and then closed
    // When: getStream() is called and an attempt is made to consume from it
    // Then: The stream terminates gracefully or throws an appropriate exception

    // TODO(#426): Implement after #426 provides the implementation
    // Implementation should:
    // 1. Create a MessageStreamer with valid host/port
    // 2. Call connect() to establish connection
    // 3. Call close() to terminate the connection
    // 4. Call getStream() and attempt to read (e.g., findFirst() or limit(1))
    // 5. Verify behavior: either null elements, empty stream, or appropriate exception
    org.junit.Assert.fail("Not yet implemented");
  }
}
