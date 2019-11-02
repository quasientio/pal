package com.ittera.cometa.core.exec;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.messages.InterceptsMsg;
import com.ittera.cometa.core.messages.OutboundMsg;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

@Singleton
public class DispatcherConnector {

  private static final Logger logger = LoggerFactory.getLogger(DispatcherConnector.class);

  private final ZContext zmqContext;
  private final UUID peerUuid;
  private final MessageBuilder messageBuilder;
  private final PALDirectory palDirectory;
  private final String outCellAddress;
  private final List<InternalHeader> WRITE_AHEAD_HEADERS;
  private final List<InternalHeader> INCOMING_INTERCEPT_REQ_HEADERS;
  static final int ERROR_READING_FROM_SOCKET = -2;

  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

  // per-thread REQ socket to send out messages
  private final ThreadLocal<Socket> threadSocket =
      new ThreadLocal<Socket>() {
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(outCellAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to outCellAddress: {}", outCellAddress);
          }
          threadSocketCreated.set(true);
          return worker;
        }
      };

  @Inject
  public DispatcherConnector(
      ZContext zmqContext,
      UUID peerUuid,
      MessageBuilder messageBuilder,
      PALDirectory palDirectory,
      @Named("out.cell") String outCellAddress) {
    this.zmqContext = zmqContext;
    this.peerUuid = peerUuid;
    this.messageBuilder = messageBuilder;
    this.palDirectory = palDirectory;
    this.outCellAddress = outCellAddress;
    this.WRITE_AHEAD_HEADERS =
        Collections.singletonList(messageBuilder.buildWriteAheadHeader(peerUuid));
    this.INCOMING_INTERCEPT_REQ_HEADERS =
        Collections.singletonList(messageBuilder.buildIncomingInterceptRequestHeader());
  }

  public ExecMessage sendExecMessage(ExecMessage message, ExecPhase execPhase) {
    return sendExecMessage(message, execPhase, null);
  }

  private ExecMessage sendExecMessage(
      ExecMessage message, ExecPhase execPhase, @Nullable List<InternalHeader> headers) {
    if (logger.isTraceEnabled()) {
      logger.trace("sendExecMessage:in w/ message with uuid: {}", message.getMessageUuid());
    }
    Socket outSocket = threadSocket.get();
    // send
    UUID followingUuid =
        message.hasFollowingUuid() ? UUID.fromString(message.getFollowingUuid()) : null;
    final OutboundMsg msg =
        new OutboundMsg(
            MessageType.ExecMessage,
            execPhase,
            headers,
            UUID.fromString(message.getMessageUuid()),
            followingUuid,
            message.toByteArray());
    msg.send(outSocket);

    // receive intercepts, if any
    InterceptsMsg interceptsMsg = null;
    try {
      interceptsMsg = InterceptsMsg.recvMsg(outSocket, true);
    } catch (ZMQException ex) {
      int errorCode = ex.getErrorCode();
      if (errorCode == ZError.ETERM) {
        logger.warn("Caught ETERM during blocking read. Will close socket");
        outSocket.close();
      } else if (errorCode == ZError.EINTR) {
        logger.warn("Caught EINTR during blocking read. Will close socket.");
        outSocket.close();
      }
    } catch (InvalidProtocolBufferException e) {
      logger.error("Error parsing received message", e);
    }

    // in case of error simply return received ExecMessage
    if (interceptsMsg == null) {
      return message;
    }

    // deal with intercepts
    final ExecMessage returnValue;
    if (interceptsMsg.getIntercepts() == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("No intercepts for message");
      }
      returnValue = message;
    } else {
      // for now we only care about the first intercept request
      InterceptRequest interceptRequest = interceptsMsg.getIntercepts().get(0);
      ExecMessage callbackMessage =
          messageBuilder.buildCallbackForInterceptRequest(peerUuid, message, interceptRequest);
      UUID interceptor = UUID.fromString(interceptRequest.getPeerUuid());
      final byte[] reply;
      try {
        if (interceptRequest.getType().equals(InterceptType.BEFORE_ASYNC)
            || interceptRequest.getType().equals(InterceptType.AFTER_ASYNC)) {
          sendAsyncCallbackToPeer(interceptor, callbackMessage);
        } else if (interceptRequest.getType().equals(InterceptType.BEFORE)
            || interceptRequest.getType().equals(InterceptType.AFTER)) {
          reply = sendCallbackToPeer(interceptor, callbackMessage);
        } else {
          logger.error("Unsupported callback type: {}", interceptRequest.getType());
        }
      } catch (Exception ex) {
        logger.error(
            "Error sending callback to peer w/uuid: {}, callback message: {}",
            interceptor,
            callbackMessage);
      }

      //      if (interceptRequest.getType().equals(Intercepts.InterceptType.AROUND)) {
      // TODO in case of AROUND we should return the message returned by callback only in
      // ExecPhase.After
      // reply = sendCallbackToPeer(interceptor, callbackMessage);
      // only parse message when needed
      // final ExecMessage replyMsg = ExecMessage.parseFrom(reply);
      // returnValue = replyMsg;
      //      } else {
      returnValue = message;
      //    }

    }

    if (logger.isTraceEnabled()) {
      logger.trace("out w/ {}", returnValue);
    }
    return returnValue;
  }

  private int sendInterceptRequest(
      InterceptRequest message, @Nullable List<InternalHeader> headers) {
    if (logger.isTraceEnabled()) {
      logger.trace("sendInterceptRequest:in w/ message with uuid: {}", message.getMessageUuid());
    }

    Socket outSocket = threadSocket.get();
    // send
    final OutboundMsg msg =
        new OutboundMsg(
            MessageType.InterceptRequest,
            null,
            headers,
            UUID.fromString(message.getMessageUuid()),
            null,
            message.toByteArray());
    msg.send(outSocket);

    // receive
    String rcvdString = null;
    try {
      rcvdString = outSocket.recvStr();
    } catch (ZMQException ex) {
      int errorCode = ex.getErrorCode();
      if (errorCode == ZError.ETERM) {
        logger.warn("Caught ETERM during blocking read. Will close socket");
        outSocket.close();
      } else if (errorCode == ZError.EINTR) {
        logger.warn("Caught EINTR during blocking read. Will close socket.");
        outSocket.close();
      }
      return ERROR_READING_FROM_SOCKET;
    }

    if (logger.isTraceEnabled()) {
      logger.trace("out w/ {}", rcvdString);
    }
    return Integer.parseInt(rcvdString);
  }

  public void writeAhead(ExecMessage message) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "writeAhead:in w/ message with uuid: {},from {}",
          message.getMessageUuid(),
          message.getPeerUuid());
    }

    // by sending out <write_ahead> header the Log Writer will serialize it with a <dispatching-by>
    // header
    sendExecMessage(message, ExecPhase.BEFORE, WRITE_AHEAD_HEADERS);
  }

  public int sendOutInterceptRequestMessage(InterceptRequest message) {
    return sendInterceptRequest(message, null);
  }

  /**
   * Register intercept info of an incoming InterceptRequest message
   *
   * @param message
   * @return {@code ERROR_READING_FROM_SOCKET} if an error occurs reading from the socket (note that
   *     registration may have taken place), {@code 0} if intercept registration is confirmed,
   *     {@code -1} if registration failed
   */
  public int registerIntercept(InterceptRequest message) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "registerIntercept:in w/ message with uuid: {},from {}",
          message.getMessageUuid(),
          message.getPeerUuid());
    }

    return sendInterceptRequest(message, INCOMING_INTERCEPT_REQ_HEADERS);
  }

  private @Nullable byte[] sendCallbackMessageToPeer(
      UUID interceptor, ExecMessage message, boolean getReply) throws Exception {
    Socket req = zmqContext.createSocket(SocketType.REQ);
    // get peer's address
    String interceptorAddress;
    interceptorAddress = palDirectory.getPeerInfo(interceptor).getReqAddress();
    // connect to peer and send callback message
    req.connect(interceptorAddress);
    req.send(message.toByteArray());

    // block until we get a reply
    byte[] reply = null;
    if (getReply) {
      reply = req.recv(0);
      if (logger.isDebugEnabled()) {
        try {
          ExecMessage replyMessage = ExecMessage.parseFrom(reply);
          logger.debug("Got reply from callback: {}", replyMessage);
        } catch (InvalidProtocolBufferException e) {
          logger.warn("Error parsing reply message", e);
        }
      }
    }
    req.close();
    return reply;
  }

  private void sendAsyncCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    sendCallbackMessageToPeer(interceptor, message, false);
  }

  private byte[] sendCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    return sendCallbackMessageToPeer(interceptor, message, true);
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
