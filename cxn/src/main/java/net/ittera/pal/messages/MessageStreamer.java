package net.ittera.pal.messages;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.stream.Stream;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
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
      message = Message.parseFrom(msg.getBody());
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
      logger.error("Error deserializing received message", e);
      logger.error("Caught exception parsing message, returning null.", e);
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
