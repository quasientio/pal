package com.ittera.cometa.messages;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
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

  private ExecMessage getNext() {
    byte[] buff;
    int headerCount;
    List<Wrappers.InternalHeader> headers = new ArrayList<>();

    ExecMessage message = null;
    try {
      // message is multi-part
      // part 1. how many headers?
      buff = subscriber.recv();
      if (buff == null) {
        return null;
      }
      headerCount = Ints.fromByteArray(buff);

      // part 2. [headers]
      if (headerCount > 0) {
        for (int i = 0; i < headerCount; i++) {
          buff = subscriber.recv();
          try {
            headers.add(Wrappers.InternalHeader.parseFrom(buff));
          } catch (InvalidProtocolBufferException e) {
            logger.error("Error parsing header from byte array", e);
          }
        }
      }

      // part 3. message
      buff = subscriber.recv();

      // parse and return message
      message = ExecMessage.parseFrom(buff);
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
    } catch (InvalidProtocolBufferException e) {
      logger.error("Caught exception parsing message. Will return null", e);
    } finally {
      return message;
    }
  }

  public void close() {
    subscriber.close();
    zContext.close();
  }

  public Stream<ExecMessage> getStream() {
    return Stream.generate(this::getNext);
  }

  public long getReceivedMessagesCount() {
    return receivedMessagesCount;
  }
}
