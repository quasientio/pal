package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

public class LogMessageInvoker extends Thread {

  protected static final Logger logger = LoggerFactory.getLogger(LogMessageInvoker.class);

  protected AtomicLong requestsDispatched = new AtomicLong(0);
  protected AtomicLong requestsDismissed = new AtomicLong(0);

  // zmq stuff
  private ZContext zmqContext;
  private final String inLogAddress;
  private Socket socket;

  public LogMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext, String inLogAddress) {
    super(group, target, name);
    this.zmqContext = zmqContext;
    this.inLogAddress = inLogAddress;
    logger.debug("Initialized new log message invoker thread named: {} with inLogAddress: {}", name, inLogAddress);
  }

  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(ZMQ.REP);
    socket.connect(inLogAddress);

    DataMessage requestMsg;

    logger.debug("Start getting requests from socket");
    while (!Thread.interrupted()) {

      String offset = null;
      long logOffset;
      byte[] req = null;

      // recv req
      try {
        offset = socket.recvStr();
        logger.debug("Getting message with kafka offset: {}", offset);
        logOffset = Long.parseLong(offset);
        req = socket.recv();
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          logger.debug("Caught ETERM during blocking read. Breaking out.");
          break;
        } else if (errorCode == ZError.EINTR) {
          logger.debug("Caught EINTR during blocking read. Breaking out.");
          break;
        } else {
          throw ex;
        }
      }

      requestMsg = null;
      long started = System.currentTimeMillis();

      // parse req
      try {
        requestMsg = DataMessage.parseFrom(req);
      } catch (Exception e) {
        logger.error("Caught exception parsing message", e);
      }

      logger.debug("Received req message with uuid: {}", requestMsg.getMessageUuid());

      // dispatch it
      if (requestMsg != null) {

        // we dispatch only if concentrator uuid isn't ours
        if (!Concentrator.uuid.toString().equals(requestMsg.getConcentratorUuid())) {
          dispatch(requestMsg, logOffset);
          if (logger.isDebugEnabled()) {
            final long took = System.currentTimeMillis() - started;
            logger.debug("Dispatched data message with uuid: {} in {} millisecs", requestMsg.getMessageUuid(), took);
          }
        } else {
          requestsDismissed.getAndIncrement();
          logger.debug("Discarding message with uuid: {}", requestMsg.getMessageUuid());
        }
      }
    }

    closeConnections();

    logger.debug("Stopped log executor thread: {}, dispatched={} dismissed={}", getName(), requestsDispatched.get(),
      requestsDismissed.get());
  }

  protected void closeConnections() {

    if (socket != null) {
      socket.close();
    }

    Concentrator.closeThreadLocalSocket();
  }

  private DataMessage dispatch(DataMessage requestMsg, long recordOffset) {
    DataMessage replyMsg = Concentrator.incomingCall(requestMsg);
    logger.debug("Invoker dispatched log request message uuid: {} and recordOffset: {}, reply uuid: {}",
      requestMsg.getMessageUuid(), recordOffset, replyMsg.getMessageUuid());
    requestsDispatched.getAndIncrement();
    return replyMsg;
  }

}
