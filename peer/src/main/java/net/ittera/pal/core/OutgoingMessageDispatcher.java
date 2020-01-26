package net.ittera.pal.core;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.messages.OutboundMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/** TODO replace this with a XPUB-XSUB proxy */
@Singleton
class OutgoingMessageDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(OutgoingMessageDispatcher.class);
  private static final String OK_REPLY = "0";
  private static final String ERROR_REPLY = "1";

  // zmq stuff
  private Socket repSocket;
  private Socket pubSocket;
  private final String outCellAddress;
  private final String outPubAddress;

  @Inject
  public OutgoingMessageDispatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("OutgoingMessageDispatcher.service") String serviceName,
      @Named("out.cell") String outCellAddress,
      @Named("out.pub") String outPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.outCellAddress = outCellAddress;
    this.outPubAddress = outPubAddress;
  }

  @Override
  protected void openConnections() {
    // open REP and PUB sockets
    repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.bind(outCellAddress);
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(outPubAddress);
  }

  @Override
  public final void run() {
    boolean socketError = false;
    while (!Thread.interrupted() && !socketError) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.recvMsg(repSocket);
        if (msg == null) {
          continue;
        }
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          socketError = true;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          socketError = true;
        } else {
          throw ex;
        }
      } catch (InvalidProtocolBufferException e) {
        logger.error("Error parsing received message", e);
        repSocket.send(ERROR_REPLY);
      } catch (Exception e) {
        logger.error("Error receiving message", e);
        repSocket.send(ERROR_REPLY);
      }
      if (msg != null) {
        // reply OK
        repSocket.send(OK_REPLY);
        // publish the message
        msg.send(pubSocket);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Published new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
        }
      }
    }
  }

  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
    closeConnection(pubSocket, "Error closing PUB socket");
  }
}
