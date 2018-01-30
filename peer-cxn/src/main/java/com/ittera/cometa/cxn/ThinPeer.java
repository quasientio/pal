package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogRequest;
import com.ittera.cometa.PeerInfo;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers;

import org.apache.commons.lang3.StringUtils;

import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.KeeperException.Code;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.io.InputStream;

import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * This class is not thread-safe. For multi-threaded scenarios, use different instances.
 */
public class ThinPeer {

	private UUID peerUuid = UUID.randomUUID();

	private boolean allowP2P;

	// static
	protected final static Logger logger = LoggerFactory.getLogger(ThinPeer.class);
	protected final DataMessageBuilder dataMessageBuilder;

	// kafka stuff
	private LogInfo inLog, outLog;
	private final String kafkaTopicPrefix;
	private final TopicPartition inTopicPartition;
	private final Long pollTimeout;

	private final Properties kafkaProducerProps = new Properties();
	private final KafkaProducer producer;
	private final KafkaConsumer<String, String> consumer;
	private final Properties kafkaConsumerProps = new Properties();

	private Map<Long, ConsumerRecord> lastRecordsRead = new HashMap();
	private final ExecutorService singleThreadConsumerExecutor = Executors.newSingleThreadExecutor();

	// zmq stuff
	private final ZContext zmqContext;
	private final Socket peerSocket;
	private PeerInfo currentPeer;
	private boolean talkingToPeer = false;

	// zookeeper
	private PeerLogDirectory peerLogDirectory;

	public ThinPeer(String propertiesFile, boolean allowP2P) throws Exception {
		this(propertiesFile, allowP2P, null, null);
	}

	public ThinPeer(String propertiesFile) throws Exception {
		this(propertiesFile, true, null, null);
	}

	public ThinPeer(String propertiesFile, LogInfo logInfo) throws Exception {
		this(propertiesFile, true, null, logInfo);
	}

	public ThinPeer(String propertiesFile, boolean allowP2P, PeerInfo initialPeer, LogInfo inLog, LogInfo outLog) throws Exception {
		logger.info("Initializing ThinPeer with props from: {}, allowP2P: {}, initialPeer: {}, inLog: {}, outLog: {}",
			propertiesFile, allowP2P, initialPeer, inLog, outLog);

		this.allowP2P = allowP2P;
		this.inLog = inLog;
		this.outLog = outLog;
		currentPeer = initialPeer;

		//load properties
		final Properties properties = new Properties();
		try (final InputStream stream = ThinPeer.class.getResourceAsStream(propertiesFile)) {
			properties.load(stream);
		}

		kafkaTopicPrefix = properties.getProperty("kafkaTopicPrefix");
		pollTimeout = Long.parseLong(properties.getProperty("pollTimeout"));

		// connect to log and peer directory
		peerLogDirectory = ZkClient.getConnectedClient(properties.getProperty("zookeeper.url"));
		try {
			// register self as new peer
			final Properties peerProperties = new Properties();
			peerLogDirectory.registerPeer(peerUuid, peerProperties);
		} catch (Exception ex) {
			logger.error("Error registering peer", ex);
		}

		// get/set log(s) to connect to
		LogInfo lastLog = null;
		if (inLog == null) {
			// get last log with prefix = kafkaTopic
			lastLog = peerLogDirectory.getLastLog(kafkaTopicPrefix);
			this.inLog = lastLog;
		}

		if (outLog == null) {
			if (lastLog == null) {
				lastLog = peerLogDirectory.getLastLog(kafkaTopicPrefix);
			}
			this.outLog = lastLog;
		}

		logger.info("Will read from log: {} and write to log: {}", this.inLog, this.outLog);


		/** Configure and Initialize Kafka Producer **/
		for (String propKey : properties.stringPropertyNames()) {
			if (propKey.startsWith("kafka.producer.")) {
				kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."),
					properties.getProperty(propKey));
			} else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.consumer")) {
				kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."),
					properties.getProperty(propKey));
			}
		}

		kafkaProducerProps.put("client.id", peerUuid.toString());
		kafkaProducerProps.put("bootstrap.servers", this.outLog.getBootstrapServers());
		producer = new KafkaProducer<>(kafkaProducerProps);
		logger.info("Kafka producer initialized. Will connect to bootstrap servers: {}", this.outLog.getBootstrapServers());

		/** Configure and Initialize Kafka Consumer **/
		for (String propKey : properties.stringPropertyNames()) {
			if (propKey.startsWith("kafka.consumer.")) {
				kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka.consumer."),
					properties.getProperty(propKey));
			} else if (propKey.startsWith("kafka.") && !propKey.startsWith("kafka.producer")) {
				kafkaConsumerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
			}
		}

		kafkaConsumerProps.put("group.id", peerUuid.toString());
		kafkaConsumerProps.put("bootstrap.servers", this.inLog.getBootstrapServers());
		consumer = new KafkaConsumer<>(kafkaConsumerProps);
		logger.info("Kafka consumer initialized. Will connect to bootstrap servers: {}", this.inLog.getBootstrapServers());

		//manual assignment of partition so we can control offset seek
		inTopicPartition = new TopicPartition(this.inLog.getName(), 0);
		consumer.assign(Arrays.asList(inTopicPartition));

		// create zmq context
		logger.info("Initializing zmq context");
		zmqContext = new ZContext();
		peerSocket = zmqContext.createSocket(ZMQ.REQ);
		if (currentPeer != null) {
			connectToPeer(currentPeer);
		}

		//init msg builder
		dataMessageBuilder = new ProtobufDataMessageBuilder();
	}

	public ThinPeer(String propertiesFile, boolean allowP2P, PeerInfo initialPeer, LogInfo logInfo) throws Exception {
		this(propertiesFile, allowP2P, initialPeer, logInfo, logInfo);
	}

	public ThinPeer(String propertiesFile, LogInfo inLog, LogInfo outLog) throws Exception {
		this(propertiesFile, true, null, inLog, outLog);
	}

	private void connectSocket() {
		peerSocket.setIdentity(("Dual-Peer-" + peerUuid.toString()).getBytes(ZMQ.CHARSET));
		peerSocket.connect(currentPeer.getListenAddress());
	}

	public DataMessage sendAndReceive(DataMessage message) throws ExecutionException, InterruptedException {
		if (allowP2P && talkingToPeer) {
			return sendToPeer(message);
		} else {
			return sendToLogAndReceive(message);
		}
	}

	public DataMessage waitFor(Wrappers.Type type, String fieldName) {
		logger.debug("Starting wait for type: {} and field name: {}", type, fieldName);
		DataMessage reply = null;
		// TODO extra param to seek before
		//consumer.seek(inTopicPartition, sentRecordOffset);

		while (true) {
			ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
			for (ConsumerRecord record : records) {
				final DataMessage dataMessage = (DataMessage) record.value();
				long receivedMsgOffset = record.offset();

				if (dataMessage.hasStaticFieldPutDone() &&
					fieldName.equals(dataMessage.getStaticFieldPutDone().getField().getName())) {
					logger.debug("Got matching message with offset {}:\n{}", receivedMsgOffset, dataMessage);
					return dataMessage;
				} else {
					logger.debug("Skipping record with offset {}", receivedMsgOffset);
				}
			}
		}
	}

	public DataMessage getMessageAtOffset(Long seek) {
		return getMessageAtOffset(seek, true);
	}

	public DataMessage getMessageAtOffset(Long seek, boolean lookupCached) {

		logger.debug("Getting message @ offset #{}, lookupCached = {}", seek, lookupCached);
		consumer.seek(inTopicPartition, seek);

		if (lookupCached) {
			DataMessage cachedMsg = getCachedMessageAtOffset(seek);
			if (cachedMsg != null) {
				logger.debug("Got cached record at offset {}", seek);
				return cachedMsg;
			}
		}

		Map recordsRead = new HashMap<Long, ConsumerRecord>();
		ConsumerRecord requestedRecord = null;

		while (requestedRecord == null) {
			ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
			logger.debug("Read {} records during poll", records.count());
			for (ConsumerRecord record : records) {
				if (seek == record.offset()) {
					requestedRecord = record;
				}
				recordsRead.put(record.offset(), record);
			}
		}

		// now swap last batch (map) of records read with the new one
		this.lastRecordsRead = recordsRead;

		return (DataMessage) requestedRecord.value();
	}

	private DataMessage getCachedMessageAtOffset(Long offset) {
		ConsumerRecord cached = lastRecordsRead.get(offset);
		if (cached != null) {
			return (DataMessage) cached.value();
		}
		return null;
	}

	public List<ConsumerRecord> getMessages(long startOffset, long numMessages) {

		logger.debug("Getting {} messages starting @ offset #{}", numMessages, startOffset);
		consumer.seek(inTopicPartition, startOffset);
		List<ConsumerRecord> messages = new ArrayList();
		boolean gotAllMessages = false;

		while (!gotAllMessages) {
			ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
			logger.debug("got {} records after poll", records.count());
			for (ConsumerRecord record : records) {
				if (record.offset() < startOffset + numMessages) {
					messages.add(record);
					gotAllMessages = messages.size() == numMessages;
				}
			}
		}

		return messages;
	}

	public UUID getPeerUuid() {
		return peerUuid;
	}

	public void sendToLogAndForget(DataMessage message) {

		// send to kafka
		producer.send(new ProducerRecord(outLog.getName(), message.getMessageUuid(), message));
		logger.debug("Message sent to log, and we're done:\n{}", message);
	}

	public Future<DataMessage> sendToLogAsync(DataMessage message) {

		final UUID requestMsgUuid = UUID.fromString(message.getMessageUuid());

		// send to kafka
		producer.send(new ProducerRecord(outLog.getName(), message.getMessageUuid(), message));
		logger.debug("Message sent to log:\n{}", message);

		final DataMessageFuture messageFuture = new DataMessageFuture(this, peerLogDirectory,
			singleThreadConsumerExecutor, outLog.getName(), new LogRequest(requestMsgUuid));

		// addLogRequest callback
		StringCallback addLogRequestCallback = (rc, path, ctx, name) -> {
			switch (Code.get(rc)) {
				case OK:
					// set watch to get notified about changes to children
					((ZkClient) peerLogDirectory).getChildren(outLog.getName(), requestMsgUuid, messageFuture, messageFuture,
						null);
					break;
				default:
					logger.error("Not OK adding log request for {}, error code: {}", requestMsgUuid, rc);
					return;
			}
		};

		// asynchronously create req node in zk
		LogRequest logRequest = null;
		if (!outLog.equals(inLog)) {
			// if we are reading from a different log, ask for reply to be written to that log (our inLog)
			logRequest = new LogRequest(requestMsgUuid, inLog);
		} else {
			logRequest = new LogRequest(requestMsgUuid);
		}

		try {
			((ZkClient) peerLogDirectory).addLogRequest(outLog.getName(), logRequest, addLogRequestCallback, null);
		} catch (Exception e) {
			logger.error("Couldn't add request node to directory", e);
			return null;
		}

		return messageFuture;
	}

	private DataMessage sendToLogAndReceive(DataMessage message) throws ExecutionException, InterruptedException {
		return sendToLogAndReceive(message, false);
	}

	private DataMessage sendToLogAndReceive(DataMessage message, boolean consumeLogUntilReply)
		throws ExecutionException, InterruptedException {

		if (!allowP2P || consumeLogUntilReply) {
			return sendAndReceiveConsumingLog(message);
		}

		// default behavior (consumeLogUntilReply=false) is to wait for Future reply on directory
		return sendAsyncAndSwitchToPeer(message);
	}

	private DataMessage sendAsyncAndSwitchToPeer(DataMessage message) throws ExecutionException, InterruptedException {

		Future<DataMessage> replyFuture = sendToLogAsync(message);

		// wait for reply (blocking)
		DataMessage replyMsg = replyFuture.get();

		// switch to direct p2p talk
		String concentratorUuid = replyMsg.getConcentratorUuid();
		connectToPeer(UUID.fromString(concentratorUuid));

		return replyMsg;
	}

	private DataMessage sendAndReceiveConsumingLog(DataMessage message) {

		//send to kafka
		Long sentRecordOffset;
		Future<RecordMetadata> recordMetadataFuture =
			producer.send(new ProducerRecord(outLog.getName(), message.getMessageUuid(), message));
		try {
			RecordMetadata recordMetadata = recordMetadataFuture.get();
			if (logger.isDebugEnabled()) {
				logger.debug("Message sent with uuid: {} \n{}", message.getMessageUuid(), getRecordInfo(recordMetadata));
			}
			sentRecordOffset = recordMetadata.offset();
		} catch (Exception e) {
			logger.error("Error getting sent record metadata", e);
			return null;
		}

		//now poll to consume
		logger.debug("Consumer seeking to offset: {}", sentRecordOffset);
		consumer.seek(inTopicPartition, sentRecordOffset);

		//wait for the reply to the sent message (reply should contain following = sentRecordOffset in message)
		while (true) {
			ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
			if (records.count() != 0) {
				logger.debug("Received {} records", records.count());
			}
			for (ConsumerRecord record : records) {
				final DataMessage dataMessage = (DataMessage) record.value();
				long receivedMsgOffset = record.offset();
				if (dataMessage.hasFollowingUuid() && message.getMessageUuid().equals(dataMessage.getFollowingUuid())) {
					logger.debug("Got reply with offset {} and uuid {} ", receivedMsgOffset, dataMessage.getMessageUuid());
					// try switching to direct peer talk (i.e. p2p)
					if (allowP2P) {
						UUID concentratorUuid = UUID.fromString(dataMessage.getConcentratorUuid());
						PeerInfo newPeer = null;
						try {
							// we getPeerProperties and close after since we assume we'll get here only once
							newPeer = peerLogDirectory.getPeerInfo(concentratorUuid);
						} catch (Exception ex) {
							logger.error("Couldn't get peer properties", ex);
						}
						if (newPeer != null && !newPeer.equals(currentPeer)) {
							connectToPeer(newPeer);
						}
					}

					return dataMessage;
				} else {
					logger.debug("Skipping record with offset {}", receivedMsgOffset);
				}
			}
		}
	}

	public void connectToPeer(UUID peerUuid) {
		PeerInfo newPeer = null;
		try {
			// we getPeerProperties and close after since we assume we'll get here only once
			newPeer = peerLogDirectory.getPeerInfo(peerUuid);
		} catch (Exception ex) {
			logger.error("Couldn't get peer properties", ex);
		}
		if (newPeer != null && !newPeer.equals(currentPeer)) {
			connectToPeer(newPeer);
		} else {
			throw new IllegalArgumentException(String.format("peer entry w/uuid: %s not found in directory", peerUuid));
		}
	}

	private void connectToPeer(PeerInfo peerInfo) {
		logger.info("Switching to direct talk with {}", peerInfo);
		currentPeer = peerInfo;
		connectSocket();
		talkingToPeer = true;
	}

	private DataMessage sendToPeer(DataMessage message) {

		// send message request to peer
		peerSocket.send(message.toByteArray());

		final long waitStart = System.currentTimeMillis();
		byte[] reply = peerSocket.recv(0);
		final long waitEnd = System.currentTimeMillis();

		DataMessage replyMsg = null;
		try {
			replyMsg = DataMessage.parseFrom(reply);
			logger.debug("Got reply message with uuid: {}, waited {} ms", replyMsg.getMessageUuid(), (waitEnd - waitStart));
		} catch (InvalidProtocolBufferException ipbe) {
			logger.error("Caught protobuf exception", ipbe);
		}

		return replyMsg;
	}

	private static String getRecordInfo(RecordMetadata recordMetadata) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n checksum: ").append('\n').append(
			" timestamp: ").append(recordMetadata.timestamp()).append('\n').append(
			" offset: ").append(recordMetadata.offset()).append('\n').append(
			" #bytes in value: ").append(recordMetadata.serializedValueSize()).append("\n}");

		return builder.toString();
	}


	public void close() {

		singleThreadConsumerExecutor.shutdown();
		logger.info("Consumer executor service shut down");

		try {
			peerSocket.close();
			logger.info("Peer socket closed.");
			zmqContext.destroy();
			logger.info("Zmq context closed.");
		} catch (Exception ex) {
			logger.error("Error closing zmq connection", ex);
		}
		producer.close();
		logger.info("Producer closed.");
		consumer.close();
		logger.info("Consumer closed.");
	}
}

