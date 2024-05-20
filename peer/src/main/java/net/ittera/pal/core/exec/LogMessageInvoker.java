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
import net.ittera.pal.core.messages.InboundLogMsg;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

class LogMessageInvoker extends AbstractMessageInvokerThread {

  private final String logDealerAddress;
  private ZMQ.Socket socket;

  public LogMessageInvoker(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String logDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(
        group,
        name,
        zmqContext,
        messageBuilder,
        incomingMessageDispatcher,
        dispatcherConnector,
        peerUuid);
    this.logDealerAddress = logDealerAddress;
  }

  LogMessageInvoker(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String logDealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    super(zmqContext, messageBuilder, incomingMessageDispatcher, peerUuid);
    this.logDealerAddress = logDealerAddress;
  }

  @Override
  public void run() {

    // create REP socket
    socket = zmqContext.createSocket(SocketType.REP);
    socket.connect(logDealerAddress);

    if (logger.isDebugEnabled()) {
      logger.debug("Start getting requests from socket");
    }
    while (!Thread.interrupted()) {
      // receive message
      InboundLogMsg msg = null;
      try {
        msg = InboundLogMsg.receive(socket, true);
        assert msg != null;
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

      final Message requestMsg = new Message();
      final long started = System.currentTimeMillis();

      // parse req
      try {
        requestMsg.unmarshal(msg.getBody(), 0);
      } catch (Exception e) {
        logger.error("Caught exception parsing message", e);
        continue;
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "Received message with offset: {}, uuid: {}",
            msg.getOffset(),
            getMessageUuid(requestMsg));
      }

      // dispatch it
      dispatch(requestMsg, msg.getOffset());
      if (logger.isDebugEnabled()) {
        final long took = System.currentTimeMillis() - started;
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Dispatched log message with uuid: {} in {} ms", getMessageUuid(requestMsg), took);
        }
      }
    }

    closeConnections();
  }

  @Override
  protected void closeConnections() {
    try {
      socket.close();
    } catch (Exception e) {
      logger.debug("Error closing socket", e);
    }
    super.closeConnections();
  }
}
