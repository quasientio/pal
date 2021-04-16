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

package net.ittera.pal.messages;

import java.util.stream.Stream;
import net.ittera.pal.messages.colfer.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

public class MessageStreamer {
  private static final Logger logger = LoggerFactory.getLogger(MessageStreamer.class);
  private static final int STATS_TRACE_INTERVAL = 100;

  private ZContext zContext;
  private Socket subscriber;
  private final String host;
  private final int port;
  private long receivedMessagesCount;

  public MessageStreamer(String host, int port) {
    this.host = host;
    this.port = port;
  }

  public MessageStreamer connect() {
    // create zmq context
    zContext = new ZContext();
    zContext.setLinger(1000);
    zContext.setRcvHWM(1000);

    // init SUB socket and subscribe
    subscriber = zContext.createSocket(SocketType.SUB);
    final String connAddr = String.format("tcp://%s:%d", host, port);
    subscriber.connect(connAddr);
    subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
    if (logger.isDebugEnabled()) {
      logger.debug("Connected and subscribed to {}", connAddr);
    }
    return this;
  }

  private Message getNext() {

    Message message = null;
    try {
      OutboundMsg msg = OutboundMsg.recvMsg(subscriber, true);
      message = new Message();
      message.unmarshal(msg.getBody(), 0);
      receivedMessagesCount++;
    } catch (ZMQException ex) {
      int errorCode = ex.getErrorCode();
      if (errorCode == ZError.ETERM) {
        logger.warn("Caught ETERM during blocking read, returning null", ex);
      } else if (errorCode == ZError.EINTR) {
        logger.warn("Caught EINTR during blocking read, returning null.", ex);
      } else {
        logger.warn("Unknown exception, returning null.", ex);
      }
    }
    if (logger.isDebugEnabled() && receivedMessagesCount % STATS_TRACE_INTERVAL == 0) {
      logger.debug("Total messages streamed so far: {}", receivedMessagesCount);
    }
    return message;
  }

  public void close() {
    subscriber.close();
    zContext.close();
  }

  public Stream<Message> getStream() {
    return Stream.generate(this::getNext);
  }

  public long getReceivedMessagesCount() {
    return receivedMessagesCount;
  }
}
