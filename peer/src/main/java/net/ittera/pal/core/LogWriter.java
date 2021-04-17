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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.util.UUIDUtils;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.messages.InternalHeaderType;
import net.ittera.pal.messages.LogMessageHeader;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.colfer.InternalHeader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/** TODO A 2nd thread that sends non-urgent messages from a queue. */
@Singleton
class LogWriter extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

  // kafka stuff
  private Producer<String, byte[]> producer;
  private final Properties producerProperties = new Properties();

  // zmq stuff
  private Socket subscriber;
  private Socket offsetPublisher;
  private final String outPubAddress;
  private final String offsetPubAddress;

  private final DirectoryConnectionProvider directoryConnectionProvider;
  private boolean publishOffsets;
  private boolean writeReplyNodes;
  private LogInfo outLog;
  private LogInfo inLog;
  private final AtomicInteger messagesSent = new AtomicInteger(0);
  private final Map<String, Header> HEADERS = new HashMap<>();

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
      @Named("offset.pub") String offsetPubAddress,
      @Named("log.registerReplies") String writeReplyNodesStr,
      DirectoryConnectionProvider directoryConnectionProvider) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.outPubAddress = outPubAddress;
    this.offsetPubAddress = offsetPubAddress;
    if (directoryConnectionProvider.get().isPresent()) {
      this.writeReplyNodes = writeReplyNodesStr == null || Boolean.parseBoolean(writeReplyNodesStr);
    } else if (writeReplyNodes) {
      logger.warn("Not writing reply nodes since we are not connected to a PAL Directory");
    }
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

  /** Used from unit tests with MockProducer */
  LogWriter(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      String serviceName,
      @Named("out.pub") String outPubAddress,
      @Named("offset.pub") String offsetPubAddress,
      boolean writeReplyNodes,
      Producer<String, byte[]> producer,
      DirectoryConnectionProvider directoryConnectionProvider) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.producer = producer;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.outPubAddress = outPubAddress;
    this.offsetPubAddress = offsetPubAddress;
    if (directoryConnectionProvider.get().isPresent()) {
      this.writeReplyNodes = writeReplyNodes;
    } else if (writeReplyNodes) {
      logger.warn("Not writing reply nodes since we are not connected to a PAL Directory");
    }
    logger.info("Created log message writer");
  }

  @Override
  protected void openConnections() {
    // start subscriber
    this.subscriber = zmqContext.createSocket(SocketType.SUB);
    subscriber.connect(outPubAddress);
    subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
    // start offsets publisher
    if (publishOffsets) {
      this.offsetPublisher = zmqContext.createSocket(SocketType.PUB);
      offsetPublisher.bind(offsetPubAddress);
    }
    logger.info("connections open - except kafka producer");
    // create and store immutable headers (instead of creating with every send)
    this.HEADERS.put(
        "SELF_PRODUCED_HEADER", new LogMessageHeader("produced-by", UUIDUtils.toBytes(peerUuid)));
    this.HEADERS.put(
        "SELF_DISPATCHING_HEADER",
        new LogMessageHeader("dispatching-by", UUIDUtils.toBytes(peerUuid)));
  }

  public void writeToLog(LogInfo outLog, LogInfo inLog, boolean publishOffsets) {
    this.outLog = outLog;
    this.inLog = inLog;
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
        msg = OutboundMsg.recvMsg(subscriber, true);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Received new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
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
        // set headers
        List<Header> logHeaders = fromInternalToLog(msg.getHeaders());
        // send to kafka immediately
        sendToKafka(
            msg.getBody(), msg.getMessageUuid(), msg.getFollowingUuid(), peerUuid, logHeaders);
      }
    }
  }

  private List<Header> fromInternalToLog(List<InternalHeader> internalHeaders) {
    List<Header> logHeaders = new ArrayList<>();
    boolean isWriteAhead = false;
    if (internalHeaders != null) {
      for (InternalHeader ih : internalHeaders) {
        if (ih.getHeaderType() == InternalHeaderType.WRITE_AHEAD.ordinal()) {
          isWriteAhead = true;
          logHeaders.add(HEADERS.get("SELF_DISPATCHING_HEADER"));
          break;
        }
      }
    }
    if (!isWriteAhead) {
      // we don't need an InternalHeader, we assume it's self-produced
      logHeaders.add(HEADERS.get("SELF_PRODUCED_HEADER"));
    }
    return logHeaders;
  }

  private void sendToKafka(
      byte[] message,
      UUID messageUuid,
      UUID followingUuid,
      UUID fromPeer,
      Iterable<Header> headers) {
    if (logger.isDebugEnabled()) {
      logger.debug("sending new message with uuid: {}", messageUuid);
    }
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(outLog.getName(), 0, fromPeer.toString(), message, headers);
    if (publishOffsets || (writeReplyNodes && directoryConnectionProvider.get().isPresent())) {
      producer.send(
          newRecord,
          new MessageOffsetInformer(
              messageUuid,
              followingUuid,
              publishOffsets,
              writeReplyNodes,
              offsetPublisher,
              directoryConnectionProvider.get().orElse(null),
              inLog,
              peerUuid));
    } else {
      producer.send(newRecord);
    }
    messagesSent.getAndIncrement();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "new message sent with uuid: {} replying to message uuid: {} ({} bytes)",
          messageUuid,
          followingUuid,
          message.length);
    }
  }

  private void close(
      Producer producer, long timeout, TemporalUnit timeUnit, String msgForException) {
    if (producer != null) {
      try {
        producer.close(Duration.of(timeout, timeUnit));
      } catch (Exception e) {
        logger.warn(msgForException, e);
      }
    }
  }

  @Override
  protected void closeConnections() {
    close(producer, 300, ChronoUnit.MILLIS, "Error closing producer");
    closeConnection(subscriber, "Error closing subscriber");
    closeConnection(offsetPublisher, "Error offset publisher");
  }
}
