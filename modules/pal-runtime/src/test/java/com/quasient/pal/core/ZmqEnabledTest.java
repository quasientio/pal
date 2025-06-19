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

package com.quasient.pal.core;

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
