/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.exec.DuplicateInterceptException;
import net.ittera.pal.core.messages.InterceptEvtMsg;
import net.ittera.pal.core.messages.InterceptEvtMsg.Type;
import net.ittera.pal.core.messages.InterceptsMsg;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptType;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
public class Intercepts extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(Intercepts.class);

  // zmq stuff
  private Socket registerSocket; // to listen for new intercepts and register them
  private Socket matchSocket; // to listen for messages that may match registered intercepts
  private final String interceptRegAddress, interceptMatchAddress;

  // intercept registration reply codes
  public static final String REG_OK_REPLY = "0";
  public static final String UNREG_OK_REPLY = "0";
  public static final String REG_DUP_REPLY = "1";
  public static final String REG_PARSING_ERROR_REPLY = "2";
  public static final String REG_UNKNOWN_ERROR_REPLY = "3";

  // intercept matching reply codes
  public static final String MATCH_ERROR_REPLY = "1";

  // map holding all intercepts
  private final Map<InterceptType, InterceptRequests> allIntercepts =
      new EnumMap<>(InterceptType.class);

  // cache
  private final Map<InterceptKeyMessage, List<InterceptMessage>> cache = new HashMap<>();

  private boolean regPollingError;
  private boolean matchPollingError;

  @Inject
  public Intercepts(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Intercepts.service") String serviceName,
      @Named("intercepts.reg") String interceptRegAddress,
      @Named("intercepts.mtx") String interceptMatchAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.interceptRegAddress = interceptRegAddress;
    this.interceptMatchAddress = interceptMatchAddress;
    // initialize intercept registry
    for (InterceptType interceptType : InterceptType.values()) {
      allIntercepts.put(interceptType, new InterceptRequests());
    }
  }

  @Override
  protected void openConnections() {
    // init sockets
    registerSocket = zmqContext.createSocket(SocketType.REP);
    registerSocket.bind(interceptRegAddress);

    matchSocket = zmqContext.createSocket(SocketType.REP);
    matchSocket.bind(interceptMatchAddress);
  }

  private void registerInterceptRequest(InterceptMessage incomingInterceptMessage)
      throws DuplicateInterceptException {
    InterceptRequests registeredIntercepts = allIntercepts.get(incomingInterceptMessage.getType());
    registeredIntercepts.registerInterceptRequest(incomingInterceptMessage);
    if (logger.isDebugEnabled()) {
      logger.debug("Registered incoming intercept message: {}", incomingInterceptMessage);
    }
  }

  private List<InterceptMessage> getMatchingIntercepts(
      InterceptKeyMessage keyExecMessage, ExecPhase phase) {
    if (ExecPhase.BEFORE.equals(phase)) {
      final List<InterceptMessage> beforeIntercepts =
          allIntercepts.get(InterceptType.BEFORE).getMatchingIntercepts(keyExecMessage);
      final List<InterceptMessage> beforeAsyncIntercepts =
          allIntercepts.get(InterceptType.BEFORE_ASYNC).getMatchingIntercepts(keyExecMessage);
      final List<InterceptMessage> aroundIntercepts =
          allIntercepts.get(InterceptType.AROUND).getMatchingIntercepts(keyExecMessage);
      final List<InterceptMessage> interceptMessages =
          new ArrayList<>(
              beforeIntercepts.size() + beforeAsyncIntercepts.size() + aroundIntercepts.size());
      interceptMessages.addAll(beforeIntercepts);
      interceptMessages.addAll(beforeAsyncIntercepts);
      interceptMessages.addAll(aroundIntercepts);
      return interceptMessages;
    } else if (ExecPhase.AFTER.equals(phase)) {
      final List<InterceptMessage> afterIntercepts =
          allIntercepts.get(InterceptType.AFTER).getMatchingIntercepts(keyExecMessage);
      final List<InterceptMessage> afterAsyncIntercepts =
          allIntercepts.get(InterceptType.AFTER_ASYNC).getMatchingIntercepts(keyExecMessage);
      final List<InterceptMessage> allIntercepts =
          new ArrayList<>(afterIntercepts.size() + afterAsyncIntercepts.size());
      allIntercepts.addAll(afterIntercepts);
      allIntercepts.addAll(afterAsyncIntercepts);
      return allIntercepts;
    } else {
      throw new UnsupportedOperationException("Unsupported execution phase: " + phase);
    }
  }

  @Override
  public final void run() {
    while (!Thread.interrupted()) {
      if (regPollingError && matchPollingError) {
        break;
      }
      // poll registerSocket and dispatch
      if (!regPollingError) {
        try {
          registerNewAndGoneIntercepts();
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Caught ETERM during blocking read. No more polling for new intercepts.");
            }
            regPollingError = true;
            continue;
          } else if (errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "Caught EINTR during blocking read. No more polling for new intercepts.");
            }
            regPollingError = true;
            continue;
          } else {
            throw ex;
          }
        } catch (Exception e) {
          regPollingError = true;
          logger.error("Error receiving message. No more polling for new intercepts.", e);
        }
      }

      // poll matchSocket and dispatch
      if (!matchPollingError) {
        try {
          pollAndMatchIntercepts();
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught ETERM during blocking read. No more matching.");
            }
            matchPollingError = true;
          } else if (errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught EINTR during blocking read. No more matching.");
            }
            matchPollingError = true;
          } else {
            throw ex;
          }
        } catch (Exception e) {
          matchPollingError = true;
          logger.error("Error receiving message. No more matching.", e);
        }
      }
    }
  }

  private void registerNewAndGoneIntercepts() {
    InterceptEvtMsg interceptEvtMsg = InterceptEvtMsg.recvMsg(registerSocket);
    if (interceptEvtMsg == null) {
      return;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Received new intercept evt message ({} bytes)", interceptEvtMsg.getSize());
    }
    // parse message
    if (interceptEvtMsg.getType().equals(Type.REGISTER)) {
      InterceptMessage interceptMessage = null;
      try {
        interceptMessage = InterceptMessage.parseFrom(interceptEvtMsg.getBody());
      } catch (InvalidProtocolBufferException e) {
        logger.error("Error parsing intercept request message", e);
        registerSocket.send(REG_PARSING_ERROR_REPLY);
      }
      if (interceptMessage != null) {
        try {
          registerInterceptRequest(interceptMessage);
          registerSocket.send(REG_OK_REPLY);
          invalidateCache();
        } catch (DuplicateInterceptException e) {
          logger.warn("Cannot register duplicate intercept request", e);
          registerSocket.send(REG_DUP_REPLY);
        } catch (Exception e) {
          registerSocket.send(REG_UNKNOWN_ERROR_REPLY);
        }
      }
    } else { // Type.UNREGISTER
      UUID interceptUuid = interceptEvtMsg.getInterceptMsgUUID();
      allIntercepts
          .values()
          .forEach(
              interceptRequests ->
                  interceptRequests.unregisterInterceptRequest(interceptUuid.toString()));
      registerSocket.send(UNREG_OK_REPLY);
      invalidateCache();
    }
  }

  /** TODO: use sub-caches or another way to not invalidate entire cache */
  private void invalidateCache() {
    cache.clear();
  }

  private void pollAndMatchIntercepts() {
    // matching is given priority over reg, so we read messages til sink is empty or we hit an error
    while (true) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.recvMsg(matchSocket);
        if (msg == null) {
          break;
        }
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Received new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
        }
      } catch (InvalidProtocolBufferException e) {
        logger.error("Error parsing received message", e);
        matchSocket.send(MATCH_ERROR_REPLY);
        return;
      }

      // try to match message and send reply
      final Message message;
      try {
        message = Message.parseFrom(msg.getBody());
      } catch (InvalidProtocolBufferException e) {
        logger.error("Parsing received exec message", e);
        matchSocket.send(MATCH_ERROR_REPLY);
        return;
      }

      final List<InterceptMessage> matchingIntercepts;
      // lookup in cache first
      final List<InterceptMessage> cached = cache.get(message.getInterceptKeyMessage());
      if (cached != null) {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Cache hit with matching intercepts, for key: {}, returning {} intercepts",
              message.getInterceptKeyMessage(),
              cached.size());
        }
        matchingIntercepts = cached;
      } else {
        // no luck; try matching
        matchingIntercepts =
            getMatchingIntercepts(message.getInterceptKeyMessage(), msg.getExecPhase());
        cache.put(message.getInterceptKeyMessage(), matchingIntercepts);
      }
      // return all matching
      new InterceptsMsg(matchingIntercepts).send(matchSocket);
    }
  }

  @Override
  protected void closeConnections() {
    closeConnection(registerSocket, "Error closing register (REP) socket");
    closeConnection(matchSocket, "Error closing match (REP) socket");
  }
}
