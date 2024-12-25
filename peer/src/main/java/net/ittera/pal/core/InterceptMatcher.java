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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.intercepts.DuplicateInterceptException;
import net.ittera.pal.core.intercepts.InterceptRequests;
import net.ittera.pal.core.messages.InterceptEventMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
public class InterceptMatcher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(InterceptMatcher.class);

  // zmq stuff
  private Socket registerSocket; // to listen for new intercepts and register them
  private final String interceptRegAddress;

  // intercept registration reply codes
  public static final String REGISTER_OK_REPLY = "0";
  public static final String UNREGISTER_OK_REPLY = "0";
  public static final String REGISTER_DUP_REPLY = "1";
  public static final String REGISTER_PARSING_ERROR_REPLY = "2";
  public static final String REGISTER_UNKNOWN_ERROR_REPLY = "3";
  public static final String UNREGISTER_UNKNOWN_ERROR_REPLY = "4";

  // map holding all intercepts
  private final Map<InterceptType, InterceptRequests> allIntercepts =
      new EnumMap<>(InterceptType.class);

  @Inject
  public InterceptMatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Intercepts.service") String serviceName,
      @Named("intercepts.reg") String interceptRegAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.interceptRegAddress = interceptRegAddress;
    // initialize intercept registry
    for (InterceptType interceptType : InterceptType.values()) {
      allIntercepts.put(interceptType, new InterceptRequests());
    }
  }

  @Override
  protected void openConnections() {
    registerSocket = zmqContext.createSocket(SocketType.REP);
    registerSocket.bind(interceptRegAddress);
  }

  private void registerInterceptRequest(InterceptMessage incomingInterceptMessage)
      throws DuplicateInterceptException {
    InterceptRequests registeredIntercepts =
        allIntercepts.get(InterceptType.fromByte(incomingInterceptMessage.getInterceptType()));
    registeredIntercepts.registerInterceptRequest(incomingInterceptMessage);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Registered incoming intercept message: {}",
          ColferUtils.format(incomingInterceptMessage));
    }
  }

  public List<InterceptMessage> getMatchingIntercepts(
      ExecMessage execMessage, MessageType messageType, ExecPhase phase) {
    if (ExecPhase.BEFORE.equals(phase)) {
      final List<InterceptMessage> beforeIntercepts =
          allIntercepts.get(InterceptType.BEFORE).getMatchingIntercepts(execMessage, messageType);
      final List<InterceptMessage> beforeAsyncIntercepts =
          allIntercepts
              .get(InterceptType.BEFORE_ASYNC)
              .getMatchingIntercepts(execMessage, messageType);
      final List<InterceptMessage> aroundIntercepts =
          allIntercepts.get(InterceptType.AROUND).getMatchingIntercepts(execMessage, messageType);
      final List<InterceptMessage> interceptMessages =
          new ArrayList<>(
              beforeIntercepts.size() + beforeAsyncIntercepts.size() + aroundIntercepts.size());
      interceptMessages.addAll(beforeIntercepts);
      interceptMessages.addAll(beforeAsyncIntercepts);
      interceptMessages.addAll(aroundIntercepts);
      return interceptMessages;
    }
    if (ExecPhase.AFTER.equals(phase)) {
      final List<InterceptMessage> afterIntercepts =
          allIntercepts.get(InterceptType.AFTER).getMatchingIntercepts(execMessage, messageType);
      final List<InterceptMessage> afterAsyncIntercepts =
          allIntercepts
              .get(InterceptType.AFTER_ASYNC)
              .getMatchingIntercepts(execMessage, messageType);
      final List<InterceptMessage> interceptMessages =
          new ArrayList<>(afterIntercepts.size() + afterAsyncIntercepts.size());
      interceptMessages.addAll(afterIntercepts);
      interceptMessages.addAll(afterAsyncIntercepts);
      return interceptMessages;
    }

    throw new UnsupportedOperationException("Unsupported execution phase: " + phase);
  }

  @Override
  public final void run() {
    while (!Thread.interrupted()) {
      // poll registerSocket and dispatch
      try {
        registerNewAndGoneIntercepts();
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. No more polling for new intercepts.");
          }
          break;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. No more polling for new intercepts.");
          }
          break;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving message. No more polling for new intercepts.", e);
        break;
      }
    }
  }

  private void registerNewAndGoneIntercepts() {
    InterceptEventMsg interceptEventMsg = InterceptEventMsg.receive(registerSocket, true);
    if (interceptEventMsg == null) {
      return;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Received new intercept evt message ({} bytes)", interceptEventMsg.getSize());
    }
    // parse message
    if (interceptEventMsg.getType().equals(InterceptEventMsg.Type.REGISTER)) {
      InterceptMessage interceptMessage = null;
      try {
        interceptMessage = new InterceptMessage();
        interceptMessage.unmarshal(interceptEventMsg.getBody(), 0);
      } catch (Exception e) {
        logger.error("Error parsing intercept request message", e);
        registerSocket.send(REGISTER_PARSING_ERROR_REPLY);
      }
      if (interceptMessage != null) {
        try {
          registerInterceptRequest(interceptMessage);
          registerSocket.send(REGISTER_OK_REPLY);
        } catch (DuplicateInterceptException e) {
          logger.warn("Cannot register duplicate intercept request", e);
          registerSocket.send(REGISTER_DUP_REPLY);
        } catch (Exception e) {
          registerSocket.send(REGISTER_UNKNOWN_ERROR_REPLY);
        }
      }
    } else { // Type.UNREGISTER
      String interceptMessageId = interceptEventMsg.getInterceptMessageId();
      if (interceptMessageId == null) {
        logger.error("Intercept id is null. Cannot unregister intercept request.");
        registerSocket.send(UNREGISTER_UNKNOWN_ERROR_REPLY);
        return;
      }
      allIntercepts
          .values()
          .forEach(
              interceptRequests ->
                  interceptRequests.unregisterInterceptRequest(interceptMessageId));
      registerSocket.send(UNREGISTER_OK_REPLY);
    }
  }

  @Override
  protected void closeConnections() {
    closeConnection(registerSocket, "Error closing register (REP) socket");
  }
}
