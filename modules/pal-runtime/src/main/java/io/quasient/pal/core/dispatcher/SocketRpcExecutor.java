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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.quasient.pal.core.dispatcher.thread.ThreadPool;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * Manages the execution of RPC messages through a configurable thread pool.
 *
 * <p>This executor extends a generic thread pool to facilitate handling of RPC messages using both
 * zmq/bin-based and WS/JSON-RPC-based channels. It leverages dependency injection to configure its
 * environment, including message dispatchers and dynamic class loading support.
 */
@Singleton
public class SocketRpcExecutor extends ThreadPool {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(SocketRpcExecutor.class);

  /**
   * Constructs a new RPC message executor, setting up the thread pool and associated RPC
   * communication components.
   *
   * <p>The executor is configured with a specific thread pool size and uses a custom thread factory
   * for creating threads that manage socket-based RPC dispatching.
   *
   * @param threadPoolSize a string representing the number of threads in the pool; must be
   *     parseable as an integer.
   * @param zmqContext the ZeroMQ context used for establishing socket-based communications.
   * @param runOptions a set of runtime options that dictate various operational parameters for RPC
   *     execution.
   * @param rpcDealerAddress the address for the RPC dealer endpoint used in socket-based
   *     communication.
   * @param jsonrpcDealerAddress the address for the JSON RPC dealer endpoint.
   * @param messageBuilder a utility responsible for constructing RPC messages.
   * @param incomingMessageDispatcher the dispatcher that routes incoming RPC messages to their
   *     appropriate handlers.
   * @param outboundMessageGateway the gateway instance for message routing.
   * @param customClassloader the custom class loader employed for dynamic loading of RPC-invoked
   *     classes.
   * @param peerUuid a unique identifier for this RPC peer within the network.
   */
  @Inject
  public SocketRpcExecutor(
      @Named("rpc.threadPoolSize") String threadPoolSize,
      ZContext zmqContext,
      Set<RunOptions> runOptions,
      @Named("in.dealer") String rpcDealerAddress,
      @Named("json.in.dealer") String jsonrpcDealerAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      OutboundMessageGateway outboundMessageGateway,
      CustomClassloader customClassloader,
      UUID peerUuid) {

    super(
        Integer.parseInt(threadPoolSize),
        new SocketRpcThreadFactory(
            zmqContext,
            runOptions,
            rpcDealerAddress,
            jsonrpcDealerAddress,
            messageBuilder,
            incomingMessageDispatcher,
            outboundMessageGateway,
            MessageChannelType.ZMQ_SOCKET_RPC,
            customClassloader,
            peerUuid));
  }
}
