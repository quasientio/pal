package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
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
	private final String kafkaTopicPrefix, kafkaTopic;
	private final TopicPartition topicPartition;
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

	public ThinPeer(String propertiesFile, boolean allowP2P, PeerInfo initialPeer, LogInfo logInfo) throws Exception {
		logger.info("Initializing ThinPeer with props from: {}, allowP2P: {}, initialPeer: {}, logInfo: {}",
			propertiesFile, allowP2P, initialPeer, logInfo);

		this.allowP2P = allowP2P;
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

		// get log to connect to
		String bootstrapServers = null;
		if (logInfo != null) {
			this.kafkaTopic = logInfo.getName();
			bootstrapServers = logInfo.getBootstrapServers();
		} else {
			// get last log with prefix = kafkaTopic
			LogInfo lastLog = peerLogDirectory.getLastLog(kafkaTopicPrefix);
			this.kafkaTopic = lastLog.getName();
			bootstrapServers = lastLog.getBootstrapServers();
		}

		logger.info("Will read and write to log: {}", this.kafkaTopic);


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
		kafkaProducerProps.put("bootstrap.servers", bootstrapServers);
		logger.info("Will connect to bootstrap servers: {}", bootstrapServers);
		producer = new KafkaProducer<>(kafkaProducerProps);

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
		kafkaConsumerProps.put("bootstrap.servers", bootstrapServers);
		consumer = new KafkaConsumer<>(kafkaConsumerProps);
		logger.debug("Kafka consumer initialized: {}", consumer);

		//manual assignment of partition so we can control offset seek
		topicPartition = new TopicPartition(kafkaTopic, 0);
		consumer.assign(Arrays.asList(topicPartition));

		// create zmq context
		logger.debug("Initializing zmq context");
		zmqContext = new ZContext();
		peerSocket = zmqContext.createSocket(ZMQ.REQ);
		if (currentPeer != null) {
			connectToPeer(currentPeer);
		}

		//init msg builder
		dataMessageBuilder = new ProtobufDataMessageBuilder();
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
		logger.info("Starting wait for type: {} and field name: {}", type, fieldName);
		DataMessage reply = null;
		// TODO extra param to seek before
		//consumer.seek(topicPartition, sentRecordOffset);

		while (true) {
			ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
			for (ConsumerRecord record : records) {
				final DataMessage dataMessage = (DataMessage) record.value();
				long receivedMsgOffset = record.offset();

				if (dataMessage.hasStaticFieldPutDone() &&
					fieldName.equals(dataMessage.getStaticFieldPutDone().getField().getName())) {
					logger.info("Got matching message with offset {}:\n{}", receivedMsgOffset, dataMessage);
					return dataMessage;
				} else {
					logger.debug("Skipping record with offset {}", receivedMsgOffset);
				}
			}
		}
	}

	public DataMessage getMessageAtOffset(Long seek) {

		logger.info("Getting message @ offset #{}", seek);
		consumer.seek(topicPartition, seek);

		DataMessage cachedMsg = getCachedMessageAtOffset(seek);
		if (cachedMsg != null) {
			logger.debug("Got cached record at offset {}", seek);
			return cachedMsg;
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

		logger.info("Getting {} messages starting @ offset #{}", numMessages, startOffset);
		consumer.seek(topicPartition, startOffset);
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
		producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
		logger.debug("Message sent to log, and we're done:\n{}", message);
	}

	public Future<DataMessage> sendToLogAsync(DataMessage message) {

		final String requestMsgUuid = message.getMessageUuid();

		// send to kafka
		producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
		logger.debug("Message sent to log:\n{}", message);

		final DataMessageFuture messageFuture = new DataMessageFuture(this, peerLogDirectory,
			singleThreadConsumerExecutor, kafkaTopic, requestMsgUuid);

		// addLogRequest callback
		StringCallback addLogCallback = new StringCallback() {
			@Override
			public void processResult(int rc, String path, Object ctx, String name) {
				switch (Code.get(rc)) {
					case OK:
						((ZkClient) peerLogDirectory).getChildren(kafkaTopic, requestMsgUuid, messageFuture, messageFuture,
							null);
						break;
					default:
						logger.warn("Not OK adding log request for {}, error code: {}", requestMsgUuid, rc);
						return;
				}
			}
		};

		// asynchronously create req node in zk
		try {
			((ZkClient) peerLogDirectory).addLogRequest(kafkaTopic, requestMsgUuid, addLogCallback, null);
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
			producer.send(new ProducerRecord(kafkaTopic, message.getMessageUuid(), message));
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
		consumer.seek(topicPartition, sentRecordOffset);

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
					logger.info("Got reply with offset {} and uuid {} ", receivedMsgOffset, dataMessage.getMessageUuid());
					// try switching to direct peer talk (i.e. p2p)
					if (allowP2P) {
						String concentratorUuid = dataMessage.getConcentratorUuid();
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
		} catch (InvalidProtocolBufferException ipbe) {
			System.err.println("Caught protobuf exception: " + ipbe.getMessage());
			ipbe.printStackTrace(System.err);
			logger.error("Caught protobuf exception", ipbe);
		}

		logger.debug("Got reply message with uuid: {}, waited {} ms", replyMsg.getMessageUuid(), (waitEnd - waitStart));

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

