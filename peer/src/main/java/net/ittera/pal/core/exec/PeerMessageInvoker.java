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

import java.util.UUID;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

class PeerMessageInvoker extends AbstractMessageInvokerThread {

  public PeerMessageInvoker(
      ThreadGroup group,
      Runnable target,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(
        group,
        target,
        name,
        zmqContext,
        messageBuilder,
        dealerAddress,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
  }

  // Constructor for unit-testing
  PeerMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, dealerAddress, incomingMessageDispatcher, peerUuid);
  }

  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(SocketType.REP);
    socket.connect(dealerAddress);

    Message requestMsg;
    Message replyMsg;
    byte[] req;

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }

    boolean socketError = false;
    while (!interrupted() && !socketError) {
      req = null;

      // receive req
      try {
        req = socket.recv(0);
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
          if (logger.isDebugEnabled()) {
            logger.debug("Re-throwing unexpected exception", ex);
          }
          throw ex;
        }
      }

      if (req == null) {
        continue;
      }

      final long started = System.currentTimeMillis();
      requestMsg = new Message();

      // parse req
      try {
        requestMsg.unmarshal(req, 0);
      } catch (Exception e) {
        logger.error("Caught exception parsing message", e);
        continue;
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Received req message with uuid: {}", getMessageUuid(requestMsg));
      }

      // dispatch
      try {
        replyMsg = dispatch(requestMsg);

        // send reply
        socket.send(ColferUtils.toBytes(replyMsg));

        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched and sent direct message w/uuid: {} in reply to request w/uuid: {} in {} ms",
                getMessageUuid(replyMsg),
                getMessageUuid(requestMsg),
                took);
          }
        }
      } catch (Exception e) {
        logger.error("Error dispatching message w/uuid {}", getMessageUuid(requestMsg), e);
      }
    }
    closeConnections();
  }
}
