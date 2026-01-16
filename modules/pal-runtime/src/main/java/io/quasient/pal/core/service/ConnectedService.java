/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import com.google.common.util.concurrent.AbstractService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Closeable;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/**
 * An abstract base class for guava-managed services with ZeroMQ-based readiness signalling to
 * synchronize all services startup. More specifically, the {@link Main} class waits for all
 * services to send their ready signal before considering them to be up and start accepting
 * requests.
 *
 * <p>This class manages a dedicated thread for executing service logic and coordinates the service
 * startup and shutdown sequences. It provides abstract hooks for opening and closing connections as
 * well as running the core service logic, leaving the specific implementation details to
 * subclasses.
 *
 * <p>Subclasses should implement the {@link #run()}, {@link #openConnections()}, and {@link
 * #closeConnections()} methods to define the service-specific behavior.
 */
@SuppressFBWarnings(
    value = "MC_OVERRIDABLE_METHOD_CALL_IN_CONSTRUCTOR",
    justification =
        "Thread created with method reference; closeConnections only called when thread runs, not during construction")
public abstract class ConnectedService extends AbstractService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ConnectedService.class);

  /** The ZeroMQ socket address used for synchronizing startup with external components. */
  private final String syncSocketAddress;

  /** Dedicated thread for executing the service's main logic. */
  private final Thread runThread;

  /** Prefix used in log messages to indicate service informational events. */
  private static final String INFO_PREFIX = "<SERVICE-INFO>";

  /** Flag indicating a request to shut down the service execution. */
  protected volatile boolean shutdownRequested = false;

  /** ZeroMQ context used to create and manage sockets for service communication. */
  protected final ZContext zmqContext;

  /** Unique identifier for this peer. */
  protected final UUID peerUuid;

  /** Thread group associated with this service for proper thread management. */
  protected final ThreadGroup threadGroup;

  /** Human-readable name of the service used in logs and thread naming. */
  protected final String serviceName;

  /**
   * Constructs a new ConnectedService with the specified parameters.
   *
   * @param peerUuid unique identifier for the service peer
   * @param zmqContext ZeroMQ context for managing sockets
   * @param syncSocketAddress address of the synchronization socket for startup signaling
   * @param serviceThreadGroup thread group to which the service's run thread belongs
   * @param serviceName human-readable name of the service
   */
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

  /**
   * {@inheritDoc}
   *
   * <p>Initiates the service startup process by launching the dedicated run thread.
   */
  @Override
  protected final void doStart() {
    logger.info("{} {}: starting", INFO_PREFIX, serviceName);
    runThread.start();
  }

  /**
   * Executes the complete lifecycle of the service: establishing connections, signaling readiness,
   * running the main service logic, and closing connections upon termination.
   */
  private void startAndRun() {
    openConnections();
    logger.info("{} {}: connections open", INFO_PREFIX, serviceName);
    signalReady();
    notifyStarted();
    logger.info("{} {}: started, now running...", INFO_PREFIX, serviceName);
    run();
    logger.info("{} {}: finished run(), closing down...", INFO_PREFIX, serviceName);
    closeConnections();
    logger.info("{} {}: connections closed", INFO_PREFIX, serviceName);
    notifyStopped();
    logger.info("{} {}: stopped", INFO_PREFIX, serviceName);
  }

  /**
   * Signals external components that the service is ready for operation.
   *
   * <p>This is performed by creating a ZeroMQ PUSH socket, connecting to the configured
   * synchronization address, and sending a "go!" message to indicate readiness.
   */
  private void signalReady() {
    // signal Main that we're ready
    ZMQ.Socket senderSocket = zmqContext.createSocket(SocketType.PUSH);
    try (senderSocket) {
      senderSocket.connect(syncSocketAddress);
      senderSocket.send("go!");
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initiates a shutdown request for the service.
   */
  @Override
  protected final void doStop() {
    logger.info("{} {}: stopping", INFO_PREFIX, serviceName);
    triggerStop();
  }

  /**
   * Executes the primary service logic.
   *
   * <p>Implementations should continuously perform service-specific tasks and regularly check for a
   * shutdown request to allow graceful termination.
   */
  protected abstract void run();

  /**
   * Opens all necessary connections required for service operation.
   *
   * <p>Subclasses should establish connections to external resources or components required during
   * service execution.
   */
  protected abstract void openConnections();

  /**
   * Closes all open connections associated with the service.
   *
   * <p>Subclasses should release resources and disconnect from external components to ensure a
   * clean shutdown.
   */
  protected abstract void closeConnections();

  /**
   * Attempts to close the provided resource while logging any exceptions encountered.
   *
   * <p>If the resource is non-null, this method will invoke its close method. Any exceptions thrown
   * during the close operation are caught and logged at debug level using the supplied message.
   *
   * @param closeable the resource to be closed; if null, no action is taken
   * @param msgForException log message used to report any exception that occurs during closing
   */
  protected final void closeConnection(@Nullable Closeable closeable, String msgForException) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (Exception e) {
        logger.warn(msgForException, e);
      }
    }
  }

  /**
   * Triggers a shutdown sequence for the service.
   *
   * <p>This method sets a shutdown flag and interrupts the dedicated run thread, thereby signaling
   * the service to cease its operations.
   */
  protected void triggerStop() {
    shutdownRequested = true; // this should drive only the stopping of secondary threads
    runThread.interrupt();
  }

  /**
   * Creates and configures a dedicated thread for executing the service's runtime logic.
   *
   * <p>The thread is assigned to the specified thread group, named after the service, and provided
   * with an uncaught exception handler for logging unexpected errors.
   *
   * @return a fully configured thread ready to execute the service lifecycle
   */
  private Thread createRunThread() {
    Thread t = new Thread(threadGroup, this::startAndRun, serviceName);
    t.setUncaughtExceptionHandler(
        (thread, throwable) -> logger.error("Uncaught error in Service thread", throwable));
    return t;
  }
}
