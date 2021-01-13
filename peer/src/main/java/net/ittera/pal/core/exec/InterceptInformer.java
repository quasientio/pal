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

package net.ittera.pal.core.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.core.messages.InterceptEvtMsg;
import net.ittera.pal.cxn.DirectoryConnectionFactory;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
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
  private final DirectoryConnectionFactory directoryConnectionFactory;
  private final String interceptsAddr;
  private final UUID peerUuid;

  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

  // per-thread REQ socket to send out messages
  private final ThreadLocal<Socket> threadSocket =
      new ThreadLocal<Socket>() {
        @Override
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
      DirectoryConnectionFactory directoryConnectionFactory,
      UUID peerUuid,
      @Named("intercepts.reg") String interceptsAddr) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionFactory = directoryConnectionFactory;
    this.peerUuid = peerUuid;
    this.interceptsAddr = interceptsAddr;
  }

  public void registerAllInterceptsInDirectory() {
    final Set<PeerInfo> peers;
    final PALDirectory palDirectory =
        directoryConnectionFactory.getConnection().orElseThrow(RuntimeException::new);
    try {
      peers = palDirectory.getAllPeers();
    } catch (Exception e) {
      logger.error("Error retrieving peers from directory", e);
      return;
    }

    final List<InterceptRequest> allInterceptRequests = new ArrayList<>();
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
          sendInterceptEventMsg(new InterceptEvtMsg(interceptMessage.toByteArray()));
        });
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
          final PALDirectory palDirectory =
              directoryConnectionFactory.getConnection().orElseThrow(RuntimeException::new);
          interceptRequest = palDirectory.getInterceptRequest(interceptPath);
        } catch (Exception e) {
          logger.warn("Error getting intercept request from directory", e);
          return;
        }
        InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);
        sendInterceptEventMsg(new InterceptEvtMsg(interceptMessage.toByteArray()));
        break;
      case INTERCEPT_REMOVED:
        if (event.getPeerUUID().equals(peerUuid)) {
          if (logger.isDebugEnabled()) {
            logger.debug("Ignoring unregistration of self-produced intercept");
          }
          return;
        }
        UUID interceptMsg = event.getInterceptUUID();
        sendInterceptEventMsg(new InterceptEvtMsg(interceptMsg));
        break;
    }
  }

  private void sendInterceptEventMsg(InterceptEvtMsg message) {
    if (logger.isDebugEnabled()) {
      logger.debug("Sending new intercept evt message: {}", message);
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
