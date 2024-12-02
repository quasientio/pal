/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.messages;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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
                    MessageType.EXEC_MESSAGE,
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
