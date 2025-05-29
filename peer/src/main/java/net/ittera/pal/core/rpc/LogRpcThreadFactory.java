package net.ittera.pal.core.rpc;

import java.util.UUID;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

/**
 * Factory class for creating RPC threads that handle Log messages.
 *
 * <p>This implementation extends RpcThreadFactory to provide specialized thread creation Log
 * message dispatch. It sets up threads with a designated Log dealer socket address.
 */
public class LogRpcThreadFactory extends RpcThreadFactory {

  /**
   * The address of the Log dealer socket. This address is provided during construction and passed
   * to the Log message invoker threads.
   */
  private final String logDealerSocketAddress;

  /**
   * Constructs a new LogRpcThreadFactory with the necessary Log message dispatching.
   *
   * <p>Initializes the thread factory with a ZeroMQ context, Log dealer socket address, message
   * builder, incoming message dispatcher, dispatcher connector, RPC channel type, class loader, and
   * peer UUID.
   *
   * @param zmqContext the ZeroMQ context used for managing communication sockets.
   * @param logDealerSocketAddress the Log dealer socket address.
   * @param messageBuilder the builder instance used for serializing Log messages.
   * @param incomingMessageDispatcher the dispatcher responsible for handling incoming Log messages.
   * @param dispatcherConnector the connector interfacing with the message dispatcher.
   * @param rpcChannelType the type of RPC channel used for message transmission.
   * @param classLoader the class loader used to resolve classes at runtime.
   * @param peerUuid the unique identifier of this peer.
   */
  public LogRpcThreadFactory(
      ZContext zmqContext,
      String logDealerSocketAddress,
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
    this.logDealerSocketAddress = logDealerSocketAddress;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a new instance of LogMessageInvoker. The new thread is assigned the provided thread
   * name and is initialized with the ZeroMQ context, message builder, and the Log dealer socket
   * address to enable processing of incoming Log messages.
   *
   * @param newThreadName the name to assign to the newly created thread.
   * @return a new instance of AbstractMessageInvokerThread for handling Log messages.
   */
  @Override
  protected AbstractMessageInvokerThread createInvokerThread(String newThreadName) {
    return new LogMessageInvoker(
        threadGroup,
        newThreadName,
        zmqContext,
        messageBuilder,
        logDealerSocketAddress,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
  }
}
