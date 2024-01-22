package net.ittera.pal.core.exec;

import java.util.UUID;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

public class PeerExecThreadFactory extends ExecThreadFactory {

  private final String rpcDealerSocketAddress;
  private final String jsonRpcDealerSocketAddress;

  public PeerExecThreadFactory(
      ZContext zmqContext,
      String rpcDealerSocketAddress,
      String jsonRpcDealerSocketAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      ExecChannelType execChannelType,
      ClassLoader classLoader,
      UUID peerUuid) {
    super.init(
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        dispatcherConnector,
        execChannelType,
        classLoader,
        peerUuid);
    this.rpcDealerSocketAddress = rpcDealerSocketAddress;
    this.jsonRpcDealerSocketAddress = jsonRpcDealerSocketAddress;
  }

  @Override
  protected AbstractMessageInvokerThread createInvokerThread(String newThreadName) {
    return new PeerMessageInvoker(
        threadGroup,
        newThreadName,
        zmqContext,
        messageBuilder,
        rpcDealerSocketAddress,
        jsonRpcDealerSocketAddress,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
  }
}
