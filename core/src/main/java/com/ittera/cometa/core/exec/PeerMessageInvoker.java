package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
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
      DispatcherConnector dispatcherConnector) {
    super(
        group,
        target,
        name,
        zmqContext,
        messageBuilder,
        dealerAddress,
        incomingMessageDispatcher,
        dispatcherConnector);
  }

  PeerMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher) {
    super(zmqContext, messageBuilder, dealerAddress, incomingMessageDispatcher);
  }

  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(SocketType.REP);
    socket.connect(dealerAddress);

    ExecMessage requestMsg, replyMsg;

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
        requestMsg = ExecMessage.parseFrom(req);
      } catch (Exception e) {
        logger.error("Caught exception parsing message", e);
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "Received req message with uuid: {}",
            requestMsg != null ? requestMsg.getMessageUuid() : null);
      }

      if (requestMsg != null) {

        // dispatch
        replyMsg = dispatch(requestMsg);

        // send reply
        socket.send(replyMsg.toByteArray());

        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched and sent direct message w/uuid: {} in reply to request w/uuid: {} in {} millisecs",
                replyMsg.getMessageUuid(),
                requestMsg.getMessageUuid(),
                took);
          }
        }
      }
    }
    closeConnections();
  }
}
