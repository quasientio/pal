package net.ittera.pal.core.rpc;

import java.util.UUID;
import net.ittera.pal.core.rpc.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

public class LogRpcThreadFactory extends RpcThreadFactory {

  private final String logDealerSocketAddress;

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
