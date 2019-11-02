package com.ittera.cometa.messages;

import com.google.common.primitives.Ints;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class MessageStreamerTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(1000);
    ctxt.setSndHWM(1000);
    return ctxt;
  }

  public static int findOpenPort() throws IOException {
    final ServerSocket tmpSocket = new ServerSocket(0, 0);
    try {
      return tmpSocket.getLocalPort();
    } finally {
      tmpSocket.close();
    }
  }

  private ExecutorService getExecutor(int nThreads) {
    return Executors.newFixedThreadPool(
        nThreads,
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
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    ExecutorService executor = getExecutor(2);
    List<InternalHeader> headers = new ArrayList<>();
    boolean done = false;
    CountDownLatch latch = new CountDownLatch(1);
    // start publisher, which simulates OutgoingMessageDispatcher
    Runnable publisher =
        () -> {
          ZMQ.Socket socket = context.createSocket(SocketType.PUB);
          socket.bind(address);
          int sentMessages = 0;
          ExecMessage msg = msgBuilder.buildEmptyConstructor(UUID.randomUUID(), "java.lang.String");
          while (latch.getCount() > 0) {
            // send headers
            socket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
            if (headers.size() > 0) {
              headers.forEach(h -> socket.send(h.toByteArray(), ZMQ.SNDMORE));
            }
            // send message
            socket.send(msg.toByteArray());
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
    executor.submit(publisher);

    // start streamer
    executor.submit(streamer);

    // stop
    executor.shutdown();
    executor.awaitTermination(2, TimeUnit.SECONDS);
    context.close();
    logger.debug("Stream received {} messages", messageStreamer.getReceivedMessagesCount());
  }
}
