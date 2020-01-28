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

package net.ittera.pal.core;

import com.google.common.util.concurrent.AbstractService;
import java.io.Closeable;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * A base class for our guava-managed services (previously extending AbstractExecutionThreadService)
 */
public abstract class ConnectedService extends AbstractService {

  private static final Logger logger = LoggerFactory.getLogger(ConnectedService.class);
  private final String syncSocketAddress;
  private final Thread runThread;
  private static final String INFO_PREFIX = "<SERVICE-INFO>";

  protected volatile boolean shutdownRequested = false;
  protected final ZContext zmqContext;
  protected final UUID peerUuid;
  protected final ThreadGroup threadGroup;
  protected final String serviceName;

  protected ConnectedService(
      UUID peerUuid,
      ZContext zmqContext,
      String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName) {
    this.peerUuid = peerUuid;
    this.zmqContext = zmqContext;
    this.syncSocketAddress = syncSocketAddress;
    this.threadGroup = serviceThreadGroup;
    this.serviceName = serviceName;
    this.runThread = createRunThread();
  }

  @Override
  protected final void doStart() {
    logger.info("{} {}: starting", INFO_PREFIX, serviceName);
    runThread.start();
  }

  private void startAndRun() {
    openConnections();
    logger.info("{} {}: connections open", INFO_PREFIX, serviceName);
    notifyStarted();
    signalReady();
    logger.info("{} {}: started, now running", INFO_PREFIX, serviceName);
    run();
    logger.info("{} {}: finished running", INFO_PREFIX, serviceName);
    closeConnections();
    logger.info("{} {}: connections closed", INFO_PREFIX, serviceName);
    notifyStopped();
    logger.info("{} {}: stopped", INFO_PREFIX, serviceName);
  }

  private void signalReady() {
    // signal Main that we're ready
    ZMQ.Socket sender = zmqContext.createSocket(SocketType.PUSH);
    sender.connect(syncSocketAddress);
    sender.send("go!");
    sender.close();
  }

  @Override
  protected final void doStop() {
    logger.info("{} {}: stopping", INFO_PREFIX, serviceName);
    triggerStop();
  }

  protected abstract void run();

  protected abstract void openConnections();

  protected abstract void closeConnections();

  protected final void closeConnection(@Nullable Closeable closeable, String msgForException) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.debug(msgForException, e);
      }
    }
  }

  protected void triggerStop() {
    shutdownRequested = true; // this should drive only the stopping of secondary threads
    runThread.interrupt();
  }

  private final Thread createRunThread() {
    Thread t = new Thread(threadGroup, this::startAndRun, serviceName);
    t.setUncaughtExceptionHandler(
        (thread, throwable) -> logger.error("Uncaught error in Service thread", throwable));
    return t;
  }
}
