package net.ittera.pal.core.exec;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.core.exec.java.CustomClassloader;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

@Singleton
public class PeerMessageExecutor extends ExtendedThreadPoolExecutor {

  protected static final Logger logger = LoggerFactory.getLogger(PeerMessageExecutor.class);

  @Inject
  public PeerMessageExecutor(
      @Named("peer.corePoolSize") String corePoolSize,
      @Named("peer.maximumPoolSize") String maximumPoolSize,
      @Named("peer.keepAliveSeconds") String keepAliveSeconds,
      ZContext zmqContext,
      @Named("in.dealer") String zmqSocketAddress,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      CustomClassloader customClassloader,
      UUID peerUuid) {

    super(
        Integer.parseInt(corePoolSize),
        Integer.parseInt(maximumPoolSize),
        Integer.parseInt(keepAliveSeconds),
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ExecThreadFactory(
            zmqContext,
            zmqSocketAddress,
            messageBuilder,
            incomingMessageDispatcher,
            dispatcherConnector,
            ExecThreadFactory.ExecChannelType.PEER,
            customClassloader,
            peerUuid));
  }
}
