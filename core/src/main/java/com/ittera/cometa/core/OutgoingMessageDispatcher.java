package com.ittera.cometa.core;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.core.exec.ExecPhase;
import com.ittera.cometa.core.messages.InterceptsMsg;
import com.ittera.cometa.core.messages.OutboundMsg;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

@Singleton
class OutgoingMessageDispatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(OutgoingMessageDispatcher.class);
  static final String ERROR_REPLY = "-1";

  // zmq stuff
  private Socket repSocket, pubSocket;
  private final String outCellAddress, outPubAddress;

  private final Map<InterceptType, InterceptRequests> allIntercepts = new HashMap<>();

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
    for (InterceptType interceptType : InterceptType.values()) {
      allIntercepts.put(interceptType, new InterceptRequests());
    }
  }

  @Override
  protected void openConnections() {
    // open REP and PUB sockets
    repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.bind(outCellAddress);
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(outPubAddress);
  }

  private boolean registerInterceptRequest(OutboundMsg interceptRequestMsg) {

    InterceptRequest incomingInterceptRequest;
    // parse message
    try {
      incomingInterceptRequest = InterceptRequest.parseFrom(interceptRequestMsg.getBody());
    } catch (InvalidProtocolBufferException e) {
      logger.error("Error parsing intercept request message", e);
      return false;
    }
    InterceptRequests registeredIntercepts = allIntercepts.get(incomingInterceptRequest.getType());
    return registeredIntercepts.registerInterceptRequest(incomingInterceptRequest);
  }

  private List<InterceptRequest> getMatchingIntercepts(ExecMessage execMessage, ExecPhase phase) {
    if (phase.equals(ExecPhase.BEFORE)) {
      final List<InterceptRequest> beforeIntercepts =
          allIntercepts.get(InterceptType.BEFORE).getMatchingIntercepts(execMessage);
      final List<InterceptRequest> beforeAsyncIntercepts =
          allIntercepts.get(InterceptType.BEFORE_ASYNC).getMatchingIntercepts(execMessage);
      final List<InterceptRequest> aroundIntercepts =
          allIntercepts.get(InterceptType.AROUND).getMatchingIntercepts(execMessage);
      final List<InterceptRequest> allIntercepts =
          new ArrayList<>(
              beforeIntercepts.size() + beforeAsyncIntercepts.size() + aroundIntercepts.size());
      allIntercepts.addAll(beforeIntercepts);
      allIntercepts.addAll(beforeAsyncIntercepts);
      allIntercepts.addAll(aroundIntercepts);
      return allIntercepts;
    } else if (phase.equals(ExecPhase.AFTER)) {
      final List<InterceptRequest> afterIntercepts =
          allIntercepts.get(InterceptType.AFTER).getMatchingIntercepts(execMessage);
      final List<InterceptRequest> afterAsyncIntercepts =
          allIntercepts.get(InterceptType.AFTER_ASYNC).getMatchingIntercepts(execMessage);
      final List<InterceptRequest> allIntercepts =
          new ArrayList<>(afterIntercepts.size() + afterAsyncIntercepts.size());
      allIntercepts.addAll(afterIntercepts);
      allIntercepts.addAll(afterAsyncIntercepts);
      return allIntercepts;
    } else {
      throw new UnsupportedOperationException("Unsupported execution phase: " + phase);
    }
  }

  private boolean isIncomingInterceptRequest(OutboundMsg msg) {
    return msg.getHeaders() != null
        && msg.getHeaders().stream()
            .anyMatch(h -> h.getHeaderType().equals(InternalHeaderType.INCOMING_INTERCEPT_REQ));
  }

  private void publish(OutboundMsg msg) {
    msg.send(pubSocket);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Published new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
    }
  }

  @Override
  public final void run() {
    while (!Thread.interrupted()) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.recvMsg(repSocket);
        if (msg == null) {
          continue;
        }
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Received new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
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
        logger.error("Error parsing received message", e);
      }
      // deal with message, send reply and [publish]
      if (msg != null) {
        // Intercept Requests
        if (msg.getMessageType().equals(MessageType.InterceptRequest)) {
          // Incoming (i.e. register for matching against ExecMessages)
          if (isIncomingInterceptRequest(msg)) {
            final boolean registered = registerInterceptRequest(msg);
            if (registered) {
              // reply 0 if registered (if it was already or we just did now)
              repSocket.send("0");
            } else {
              // not registered (due to errors or any other reason)
              repSocket.send(ERROR_REPLY);
            }
            // TODO if received via socket, then we SHOULD publish it
          } else { // Outgoing
            repSocket.send("0");
            publish(msg);
          }
        } // Exec Messages
        else if (msg.getMessageType().equals(MessageType.ExecMessage)) {
          ExecMessage execMessage;
          try {
            execMessage = ExecMessage.parseFrom(msg.getBody());
          } catch (InvalidProtocolBufferException e) {
            logger.error("Parsing received ExecMessage", e);
            repSocket.send(ERROR_REPLY);
            continue; // no need to publish
          }
          final List<InterceptRequest> matchingIntercepts =
              getMatchingIntercepts(execMessage, msg.getExecPhase());
          // reply to REQ with matching intercept requests, if any
          new InterceptsMsg(matchingIntercepts).send(repSocket);
          // send ExecMessage to PUB
          publish(msg);
        } else {
          logger.warn("Ignoring message of unsupported type: {}", msg);
          repSocket.send(ERROR_REPLY);
        }
      } else {
        logger.warn("null message");
        repSocket.send(ERROR_REPLY);
      }
    }
  }

  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
    closeConnection(pubSocket, "Error closing PUB socket");
  }
}
