package net.ittera.pal.core.exec;

import java.util.UUID;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.ZContext;

public class LogExecThreadFactory extends ExecThreadFactory {

  private final String logDealerSocketAddress;

  public LogExecThreadFactory(
      ZContext zmqContext,
      String logDealerSocketAddress,
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
