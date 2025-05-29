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

package net.ittera.pal.core.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.UUID;
import net.ittera.pal.core.rpc.exec.java.CustomClassloader;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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
public class LogMessageExecutor extends ThreadPool {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(LogMessageExecutor.class);

  /**
   * Constructs a LogMessageExecutor with the necessary dependencies for Log message processing.
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
   * @param dispatcherConnector the connector that facilitates communication with remote
   *     dispatchers.
   * @param customClassloader the class loader for dynamically loading classes during execution.
   * @param peerUuid the unique identifier for the peer associated with this executor instance.
   */
  @Inject
  public LogMessageExecutor(
      @Named("log.threadPoolSize") String threadPoolSize,
      ZContext zmqContext,
      @Named("in.log") String logDealerAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      CustomClassloader customClassloader,
      UUID peerUuid) {

    super(
        Integer.parseInt(threadPoolSize),
        new LogRpcThreadFactory(
            zmqContext,
            logDealerAddress,
            messageBuilder,
            incomingMessageDispatcher,
            dispatcherConnector,
            RpcThreadFactory.RpcChannelType.LOG,
            customClassloader,
            peerUuid));
  }
}
