package com.ittera.cometa.core.exec;

import com.ittera.cometa.common.znodes.InterceptEvent;
import com.ittera.cometa.common.znodes.InterceptNodeListener;
import com.ittera.cometa.common.znodes.InterceptRequest;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
public class InterceptInformer implements InterceptNodeListener {

  private static final Logger logger = LoggerFactory.getLogger(InterceptInformer.class);

  private final ZContext zmqContext;
  private final MessageBuilder messageBuilder;
  private final PALDirectory palDirectory;
  private final String interceptsAddr;
  private final UUID peerUuid;

  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

  // per-thread REQ socket to send out messages
  private final ThreadLocal<Socket> threadSocket =
      new ThreadLocal<Socket>() {
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(interceptsAddr);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to interceptsAddress: {}", interceptsAddr);
          }
          threadSocketCreated.set(true);
          return worker;
        }
      };

  @Inject
  public InterceptInformer(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      PALDirectory palDirectory,
      UUID peerUuid,
      @Named("intercepts.reg") String interceptsAddr) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.palDirectory = palDirectory;
    this.peerUuid = peerUuid;
    this.interceptsAddr = interceptsAddr;
  }

  @Override
  public void interceptEvent(InterceptEvent event) {
    if (logger.isDebugEnabled()) {
      logger.debug("Got new intercept event: {}", event);
    }
    switch (event.getType()) {
      case INTERCEPT_ADDED:
        final String interceptPath = event.getInterceptPath();
        final InterceptRequest interceptRequest;
        if (event.getPeerUUID().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring self-produced intercept request");
          }
          return;
        }
        try {
          interceptRequest = palDirectory.getInterceptRequest(interceptPath);
        } catch (Exception e) {
          logger.warn("Error getting intercept request from directory", e);
          return;
        }
        InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);
        sendInterceptRequest(interceptMessage);
        break;
      case INTERCEPT_REMOVED:
        logger.warn("Unsupported operation");
        break;
    }
  }

  private boolean sendInterceptRequest(InterceptMessage message) {
    if (logger.isDebugEnabled()) {
      logger.debug("Sending new intercept message: {}", message);
    }
    // send
    Socket outSocket = threadSocket.get();
    outSocket.send(message.toByteArray(), 0);

    // receive
    String rcvdString;
    try {
      rcvdString = outSocket.recvStr();
    } catch (ZMQException ex) {
      int errorCode = ex.getErrorCode();
      if (errorCode == ZError.ETERM) {
        logger.warn("Caught ETERM during blocking read. Will close socket");
      } else if (errorCode == ZError.EINTR) {
        logger.warn("Caught EINTR during blocking read. Will close socket.");
      } else {
        logger.warn("Caught unknown error during blocking read. Will close socket.");
      }
      outSocket.close();
      return false;
    }
    if (rcvdString.equals("0")) {
      return true;
    } else {
      logger.warn("Received non-0 reply when informing of intercept: {}", message);
      return false;
    }
  }

  void closeThreadLocalSocket() {
    if (threadSocketCreated.get()) {
      Socket outSocket = threadSocket.get();
      if (outSocket != null) {
        outSocket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local socket closed");
        }
      }
    }
  }
}
