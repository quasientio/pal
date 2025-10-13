/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

import com.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import com.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import com.quasient.pal.messages.OutboundMsg;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import org.jctools.queues.MessagePassingQueue;
import org.zeromq.ZContext;

/**
 * Same queues, same batching, same network thread – but the final ZMQ send() is replaced by a
 * no-op, so no system calls occur.
 */
public final class DummyMessagePublisher extends MessagePublisher {

  /**
   * Constructs a new Dummy MessagePublisher instance that sets up the messaging endpoints.
   *
   * @param peerUuid Unique identifier representing this peer.
   * @param context ZeroMQ context used for creating and managing sockets.
   * @param syncSocketAddress Address of the synchronization socket for service readiness.
   * @param serviceThreadGroup Thread group under which the service runs.
   * @param serviceName Unique name identifying this service instance.
   * @param pubQueue initialized {@link OutboundMsg} queue instance from which to consume
   * @param config instance of {@link MessagePublisherConfig} with the runtime configuration.
   */
  @Inject
  public DummyMessagePublisher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("MessagePublisher.service") String serviceName,
      @Named("pub_queue") HwmMessageQueue<OutboundMsg> pubQueue,
      MessagePublisherConfig config) {

    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName, pubQueue, config);
  }

  /** Don’t create a real socket; just start the network thread. */
  @Override
  protected void openConnections() {
    // no socket, but start the same network thread
    networkThread = new Thread(this::networkLoop, serviceName + "-net");
    networkThread.setDaemon(false);
    networkThread.start();
  }

  /** No socket to close. Stats snapshot is still handled in super.closeConnections(). */
  @Override
  protected void closeConnections() {
    super.closeConnections(); // handles interrupt, stats, SPSC clear
  }

  /* ─────────────── Override the actual send ─────────────── */

  @Override
  protected void flushBurst(OutboundMsg[] batch, int size) {
    // Same accounting as the original, minus the ZMQ send
    messagesPublished += size; // pretend all sends succeeded
    for (int i = 0; i < size; i++) {
      batch[i] = null; // help GC
    }
  }

  /** Drain pubQueue just like the real one, but drop straight away. */
  @Override
  public void run() {
    final MessagePassingQueue.WaitStrategy NO_WAIT = idle -> idle;
    pubQueue.drain(
        this::forwardToNetworkThread, // use super’s method
        NO_WAIT,
        () -> !(Thread.currentThread().isInterrupted() || shutdownRequested));
  }
}
