package com.ittera.cometa.core.exec;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.core.messages.InboundLogMsg;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

class LogMessageInvoker extends AbstractMessageInvokerThread {

  public LogMessageInvoker(
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

  LogMessageInvoker(
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

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }
    while (!Thread.interrupted()) {
      // recv req
      InboundLogMsg msg = null;
      try {
        msg = InboundLogMsg.recvMsg(socket, true);
        if (logger.isDebugEnabled()) {
          logger.debug("Getting message with kafka offset: {}", msg.getOffset());
        }
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
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving/parsing message", e);
      }

      if (msg == null) {
        continue;
      }

      Message requestMsg = null;
      long started = System.currentTimeMillis();

      // parse req
      try {
        requestMsg = Message.parseFrom(msg.getBody());
      } catch (InvalidProtocolBufferException e) {
        logger.error("Caught exception parsing message", e);
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "Received message with offset: {}, uuid: {}",
            msg.getOffset(),
            getMessageUuid(requestMsg));
      }

      // dispatch it
      if (requestMsg != null) {
        dispatch(requestMsg, msg.getOffset());
        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched log message with uuid: {} in {} millisecs",
                getMessageUuid(requestMsg),
                took);
          }
        }
      }
    }

    closeConnections();
  }
}
