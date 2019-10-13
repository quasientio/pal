package com.ittera.cometa.core;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.LogMessageHeader;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;

import javax.inject.Named;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * TODO A 2nd thread that sends non-urgent messages from a queue.
 */

@Singleton
class LogWriter extends ConnectedService {

	private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

	// kafka stuff
	private Producer<String, byte[]> producer;
	private final Properties producerProperties = new Properties();

	// zmq stuff
	private Socket subscriber;
	private Socket offsetPublisher;
	private final String outPubAddress, offsetPubAddress;

	private PALDirectory palDirectory;
	private boolean publishOffsets;
	private boolean writeReplyNodes;
	private LogInfo outLog, inLog;
	private final AtomicInteger messagesSent = new AtomicInteger(0);
	private final Map<String, Header> HEADERS = new HashMap<>();

	@Inject
	public LogWriter(UUID peerUuid,
									 ZContext context,
									 @Named("sync.ready") String syncSocketAddress,
									 ThreadGroup serviceThreadGroup,
									 @Named("LogWriter.service") String serviceName,
									 @Named("key.serializer") String keySerializer,
									 @Named("value.serializer") String valueSerializer,
									 @Named("out.pub") String outPubAddress,
									 @Named("offset.pub") String offsetPubAddress,
									 @Named("log.registerReplies") String writeReplyNodesStr,
									 PALDirectory palDirectory) {
		super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
		this.palDirectory = palDirectory;
		this.outPubAddress = outPubAddress;
		this.offsetPubAddress = offsetPubAddress;
		this.writeReplyNodes = writeReplyNodesStr == null || Boolean.parseBoolean(writeReplyNodesStr);
		producerProperties.put("key.serializer", keySerializer);
		producerProperties.put("value.serializer", valueSerializer);
		StringBuilder propsStr = new StringBuilder();
		for (String propKey : producerProperties.stringPropertyNames()) {
			propsStr.append(propKey).append('=').append(producerProperties.getProperty(propKey)).append(", ");
		}
		logger.info("Created log message writer for peer with id '{}' and properties: [{}]", peerUuid, propsStr.toString());
	}

	/**
	 * Used from unit tests with MockProducer
	 */
	LogWriter(UUID peerUuid,
						ZContext context,
						@Named("sync.ready") String syncSocketAddress,
						ThreadGroup serviceThreadGroup,
						String serviceName,
						@Named("out.pub") String outPubAddress,
						@Named("offset.pub") String offsetPubAddress,
						boolean writeReplyNodes,
						Producer<String, byte[]> producer,
						PALDirectory palDirectory) {
		super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
		this.producer = producer;
		this.palDirectory = palDirectory;
		this.outPubAddress = outPubAddress;
		this.offsetPubAddress = offsetPubAddress;
		this.writeReplyNodes = writeReplyNodes;
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
		this.HEADERS.put("SELF_PRODUCED_HEADER", new LogMessageHeader("produced-by", UUIDUtils.toBytes(peerUuid)));
		this.HEADERS.put("SELF_DISPATCHING_HEADER", new LogMessageHeader("dispatching-by", UUIDUtils.toBytes(peerUuid)));
		this.HEADERS.put("EXEC_MSG_TYPE_HEADER",
			new LogMessageHeader("type", Ints.toByteArray(MessageType.ExecMessage.ordinal())));
		this.HEADERS.put("INTERCEPT_MSG_TYPE_HEADER",
			new LogMessageHeader("type", Ints.toByteArray(MessageType.InterceptRequest.ordinal())));
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
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled()) {
			logger.debug("Starting to dispatch messages to log");
		}
		while (!Thread.interrupted()) {
			int headerCount;
			byte[] msg;
			UUID msgUuid;
			UUID followingUuid = null;
			List<InternalHeader> headers = new ArrayList<>();
			MessageType messageType;
			try {
				byte[] buff;
				/* MULTI-PART message */
				// part 0. get type of message to follow
				buff = subscriber.recv(ZMQ.DONTWAIT);
				if (buff == null) {
					continue;
				}
				messageType = MessageType.values[Ints.fromByteArray(buff)];
				// part 1. how many headers?
				buff = subscriber.recv(ZMQ.DONTWAIT);
				headerCount = Ints.fromByteArray(buff);
				if (logger.isDebugEnabled()) {
					logger.debug("Receiving new message with {} header(s)", headerCount);
				}
				// part 2. [headers]
				if (headerCount > 0) {
					for (int i = 0; i < headerCount; i++) {
						buff = subscriber.recv();
						try {
							headers.add(InternalHeader.parseFrom(buff));
						} catch (InvalidProtocolBufferException e) {
							logger.error("Error parsing internal header from byte array", e);
						}
					}
				}
				// part 3. message uuid
				buff = subscriber.recv();
				msgUuid = UUIDUtils.fromBytes(buff);
				// part 4. followingUuid
				buff = subscriber.recv();
				if (Ints.fromByteArray(buff) != 0) {
					followingUuid = UUIDUtils.fromBytes(buff);
				}
				// part 5. message
				msg = subscriber.recv();
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
			}
			// set headers
			List<Header> logHeaders = fromInternalToLog(headers);
			if (messageType.equals(MessageType.ExecMessage)) {
				logHeaders.add(HEADERS.get("EXEC_MSG_TYPE_HEADER"));
			} else {
				logHeaders.add(HEADERS.get("INTERCEPT_MSG_TYPE_HEADER"));
			}
			// send to kafka immediately
			sendToKafka(msg, msgUuid, followingUuid, peerUuid, logHeaders);
		}
	}

	private List<Header> fromInternalToLog(List<InternalHeader> internalHeaders) {
		List<Header> logHeaders = new ArrayList<>();
		boolean isWriteAhead = false;
		for (InternalHeader ih : internalHeaders) {
			if (ih.getHeaderType().equals(Wrappers.InternalHeaderType.WRITE_AHEAD)) {
				isWriteAhead = true;
				logHeaders.add(HEADERS.get("SELF_DISPATCHING_HEADER"));
				break;
			}
		}
		if (!isWriteAhead) {
			// we don't need an InternalHeader, we assume it's self-produced
			logHeaders.add(HEADERS.get("SELF_PRODUCED_HEADER"));
		}
		return logHeaders;
	}


	private void sendToKafka(byte[] message, UUID messageUuid, UUID followingUuid, UUID fromPeer,
													 Iterable<Header> headers) {
		if (logger.isDebugEnabled()) {
			logger.debug("sending new message with uuid: {}", messageUuid);
		}
		ProducerRecord<String, byte[]> newRecord = new ProducerRecord<>(outLog.getName(), 0,
			fromPeer.toString(), message, headers);
		if (publishOffsets || writeReplyNodes) {
			producer.send(newRecord, new MessageOffsetInformer(messageUuid, followingUuid, publishOffsets, writeReplyNodes,
				offsetPublisher, palDirectory, inLog, peerUuid));
		} else {
			producer.send(newRecord);
		}
		messagesSent.getAndIncrement();
		if (logger.isDebugEnabled()) {
			logger.debug("new message sent with uuid: {} replying to message uuid: {} ({} bytes)",
				messageUuid, followingUuid, message.length);
		}
	}

	@Override
	protected void closeConnections() {
		if (producer != null) {
			try {
				producer.close();
			} catch (Exception e) {
				logger.debug("Error closing producer", e);
			}
		}
		if (subscriber != null) {
			try {
				subscriber.close();
			} catch (Exception e) {
				logger.debug("Error closing subscriber", e);
			}
		}
		if (offsetPublisher != null) {
			try {
				offsetPublisher.close();
			} catch (Exception e) {
				logger.debug("Error offset publisher", e);
			}
		}
	}
}
