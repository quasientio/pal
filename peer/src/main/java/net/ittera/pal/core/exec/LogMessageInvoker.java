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

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.UUID;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.core.messages.InboundLogMsg;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

class LogMessageInvoker extends AbstractMessageInvokerThread {

  public LogMessageInvoker(
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

  LogMessageInvoker(
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

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }
    while (!Thread.interrupted()) {
      // recv req
      InboundLogMsg msg = null;
      try {
        msg = InboundLogMsg.recvMsg(socket, true);
        if (logger.isDebugEnabled()) {
          logger.debug("Getting message with kafka offset: {}", msg.getOffset());
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
        logger.error("Error receiving/parsing message", e);
      }

      if (msg == null) {
        continue;
      }

      Message requestMsg = null;
      long started = System.currentTimeMillis();

      // parse req
      try {
        requestMsg = Message.parseFrom(msg.getBody());
      } catch (InvalidProtocolBufferException e) {
        logger.error("Caught exception parsing message", e);
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "Received message with offset: {}, uuid: {}",
            msg.getOffset(),
            getMessageUuid(requestMsg));
      }

      // dispatch it
      if (requestMsg != null) {
        dispatch(requestMsg, msg.getOffset());
        if (logger.isDebugEnabled()) {
          final long took = System.currentTimeMillis() - started;
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Dispatched log message with uuid: {} in {} millisecs",
                getMessageUuid(requestMsg),
                took);
          }
        }
      }
    }

    closeConnections();
  }
}
