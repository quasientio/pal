/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.messages;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.colfer.Message;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * The MessageStreamer class manages the connection to a peer's ZeroMQ PUB interface, subscribes to
 * incoming messages, and provides a stream of {@link Message} objects for processing. It handles
 * the setup of the ZeroMQ context and subscriber socket, maintains a count of received messages,
 * and offers methods to retrieve the message stream and connection statistics.
 */
@SuppressFBWarnings(
    value = "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR",
    justification = "Fields initialized in connect() - two-phase initialization pattern")
public class MessageStreamer {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(MessageStreamer.class);

  /** The interval at which received message statistics are logged. */
  private static final int STATS_TRACE_INTERVAL = 100;

  /** The ZeroMQ context used for managing socket communication. */
  private ZContext zmqContext;

  /** The ZeroMQ subscriber socket used to receive messages from the publisher. */
  private Socket subscriber;

  /** The hostname or IP address of the message publisher to connect to. */
  private final String host;

  /** The port number of the PUB socket to connect to. */
  private final int port;

  /** The count of messages received since the connection was established. */
  private long receivedMessagesCount;

  /**
   * Constructs a new MessageStreamer with the specified host and port.
   *
   * @param host the hostname or IP address of the message publisher
   * @param port the port number of the message publisher
   */
  public MessageStreamer(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /**
   * Establishes a connection to the message publisher by creating a ZeroMQ context and subscriber
   * socket. Configures the subscriber to receive all incoming messages and connects to the
   * specified host and port.
   *
   * @return the current instance of {@code MessageStreamer} for method chaining
   */
  public MessageStreamer connect() {
    // create zmq context
    zmqContext = new ZContext();
    zmqContext.setLinger(1000);
    zmqContext.setRcvHWM(1000);

    // init SUB socket and subscribe
    subscriber = zmqContext.createSocket(SocketType.SUB);
    final String publishingEndpoint = String.format("tcp://%s:%d", host, port);
    subscriber.connect(publishingEndpoint);
    subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
    if (logger.isDebugEnabled()) {
      logger.debug("Connected and subscribed to {}", publishingEndpoint);
    }
    return this;
  }

  /**
   * Retrieves the next {@link Message} from the subscriber socket. This method blocks until a
   * message is received or an interruption occurs.
   *
   * @return the next {@link Message} received, or {@code null} if the operation was interrupted or
   *     an error occurred
   */
  private Message getNext() {

    Message message = null;
    try {
      OutboundMsg msg = OutboundMsg.receive(subscriber, true);
      if (msg == null) {
        return null;
      }
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

  /**
   * Closes the subscriber socket and terminates the ZeroMQ context, releasing all associated
   * resources.
   *
   * <p>This method is idempotent and safe to call multiple times. It is also safe to call before
   * {@link #connect()} has been called, in which case it is a no-op.
   */
  public void close() {
    if (subscriber != null) {
      subscriber.close();
    }
    if (zmqContext != null) {
      zmqContext.close();
    }
  }

  /**
   * Provides a sequential {@link Stream} of {@link Message} objects generated from incoming
   * messages.
   *
   * @return a {@link Stream} that continuously generates {@link Message} instances as they are
   *     received
   */
  public Stream<Message> getStream() {
    return Stream.generate(this::getNext);
  }

  /**
   * Retrieves the total number of messages received since the connection was established.
   *
   * @return the count of received messages
   */
  public long getReceivedMessagesCount() {
    return receivedMessagesCount;
  }
}
