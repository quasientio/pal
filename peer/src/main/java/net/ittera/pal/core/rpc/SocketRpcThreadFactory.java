package net.ittera.pal.core.rpc;

import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

/**
 * Creates and configures threads for handling RPC messages over socket-based channels.
 *
 * <p>This factory extends {@link RpcThreadFactory} and leverages a ZeroMQ context along with
 * specific socket addresses to establish both binary and JSON-based RPC communication channels.
 */
public class SocketRpcThreadFactory extends RpcThreadFactory {

  /**
   * Set of runtime options that influence the behavior and configuration of RPC message processing.
   */
  private final Set<RunOptions> runOptions;

  /**
   * The socket address for the RPC dealer channel, used for routing messages via the ZMQ dealer
   * pattern.
   */
  private final String rpcDealerSocketAddress;

  /**
   * The socket address for the JSON RPC dealer channel, enabling JSON formatted RPC communication.
   */
  private final String jsonRpcDealerSocketAddress;

  /**
   * Constructs a new {@code SocketRpcThreadFactory} with the provided communication context,
   * message handling components, and socket addressing details.
   *
   * @param zmqContext the ZeroMQ context used to manage socket communications
   * @param runOptions a set of runtime options that dictate the processing behavior of RPC messages
   * @param rpcDealerSocketAddress the socket address for the binary RPC dealer channel
   * @param jsonRpcDealerSocketAddress the socket address for the JSON RPC dealer channel
   * @param messageBuilder the builder responsible for creating messages
   * @param incomingMessageDispatcher the dispatcher that handles incoming RPC messages
   * @param dispatcherConnector the connector used to link the dispatcher with its corresponding
   *     endpoints
   * @param rpcChannelType the type of RPC channel to be used for communication
   * @param classLoader the class loader utilized for dynamic loading within the RPC system
   * @param peerUuid the unique identifier representing this peer in the network
   */
  public SocketRpcThreadFactory(
      ZContext zmqContext,
      Set<RunOptions> runOptions,
      String rpcDealerSocketAddress,
      String jsonRpcDealerSocketAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      RpcChannelType rpcChannelType,
      ClassLoader classLoader,
      UUID peerUuid) {
    super.init(
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        dispatcherConnector,
        rpcChannelType,
        classLoader,
        peerUuid);
    this.runOptions = runOptions;
    this.rpcDealerSocketAddress = rpcDealerSocketAddress;
    this.jsonRpcDealerSocketAddress = jsonRpcDealerSocketAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a new invoker thread, which is responsible for processing and dispatching RPC
   * messages using the configured socket addresses and runtime options.
   *
   * @param newThreadName the name to assign to the newly created invoker thread for identification
   *     and debugging purposes
   * @return a new instance of {@link AbstractMessageInvokerThread} configured for RPC message
   *     handling
   */
  @Override
  protected AbstractMessageInvokerThread createInvokerThread(String newThreadName) {
    return new RpcMessageInvoker(
        threadGroup,
        newThreadName,
        zmqContext,
        messageBuilder,
        runOptions,
        rpcDealerSocketAddress,
        jsonRpcDealerSocketAddress,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
  }
}
