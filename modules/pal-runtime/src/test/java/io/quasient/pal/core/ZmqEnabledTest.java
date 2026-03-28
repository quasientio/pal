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
