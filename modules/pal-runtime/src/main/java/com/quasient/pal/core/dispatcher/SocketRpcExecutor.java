/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.quasient.pal.core.dispatcher.thread.ThreadPool;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.serdes.colfer.MessageBuilder;
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
