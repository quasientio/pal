package net.ittera.pal.core.exec;

import java.util.UUID;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

class PeerMessageInvoker extends AbstractMessageInvokerThread {

  public PeerMessageInvoker(
      ThreadGroup group,
      Runnable target,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(
        group,
        target,
        name,
        zmqContext,
        messageBuilder,
        dealerAddress,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
  }

  // Constructor for unit-testing
  PeerMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, dealerAddress, incomingMessageDispatcher, peerUuid);
  }

  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(SocketType.REP);
    socket.connect(dealerAddress);

    Message requestMsg;
    Message replyMsg;

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }

    boolean socketError = false;
    while (!interrupted() && !socketError) {

      // recv req
      byte[] req = null;

      try {
        req = socket.recv();
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
          if (logger.isDebugEnabled()) {
            logger.debug("Re-throwing unexpected exception", ex);
          }
          throw ex;
        }
      }

      if (req != null) {
        final long started = System.currentTimeMillis();

        requestMsg = null;

        // parse req
        try {
          requestMsg = Message.parseFrom(req);
        } catch (Exception e) {
          logger.error("Caught exception parsing message", e);
        }

        if (logger.isDebugEnabled()) {
          logger.debug(
              "Received req message with uuid: {}",
              requestMsg != null ? getMessageUuid(requestMsg) : null);
        }

        if (requestMsg != null) {

          // dispatch
          try {
            replyMsg = dispatch(requestMsg);

            // send reply
            socket.send(replyMsg.toByteArray());

            if (logger.isDebugEnabled()) {
              final long took = System.currentTimeMillis() - started;
              if (logger.isDebugEnabled()) {
                logger.debug(
                    "Dispatched and sent direct message w/uuid: {} in reply to request w/uuid: {} in {} millisecs",
                    getMessageUuid(replyMsg),
                    getMessageUuid(requestMsg),
                    took);
              }
            }
          } catch (Exception e) {
            logger.error("Error dispatching message w/uuid {}", getMessageUuid(requestMsg), e);
          }
        }
      }
    }
    closeConnections();
  }
}
