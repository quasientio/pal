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
package io.quasient.pal.core.dispatcher;

import io.quasient.pal.core.dispatcher.thread.InvokerThreadFactory;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.UUID;
import org.zeromq.ZContext;

/**
 * Factory class for creating RPC threads that handle Log messages.
 *
 * <p>This implementation extends InvokerThreadFactory to provide specialized thread creation Log
 * message dispatch. It sets up threads with a designated Log dealer socket address.
 */
public class LogRpcThreadFactory extends InvokerThreadFactory {

  /**
   * The address of the Log dealer socket. This address is provided during construction and passed
   * to the Log message invoker threads.
   */
  private final String logDealerSocketAddress;

  /**
   * Constructs a new LogRpcThreadFactory with the necessary Log message dispatching.
   *
   * <p>Initializes the thread factory with a ZeroMQ context, Log dealer socket address, message
   * builder, incoming message dispatcher, message gateway, RPC channel type, class loader, and peer
   * UUID.
   *
   * @param zmqContext the ZeroMQ context used for managing communication sockets.
   * @param logDealerSocketAddress the Log dealer socket address.
   * @param messageBuilder the builder instance used for serializing Log messages.
   * @param incomingMessageDispatcher the dispatcher responsible for handling incoming Log messages.
   * @param outboundMessageGateway the gateway for routing messages.
   * @param messageChannelType the type of RPC channel used for message transmission.
   * @param classLoader the class loader used to resolve classes at runtime.
   * @param peerUuid the unique identifier of this peer.
   */
  public LogRpcThreadFactory(
      ZContext zmqContext,
      String logDealerSocketAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      OutboundMessageGateway outboundMessageGateway,
      MessageChannelType messageChannelType,
      ClassLoader classLoader,
      UUID peerUuid) {
    super.init(
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        outboundMessageGateway,
        messageChannelType,
        classLoader,
        peerUuid);
    this.logDealerSocketAddress = logDealerSocketAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a new instance of LogRpcInvoker. The new thread is assigned the provided thread name
   * and is initialized with the ZeroMQ context, message builder, and the Log dealer socket address
   * to enable processing of incoming Log messages.
   *
   * @param newThreadName the name to assign to the newly created thread.
   * @return a new instance of AbstractMessageInvokerThread for handling Log messages.
   */
  @Override
  protected AbstractMessageInvokerThread createInvokerThread(String newThreadName) {
    return new LogRpcInvoker(
        threadGroup,
        newThreadName,
        zmqContext,
        messageBuilder,
        logDealerSocketAddress,
        incomingMessageDispatcher,
        outboundMessageGateway,
        peerUuid);
  }
}
