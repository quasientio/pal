package com.ittera.cometa.core;

import com.google.protobuf.InvalidProtocolBufferException;

import com.google.common.primitives.Ints;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.messages.LogMessageHeader;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

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
class LogWriter extends AbstractExecutionThreadService {

	private static final Logger logger = LoggerFactory.getLogger(LogWriter.class);

	// kafka stuff
	private Producer<String, ExecMessage> producer;
	private final Properties producerProperties = new Properties();

	// zmq stuff
	private ZContext zmqContext;
	private Socket subscriber;
	private Socket offsetPublisher;
	private final String outPubAddress, offsetPubAddress;

	// zookeeper
	private PeerLogDirectory peerLogDirectory;

	private UUID peerUuid;

	private boolean publishOffsets;
	private LogInfo outLog, inLog;
	private volatile boolean connectionsOpen = false;
	private final AtomicInteger messagesSent = new AtomicInteger(0);
	private Header SELF_PRODUCED_HEADER, SELF_DISPATCHING_HEADER;

	@Inject
	public LogWriter(@Named("key.serializer") String keySerializer,
									 @Named("value.serializer") String valueSerializer,
									 @Named("out.pub") String outPubAddress,
									 @Named("offset.pub") String offsetPubAddress,
									 ZContext zmqContext,
									 PeerLogDirectory peerLogDirectory,
									 UUID peerUuid) {
		this.zmqContext = zmqContext;
		this.peerLogDirectory = peerLogDirectory;
		this.peerUuid = peerUuid;
		this.outPubAddress = outPubAddress;
		this.offsetPubAddress = offsetPubAddress;
		producerProperties.put("key.serializer", keySerializer);
		producerProperties.put("value.serializer", valueSerializer);
		logger.info("Initialized log message writer");
	}

	/**
	 * Used from unit tests with MockProducer
	 *
	 * @param outPubAddress
	 * @param offsetPubAddress
	 * @param zmqContext
	 * @param peerLogDirectory
	 * @param peerUuid
	 */
	LogWriter(@Named("out.pub") String outPubAddress,
						@Named("offset.pub") String offsetPubAddress,
						Producer<String, ExecMessage> producer,
						ZContext zmqContext,
						PeerLogDirectory peerLogDirectory,
						UUID peerUuid) {
		this.producer = producer;
		this.zmqContext = zmqContext;
		this.peerLogDirectory = peerLogDirectory;
		this.peerUuid = peerUuid;
		this.outPubAddress = outPubAddress;
		this.offsetPubAddress = offsetPubAddress;
		logger.info("Initialized log message writer");
	}

	private void openConnections() {

		// start subscriber
		this.subscriber = zmqContext.createSocket(SocketType.SUB);
		subscriber.bind(outPubAddress);
		subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
		logger.info("Subscriber connected");

		// start offsets publisher
		if (publishOffsets) {
			this.offsetPublisher = zmqContext.createSocket(SocketType.PUB);
			offsetPublisher.bind(offsetPubAddress);
			logger.info("Publisher connected");
		}

		// create and store immutable headers (instead of creating with every send)
		this.SELF_PRODUCED_HEADER = new LogMessageHeader("produced-by", UUIDUtils.toBytes(peerUuid));
		this.SELF_DISPATCHING_HEADER = new LogMessageHeader("dispatching-by", UUIDUtils.toBytes(peerUuid));

		connectionsOpen = true;
		logger.info("All connections open - except kafka producer");
	}

	private void closeConnections() {
		if (producer != null) {
			producer.close();
		}

		if (subscriber != null) {
			subscriber.close();
		}

		if (offsetPublisher != null) {
			offsetPublisher.close();
		}

		logger.info("All connections closed");
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
		logger.info("Will write to log: {}", outLog);
	}

	@Override
	public void run() {

		//wait for connections established
		while (!connectionsOpen) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				//what to do
			}
		}

		if (logger.isDebugEnabled()) {
			logger.debug("Starting to dispatch messages to log");
		}

		while (isRunning() && !Thread.interrupted()) {

			int headerCount;
			byte[] msg;
			List<InternalHeader> headers = new ArrayList<>();
			try {
				byte[] buff;

				// message is multi-part
				// part 1. how many headers?
				buff = subscriber.recv(ZMQ.DONTWAIT);
				if (buff == null) {
					// TODO should we sleep a bit here?
					continue;
				}
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

				// part 3. message
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

			ExecMessage execMessage = null;
			try {
				execMessage = ExecMessage.parseFrom(msg);
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			// got a message
			if (execMessage != null) {

				// send to kafka immediately
				sendToKafka(execMessage, peerUuid, fromInternalToLog(headers));
			}
		}

		closeConnections();
	}

	private Iterable<Header> fromInternalToLog(List<InternalHeader> internalHeaders) {
		List<Header> logHeaders = new ArrayList<>();
		boolean isWriteAhead = false;
		for (InternalHeader ih : internalHeaders) {
			if (ih.getHeaderType().equals(Wrappers.InternalHeaderType.WRITE_AHEAD)) {
				isWriteAhead = true;
				logHeaders.add(SELF_DISPATCHING_HEADER);
				break;
			}
		}

		if (!isWriteAhead) {
			// we don't need an InternalHeader, we assume it's self-produced
			logHeaders.add(SELF_PRODUCED_HEADER);
		}

		return logHeaders;
	}


	private void sendToKafka(ExecMessage message, UUID fromPeer, Iterable<Header> headers) {
		if (logger.isDebugEnabled()) {
			logger.debug("sending new message with uuid: {}", message.getMessageUuid());
		}

		ProducerRecord<String, ExecMessage> newRecord = new ProducerRecord<>(outLog.getName(), 0,
			fromPeer.toString(), message, headers);

		producer.send(newRecord, new MessageOffsetInformer(message, publishOffsets, offsetPublisher,
			peerLogDirectory, inLog, peerUuid));
		messagesSent.getAndIncrement();
		if (logger.isDebugEnabled()) {
			logger.debug("new message sent with uuid: {} replying to message uuid: {}", message.getMessageUuid(),
				message.getFollowingUuid());
		}
	}

	@Override
	protected void triggerShutdown() {

		logger.info("Data message writer shutting down.");
	}

	@Override
	protected void shutDown() {

		logger.info("Data message writer shut down.");
	}

	@Override
	protected void startUp() {
		openConnections();
	}
}
