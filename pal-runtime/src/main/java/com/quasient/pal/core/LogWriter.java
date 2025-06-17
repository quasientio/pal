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

package com.quasient.pal.core;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.util.UuidUtils;
import com.quasient.pal.messages.LogMessageHeader;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.types.InternalHeaderType;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * LogWriter is responsible for retrieving messages from a ZeroMQ subscriber socket, transforming
 * header information if needed, and publishing the messages to a Kafka Log. It also optionally
 * publishes message offset information via a ZeroMQ publisher socket.
 *
 * <p>The class manages its own connections to ZeroMQ and Kafka, performing asynchronous checks on
 * message delivery and maintaining internal counters for sent messages.
 */
@Singleton
// TODO A 2nd thread that sends non-urgent messages from a queue.
class LogWriter extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

  /** Kafka producer used to send Log messages to a Kafka topic. */
  private Producer<String, byte[]> producer;

  /** Properties used to configure the Kafka producer. */
  private final Properties producerProperties = new Properties();

  /** Timeout duration for closing the Kafka producer. */
  private static final Duration PRODUCER_CLOSE_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);

  /**
   * Executor service used to asynchronously verify that messages are successfully sent to Kafka.
   */
  private final ExecutorService producerCheckExecutorService = Executors.newSingleThreadExecutor();

  /** ZeroMQ subscriber socket used to receive outbound messages. */
  private Socket subscriberSocket;

  /** ZeroMQ publisher socket used to publish message offsets when enabled. */
  private Socket offsetPublisherSocket;

  /** ZeroMQ address to connect the subscriber socket for receiving outgoing messages. */
  private final String outPubAddress;

  /** ZeroMQ address to bind the offset publisher socket for delivering offset updates. */
  private final String offsetPubAddress;

  /** Flag indicating whether message offsets should be published. */
  private boolean publishOffsets;

  /** Information describing the Log to which messages are written. */
  private LogInfo outLog;

  /** Counter tracking the number of messages successfully sent to the Log. */
  private final AtomicInteger messagesSent = new AtomicInteger(0);

  /** Immutable headers used for marking messages as self-produced or dispatched. */
  private static final Map<String, Header> SELF_HEADERS = new HashMap<>();

  /**
   * Constructs a new LogWriter instance with the required dependencies and configuration.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this Log writer service.
   * @param keySerializer configuration for the Kafka key serializer.
   * @param valueSerializer configuration for the Kafka value serializer.
   * @param outPubAddress ZeroMQ address for the outbound publisher connection.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   */
  @Inject
  public LogWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("LogWriter.service") String serviceName,
      @Named("key.serializer") String keySerializer,
      @Named("value.serializer") String valueSerializer,
      @Named("out.pub") String outPubAddress,
      @Named("offset.pub") String offsetPubAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.outPubAddress = outPubAddress;
    this.offsetPubAddress = offsetPubAddress;
    producerProperties.put("key.serializer", keySerializer);
    producerProperties.put("value.serializer", valueSerializer);
    StringBuilder propsStr = new StringBuilder();
    for (String propKey : producerProperties.stringPropertyNames()) {
      propsStr
          .append(propKey)
          .append('=')
          .append(producerProperties.getProperty(propKey))
          .append(", ");
    }
    logger.info(
        "Created log message writer for peer with id '{}' and properties: [{}]",
        peerUuid,
        propsStr);
  }

  /**
   * Constructs a LogWriter instance using a provided Kafka producer instance. This constructor is
   * primarily intended for use in unit tests with a mock producer.
   *
   * @param peerUuid unique identifier for this peer.
   * @param context ZeroMQ context for creating and managing socket connections.
   * @param syncSocketAddress address used for synchronizing service startup.
   * @param serviceThreadGroup thread group in which the service thread will be executed.
   * @param serviceName logical name that identifies this log writer service.
   * @param outPubAddress ZeroMQ address for the outbound publisher connection.
   * @param offsetPubAddress ZeroMQ address for the message offset publisher connection.
   * @param producer Kafka producer instance to be used for sending messages.
   */
  LogWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      @Named("out.pub") String outPubAddress,
      @Named("offset.pub") String offsetPubAddress,
      Producer<String, byte[]> producer) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.producer = producer;
    this.outPubAddress = outPubAddress;
    this.offsetPubAddress = offsetPubAddress;
    logger.info("Created log message writer");
  }

  /**
   * Opens ZeroMQ connections for the subscriber and (optionally) for the offset publisher.
   * Additionally, initializes immutable header entries used for identifying message origins.
   */
  @Override
  protected void openConnections() {

    // start subscriber
    this.subscriberSocket = zmqContext.createSocket(SocketType.SUB);
    subscriberSocket.connect(outPubAddress);
    subscriberSocket.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // start offsets publisher
    if (publishOffsets) {
      this.offsetPublisherSocket = zmqContext.createSocket(SocketType.PUB);
      offsetPublisherSocket.bind(offsetPubAddress);
    }
    logger.info("connections open - except kafka producer");

    // create and store immutable headers (instead of creating with every send)
    SELF_HEADERS.put(
        "SELF_PRODUCED_HEADER", new LogMessageHeader("producer-id", UuidUtils.toBytes(peerUuid)));
    SELF_HEADERS.put(
        "SELF_DISPATCHING_HEADER",
        new LogMessageHeader("dispatcher-id", UuidUtils.toBytes(peerUuid)));
  }

  /**
   * Configures the Log writer with the designated Log information and offset publishing preference.
   * Sets Kafka producer properties based on the provided Log details and creates the producer if
   * necessary.
   *
   * @param outLog log information containing details such as the Log name and bootstrap servers.
   * @param publishOffsets flag indicating whether message offsets should be published via ZeroMQ.
   */
  public void writeToLog(LogInfo outLog, boolean publishOffsets) {
    this.outLog = outLog;
    this.publishOffsets = publishOffsets;
    producerProperties.put("bootstrap.servers", outLog.getBootstrapServers());

    // create producer, if not assigned in constructor
    if (this.producer == null) {
      this.producer = new KafkaProducer<>(producerProperties);
    }
    logger.info(
        "Writing to log: {}, w/ bootstrapServers: {}",
        outLog.getName(),
        outLog.getBootstrapServers());
  }

  /**
   * Continuously receives messages from the ZeroMQ subscriber socket and dispatches them to Kafka.
   * The method processes messages until the thread is interrupted or a socket error occurs.
   */
  @Override
  public void run() {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting to dispatch messages to log");
    }
    boolean socketError = false;
    while (!Thread.interrupted() && !socketError) {
      OutboundMsg msg = null;
      try {
        msg = OutboundMsg.receive(subscriberSocket, true);
        assert msg != null;
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Received new message w/id: {} ({} bytes)", msg.getMessageId(), msg.getSize());
        }
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
        logger.error("Error parsing received message", e);
      }
      if (msg != null) {
        final List<Header> logHeaders = fromInternalToLog(msg.getHeaders());
        sendToKafka(
            MessageFormatType.BINARY,
            msg.getMessageType(),
            msg.getBody(),
            msg.getMessageId(),
            msg.getResponseToId(),
            peerUuid,
            logHeaders);
      }
    }
  }

  /**
   * Converts internal header representations into headers suitable for the Log message (i.e. Kafka
   * record). The conversion checks if any header indicates a write-ahead and accordingly selects
   * the corresponding self-header.
   *
   * @param internalHeaders list of internal headers to be converted; may be null.
   * @return a list of headers appropriate for the Kafka Log message.
   */
  private List<Header> fromInternalToLog(@Nullable List<InternalHeader> internalHeaders) {
    if (logger.isDebugEnabled()) {
      StringBuilder logHeadersStr = new StringBuilder();
      if (internalHeaders != null) {
        for (InternalHeader ih : internalHeaders) {
          logHeadersStr
              .append("InternalHeader [type = ")
              .append(InternalHeaderType.fromByte(ih.getHeaderType()).name())
              .append(", value = ")
              .append(ih.getValue())
              .append("]")
              .append("\n");
        }
      }
      // remove the last \n
      if (!logHeadersStr.isEmpty()) {
        logHeadersStr.setLength(logHeadersStr.length() - 1);
      }
      logger.debug("Converting internal headers to log headers: {}", logHeadersStr);
    }
    List<Header> logHeaders = new ArrayList<>();
    boolean isWriteAhead = false;
    if (internalHeaders != null) {
      for (InternalHeader ih : internalHeaders) {
        if (ih.getHeaderType() == InternalHeaderType.WRITE_AHEAD.toByte()) {
          isWriteAhead = true;
          logHeaders.add(SELF_HEADERS.get("SELF_DISPATCHING_HEADER"));
          break;
        }
      }
    }
    if (!isWriteAhead) { // if not write-ahead, we assume it's self-produced
      logHeaders.add(SELF_HEADERS.get("SELF_PRODUCED_HEADER"));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Returning log headers: {}", logHeaders);
    }
    return logHeaders;
  }

  /**
   * Sends a message to the Kafka Log. Constructs a ProducerRecord with the provided message data,
   * appends necessary headers, and dispatches it asynchronously using the Kafka producer. An
   * executor service monitors the send operation and logs confirmation or errors.
   *
   * @param messageFormat the format of the message payload.
   * @param messageType the type identifier for the message.
   * @param message the byte array payload of the message.
   * @param messageId unique identifier for the message.
   * @param responseId identifier of the message to which this is a response.
   * @param fromPeer UUID of this peer.
   * @param headers optional iterable of additional headers; may be null.
   */
  private void sendToKafka(
      MessageFormatType messageFormat,
      MessageType messageType,
      byte[] message,
      String messageId,
      String responseId,
      UUID fromPeer,
      @Nullable Iterable<Header> headers) {
    if (logger.isDebugEnabled()) {
      logger.debug("sending new message to kafka log with id: {}", messageId);
    }
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(outLog.getName(), 0, fromPeer.toString(), message, headers);

    // add message description headers
    newRecord.headers().add("message-format", new byte[] {messageFormat.toByte()});
    newRecord.headers().add("message-type", new byte[] {messageType.getId()});

    // send the message
    Future<RecordMetadata> sendFuture;
    if (publishOffsets) {
      sendFuture =
          producer.send(newRecord, new MessageOffsetInformer(messageId, offsetPublisherSocket));
    } else {
      sendFuture = producer.send(newRecord);
    }

    producerCheckExecutorService.execute(
        () -> {
          try {
            RecordMetadata sentRecordMetadata = sendFuture.get();
            messagesSent.getAndIncrement();
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "new message written to log at offset: {}, w/id: {},"
                      + " in response to message w/id: {} ({} bytes)",
                  sentRecordMetadata.offset(),
                  messageId,
                  responseId,
                  message.length);
            }
          } catch (Exception e) {
            logger.error("Error sending message to log", e);
          }
        });
  }

  /**
   * Closes the Kafka producer using a predefined timeout to ensure graceful shutdown. Any exception
   * encountered during closure is logged as a warning.
   */
  private void closeProducer() {
    if (producer != null) {
      try {
        producer.close(PRODUCER_CLOSE_TIMEOUT);
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
  }

  /**
   * Closes all open connections and resources used by the LogWriter. This includes shutting down
   * the executor service, closing the Kafka producer, and closing the ZeroMQ sockets.
   */
  @Override
  protected void closeConnections() {
    // stop the producer async executor
    producerCheckExecutorService.shutdown();
    try {
      if (!producerCheckExecutorService.awaitTermination(250, TimeUnit.MILLISECONDS)) {
        producerCheckExecutorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      producerCheckExecutorService.shutdownNow();
    }
    // close the producer
    closeProducer();

    // close the zmq sockets
    closeConnection(subscriberSocket, "Error closing subscriber");
    closeConnection(offsetPublisherSocket, "Error closing offset publisher");
  }
}
