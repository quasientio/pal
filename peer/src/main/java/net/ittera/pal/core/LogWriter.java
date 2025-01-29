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
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.util.UuidUtils;
import net.ittera.pal.messages.LogMessageHeader;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.types.InternalHeaderType;
import net.ittera.pal.messages.types.MessageFormatType;
import net.ittera.pal.messages.types.MessageType;
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

// TODO A 2nd thread that sends non-urgent messages from a queue.
@Singleton
class LogWriter extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

  // kafka stuff
  private Producer<String, byte[]> producer;
  private final Properties producerProperties = new Properties();
  private static final Duration PRODUCER_CLOSE_TIMEOUT = Duration.of(300, ChronoUnit.MILLIS);

  // used to check that messages sent to the log complete successfully
  private final ExecutorService producerCheckExecutorService = Executors.newSingleThreadExecutor();

  // zmq stuff
  private Socket subscriberSocket;
  private Socket offsetPublisherSocket;
  private final String outPubAddress;
  private final String offsetPubAddress;

  private boolean publishOffsets;
  private LogInfo outLog;
  private final AtomicInteger messagesSent = new AtomicInteger(0);
  private static final Map<String, Header> SELF_HEADERS = new HashMap<>();

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

  // Used from unit tests with MockProducer
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

  private void closeProducer() {
    if (producer != null) {
      try {
        producer.close(PRODUCER_CLOSE_TIMEOUT);
      } catch (Exception e) {
        logger.warn("Error closing producer", e);
      }
    }
  }

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
