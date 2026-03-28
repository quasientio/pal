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
package io.quasient.pal.core.bench;

import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisher;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import io.quasient.pal.messages.OutboundMsg;
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
