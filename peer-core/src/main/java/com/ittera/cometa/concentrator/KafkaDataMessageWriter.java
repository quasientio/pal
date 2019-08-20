package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.messages.LogMessageHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Singleton;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
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
public class KafkaDataMessageWriter extends AbstractExecutionThreadService {

	protected static final Logger logger = LoggerFactory.getLogger(KafkaDataMessageWriter.class);

	// kafka stuff
	private KafkaProducer producer;
	private final Properties producerProperties = new Properties();

	// zmq stuff
	@Inject
	private ZContext zmqContext;
	private Socket subscriber;
	private Socket offsetPublisher;
	private final String outPubAddress, offsetPubAddress;

	// zookeeper
	@Inject
	private PeerLogDirectory peerLogDirectory;

	@Inject
	private UUID peerUuid;

	private boolean publishOffsets;
	private LogInfo outLog, inLog;
	private volatile boolean connectionsOpen = false;
	private final AtomicInteger messagesSent = new AtomicInteger(0);
	private Iterable<Header> SELF_PRODUCED_HEADERS;
	private Iterable<Header> SELF_DISPATCHING_HEADERS;

	@Inject
	public KafkaDataMessageWriter(@Named("key.serializer") String keySerializer,
																@Named("value.serializer") String valueSerializer,
																@Named("out.pub") String outPubAddress,
																@Named("offset.pub") String offsetPubAddress) {
		this.outPubAddress = outPubAddress;
		this.offsetPubAddress = offsetPubAddress;
		producerProperties.put("key.serializer", keySerializer);
		producerProperties.put("value.serializer", valueSerializer);
		logger.info("Initialized kafka message writer");
	}

	public void openConnections() {

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

		connectionsOpen = true;
		logger.info("All connections open - except kafka producer");
	}

	protected void closeConnections() {
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

		// create and store headers (instead of creating with every send)
		this.SELF_PRODUCED_HEADERS = Arrays.asList(new LogMessageHeader("produced-by", peerUuid.toString().toUpperCase()));
		this.SELF_DISPATCHING_HEADERS = Arrays.asList(new LogMessageHeader("dispatching-by", peerUuid.toString().toUpperCase()));

		// start kafka writer
		this.producer = new KafkaProducer<>(producerProperties);
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
			logger.debug("Starting to dispatch messages to kafka");
		}

		while (isRunning() && !Thread.interrupted()) {

			byte[] msg;
			try {
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

			DataMessage dataMessage = null;
			try {
				dataMessage = DataMessage.parseFrom(msg);
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			// got a message
			if (dataMessage != null) {

				// send to kafka immediately
				sendToKafka(dataMessage, peerUuid);
			}
		}

		closeConnections();
	}

	private void sendToKafka(DataMessage message, UUID fromPeer) {
		if (logger.isDebugEnabled()) {
			logger.debug("sending new message with uuid: {}", message.getMessageUuid());
		}

		ProducerRecord<String, DataMessage> newRecord = new ProducerRecord<String, DataMessage>(outLog.getName(), 0,
			fromPeer.toString(), message, this.SELF_PRODUCED_HEADERS);

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
