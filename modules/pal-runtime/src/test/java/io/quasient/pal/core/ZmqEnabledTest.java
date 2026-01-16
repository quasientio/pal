/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public abstract class ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final String SYNC_SOCKET_ADDRESS = "inproc://sync_ready";

  protected ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  protected void collectGoSignals(int numberOfSignals, ZContext context) {
    CountDownLatch latch = new CountDownLatch(numberOfSignals);
    Socket syncSocket = context.createSocket(SocketType.PULL);
    syncSocket.bind(SYNC_SOCKET_ADDRESS);
    while (latch.getCount() > 0) {
      String receivedString = syncSocket.recvStr();
      if (receivedString.equalsIgnoreCase("go!")) {
        logger.debug("go received");
        latch.countDown();
      }
    }
    logger.debug("all go signals received");
    syncSocket.close();
    logger.debug("syncSocket closed");
  }

  protected void closeContext(ZContext context) throws InterruptedException {
    ExecutorService execService = Executors.newCachedThreadPool();
    execService.execute(
        () -> {
          context.close();
          logger.debug("zmq context terminated");
        });

    // stop executor
    execService.shutdown();
    @SuppressWarnings("unused")
    boolean unusedResult = execService.awaitTermination(1, TimeUnit.SECONDS);
  }

  protected static int findAvailableServerPort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("No available server port found", e);
    }
  }
}
