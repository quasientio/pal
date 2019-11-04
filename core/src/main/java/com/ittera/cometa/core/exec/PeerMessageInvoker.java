package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import java.util.UUID;
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

    Message requestMsg, replyMsg;

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }

    while (!Thread.interrupted()) {

      // recv req
      byte[] req;

      try {
        req = socket.recv();
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          break;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          break;
        } else {
          if (logger.isDebugEnabled()) {
            logger.debug("Re-throwing unexpected exception", ex);
          }
          throw ex;
        }
      }

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
    closeConnections();
  }
}
