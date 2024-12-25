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

package net.ittera.pal.core.intercepts;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.core.messages.InterceptEventMsg;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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
  private final DirectoryConnectionProvider directoryConnectionProvider;
  private final String interceptsAddress;
  private final UUID peerUuid;

  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

  // per-thread REQ socket to send out messages
  private final ThreadLocal<Socket> threadSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(interceptsAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to interceptsAddress: {}", interceptsAddress);
          }
          threadSocketCreated.set(true);
          return worker;
        }
      };

  @Inject
  public InterceptInformer(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider,
      UUID peerUuid,
      @Named("intercepts.reg") String interceptsAddress) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.peerUuid = peerUuid;
    this.interceptsAddress = interceptsAddress;
  }

  public void registerAllInterceptsInDirectory() {
    final Set<PeerInfo> peers;
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    try {
      peers = palDirectory.getAllPeers();
    } catch (Exception e) {
      logger.error("Error retrieving peers from directory", e);
      return;
    }

    final List<InterceptRequest<?>> allInterceptRequests = new ArrayList<>();
    for (PeerInfo peer : peers) {
      try {
        allInterceptRequests.addAll(palDirectory.getPeerInterceptRequests(peer.getUuid()));
      } catch (Exception e) {
        logger.error("Error retrieving intercepts for peer w/uuid:{}", peer.getUuid(), e);
      }
    }

    allInterceptRequests.forEach(
        interceptRequest -> {
          InterceptMessage interceptMessage =
              messageBuilder.buildInterceptMessage(interceptRequest);
          sendInterceptEventMsg(new InterceptEventMsg(ColferUtils.toBytes(interceptMessage)));
        });
  }

  @Override
  public void interceptEvent(InterceptEvent event) {
    if (logger.isDebugEnabled()) {
      logger.debug("Got new intercept event: {}", event);
    }

    InterceptEventMsg interceptEventMsg;
    switch (event.type()) {
      case INTERCEPT_ADDED:
        final InterceptRequest<?> interceptRequest = event.interceptRequest();
        if (event.peerUuid().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring self-produced intercept request: {}", interceptRequest);
          }
          return;
        }
        Objects.requireNonNull(interceptRequest);
        InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);
        interceptEventMsg = new InterceptEventMsg(ColferUtils.toBytes(interceptMessage));
        break;
      case INTERCEPT_REMOVED:
        if (event.peerUuid().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Ignoring unregistration of self-produced intercept request: {}",
                event.interceptRequest());
          }
          return;
        }
        String interceptMsgId = event.interceptId();
        interceptEventMsg = new InterceptEventMsg(interceptMsgId);
        break;
      default:
        throw new IllegalStateException("Unexpected intercept event type: " + event.type());
    }

    sendInterceptEventMsg(interceptEventMsg);
  }

  private void sendInterceptEventMsg(InterceptEventMsg message) {
    if (logger.isTraceEnabled()) {
      logger.trace("Sending new intercept evt message: {}", message);
    }
    // send
    Socket outSocket = threadSocket.get();
    message.send(outSocket);

    // receive
    String rcvdString = null;
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
    }
    if (!"0".equals(rcvdString)) {
      logger.warn(
          "Received non-0 reply (code={}) when informing of intercept event: {}",
          rcvdString,
          message);
    }
  }

  public void closeThreadLocalSocket() {
    if (Boolean.TRUE.equals(threadSocketCreated.get())) {
      Socket outSocket = threadSocket.get();
      if (outSocket != null) {
        outSocket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local socket closed");
        }
      }
      threadSocket.remove();
    }
    threadSocketCreated.remove();
  }
}
