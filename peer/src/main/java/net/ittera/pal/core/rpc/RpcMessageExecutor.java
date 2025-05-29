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
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.rpc.exec.java.CustomClassloader;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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
public class RpcMessageExecutor extends ThreadPool {

  /** Logger instance. */
  protected static final Logger logger = LoggerFactory.getLogger(RpcMessageExecutor.class);

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
   * @param dispatcherConnector the connector for message routing.
   * @param customClassloader the custom class loader employed for dynamic loading of RPC-invoked
   *     classes.
   * @param peerUuid a unique identifier for this RPC peer within the network.
   */
  @Inject
  public RpcMessageExecutor(
      @Named("rpc.threadPoolSize") String threadPoolSize,
      ZContext zmqContext,
      Set<RunOptions> runOptions,
      @Named("in.dealer") String rpcDealerAddress,
      @Named("json.in.dealer") String jsonrpcDealerAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
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
            dispatcherConnector,
            RpcThreadFactory.RpcChannelType.SOCKET,
            customClassloader,
            peerUuid));
  }
}
