/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    final int port = findOpenPort();

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
}
