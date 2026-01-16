/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.quasient.pal.core.dispatcher.thread.ThreadPool;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * Executor responsible for handling Log messages using a dedicated thread pool.
 *
 * <p>This class is instantiated as a singleton and integrates with dependency injection to
 * configure and initialize a thread pool dedicated for processing Log messages. It leverages a
 * custom thread factory for thread creation and management.
 */
@Singleton
public class LogRpcExecutor extends ThreadPool {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(LogRpcExecutor.class);

  /**
   * Constructs a LogRpcExecutor with the necessary dependencies for Log message processing.
   *
   * <p>The thread pool size is parsed from the provided string parameter and a custom thread
   * factory is created to process Log messages.
   *
   * @param threadPoolSize the pool size specified as a string, which is parsed to determine the
   *     number of threads to be allocated.
   * @param zmqContext the ZeroMQ context used for managing socket connections.
   * @param logDealerAddress the network address for the Log dealer endpoint.
   * @param messageBuilder the utility for constructing messages.
   * @param incomingMessageDispatcher the dispatcher that handles routing of incoming Log messages.
   * @param outboundMessageGateway the gateway that handles message routing.
   * @param customClassloader the class loader for dynamically loading classes during execution.
   * @param peerUuid the unique identifier for the peer associated with this executor instance.
   */
  @Inject
  public LogRpcExecutor(
      @Named("log.threadPoolSize") String threadPoolSize,
      ZContext zmqContext,
      @Named("source.log") String logDealerAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      OutboundMessageGateway outboundMessageGateway,
      CustomClassloader customClassloader,
      UUID peerUuid) {

    super(
        Integer.parseInt(threadPoolSize),
        new LogRpcThreadFactory(
            zmqContext,
            logDealerAddress,
            messageBuilder,
            incomingMessageDispatcher,
            outboundMessageGateway,
            MessageChannelType.LOG_RPC,
            customClassloader,
            peerUuid));
  }
}
