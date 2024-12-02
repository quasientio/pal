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
import java.util.UUID;
import net.ittera.pal.messages.OutboundMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

// TODO replace this with a XPUB-XSUB proxy
@Singleton
class MessagePublisher extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(MessagePublisher.class);
  private static final String OK_REPLY = "0";
  private static final String ERROR_REPLY = "1";

  // zmq stuff
  private Socket repSocket;
  private Socket pubSocket;
  private final String outRepAddress;
  private final String outPubAddress;

  @Inject
  public MessagePublisher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("MessagePublisher.service") String serviceName,
      @Named("out.cell") String outRepAddress,
      @Named("out.pub") String outPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.outRepAddress = outRepAddress;
    this.outPubAddress = outPubAddress;
  }

  @Override
  protected void openConnections() {
    // open REP and PUB sockets
    repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.bind(outRepAddress);
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(outPubAddress);
  }

  @Override
  public final void run() {
    boolean socketError = false;
    while (!Thread.interrupted() && !socketError) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.receive(repSocket, true);
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          socketError = true;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          socketError = true;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving message", e);
        repSocket.send(ERROR_REPLY);
      }
      if (msg != null) {
        // reply OK
        repSocket.send(OK_REPLY);
        // publish the message
        msg.send(pubSocket);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Published new message w/id: {} ({} bytes)", msg.getMessageId(), msg.getSize());
        }
      }
    }
  }

  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
    closeConnection(pubSocket, "Error closing PUB socket");
  }
}
