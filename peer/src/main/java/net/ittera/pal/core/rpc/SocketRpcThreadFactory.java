package net.ittera.pal.core.rpc;

import java.util.Set;
import java.util.UUID;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.rpc.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

public class SocketRpcThreadFactory extends RpcThreadFactory {

  private final Set<RunOptions> runOptions;
  private final String rpcDealerSocketAddress;
  private final String jsonRpcDealerSocketAddress;

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
