package com.ittera.cometa.core;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.nio.channels.ClosedSelectorException;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.AbstractExecutionThreadService;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * TODO Optimize - :sampling with visualvm shows this class as the one with highest memory allocation per thread.
 * We are reading everything from the log. Is it absolutely required? Can it be optional? If so, what to skip reading?
 */
@Singleton
public class LogReader extends AbstractExecutionThreadService {

	private static final Logger logger = LoggerFactory.getLogger(LogReader.class);

	private volatile boolean acceptingRequests = false;
	private volatile boolean connectionsOpen = false;
	private volatile boolean shutdownRequested = false;

	// zmq stuff
	private ZContext zmqContext;
	private Socket logDealer;
	private Socket offsetSubscriber;
	private final String inLogAddress, offsetPubAddress;

	// counters
	private final AtomicLong totalPollingNanos = new AtomicLong(0);
	private final AtomicInteger totalPolls = new AtomicInteger(0);
	private final AtomicInteger messagesRcvd = new AtomicInteger(0);

	// kafka stuff
	private boolean skipWrittenOffsets;
	private final Duration pollDuration;
	private Long initialOffset;
	private String kafkaTopic;
	private TopicPartition topicPartition;
	private Consumer<String, ExecMessage> consumer;
	private final Properties consumerProperties = new Properties();
	private volatile long lastOffsetRead = -1;

	// pal directory
	private PALDirectory palDirectory;

	private UUID peerUuid;

	// shared by threads OffsetUpdater and LogReader: TODO avoid sharing
	final private AbstractQueue<Long> skipOffsets = new ConcurrentLinkedQueue<>();

	private final class OffsetUpdater extends Thread {

		private final Socket offsetSubscriber;

		OffsetUpdater(Socket offsetSubscriber) {
			super("Offset informer");
			this.offsetSubscriber = offsetSubscriber;
		}

		@Override
		public void run() {
			if (logger.isDebugEnabled()) {
				logger.debug("Offset informer running");
			}

			byte[] offsetBuff;

			while (!Thread.interrupted()) {
				long offset;
				try {
					// multi-part msg: 1) offset as byte[], 2) uuid as byte[]
					offsetBuff = offsetSubscriber.recv();
					offsetSubscriber.recv(); // read and discard UUID
					offset = Longs.fromByteArray(offsetBuff);
					skipOffsets.add(offset);
				} catch (ClosedSelectorException ex) {
					if (logger.isDebugEnabled()) {
						logger.debug("Caught ClosedSelectorException. Breaking out.");
					}
					break;
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
			}
		}
	}

	@Inject
	public LogReader(@Named("key.deserializer") String keyDeserializer,
									 @Named("value.deserializer") String valueDeserializer,
									 @Named("enable.auto.commit") String autoCommit,
									 @Named("auto.commit.interval.ms") String autoCommitInterval,
									 @Named("auto.offset.reset") String autoOffsetReset,
									 @Named("session.timeout.ms") String sessionTimeout,
									 @Named("id") String peerId,
									 @Named("pollDuration") String pollDuration,
									 @Named("in.log") String inLogAddress,
									 @Named("offset.pub") String offsetPubAddress,
									 ZContext zmqContext,
									 PALDirectory palDirectory,
									 UUID peerUuid) {
		this.zmqContext = zmqContext;
		this.peerUuid = peerUuid;
		this.palDirectory = palDirectory;
		// zmq addresses
		this.inLogAddress = inLogAddress;
		this.offsetPubAddress = offsetPubAddress;
		// prepare Kafka consumer
		this.pollDuration = Duration.of(Long.parseLong(pollDuration), ChronoUnit.MILLIS);
		consumerProperties.put("group.id", peerId);
		consumerProperties.put("key.deserializer", keyDeserializer);
		consumerProperties.put("value.deserializer", valueDeserializer);
		consumerProperties.put("enable.auto.commit", autoCommit);
		consumerProperties.put("auto.commit.interval.ms", autoCommitInterval);
		consumerProperties.put("auto.offset.reset", autoOffsetReset);
		consumerProperties.put("session.timeout.ms", sessionTimeout);

		StringBuilder propsStr = new StringBuilder();
		for (String propKey : consumerProperties.stringPropertyNames()) {
			propsStr.append(propKey).append('=').append(consumerProperties.getProperty(propKey)).append(", ");
		}
		logger.info("Created log reader for peer with id '{}' and properties: [{}]",
			peerId, propsStr.toString());
	}

	/**
	 * Used from unit tests with MockConsumer
	 *
	 * @param zmqContext
	 * @param inLogAddress
	 * @param offsetPubAddress
	 * @param palDirectory
	 * @param consumer
	 * @param peerUuid
	 * @param pollDuration
	 */
	LogReader(ZContext zmqContext,
						String inLogAddress,
						String offsetPubAddress,
						PALDirectory palDirectory,
						Consumer<String, ExecMessage> consumer,
						UUID peerUuid,
						long pollDuration) {
		this.zmqContext = zmqContext;
		this.inLogAddress = inLogAddress;
		this.offsetPubAddress = offsetPubAddress;
		this.peerUuid = peerUuid;
		this.palDirectory = palDirectory;
		this.consumer = consumer;
		this.pollDuration = Duration.of(pollDuration, ChronoUnit.MILLIS);

		logger.info("Created log reader for peer with id '{}'", peerUuid);
	}

	public void readFromLog(String logName, boolean skipWrittenOffsets, Long initialOffset) throws Exception {

		this.kafkaTopic = logName;
		this.skipWrittenOffsets = skipWrittenOffsets;
		this.initialOffset = initialOffset;
		LogInfo logInfo = palDirectory.getLogInfo(logName);

		consumerProperties.put("bootstrap.servers", logInfo.getBootstrapServers());
		logger.info("Now reading from log: {} and bootstrapServers: {}, starting at offset: {}", logInfo.getName(),
			logInfo.getBootstrapServers(), initialOffset);
	}

	private void openConnections() {
		// only configure consumer if no consumer passed in constructor
		if (consumer == null) {
			this.consumer = new KafkaConsumer<>(consumerProperties);
			//manual assignment of partition so we can control offset seek
			topicPartition = new TopicPartition(kafkaTopic, 0);
			final List<TopicPartition> topicPartitionList = Collections.singletonList(topicPartition);
			consumer.assign(topicPartitionList);
			if (initialOffset == null) {
				consumer.seekToBeginning(topicPartitionList);
			} else {
				consumer.seek(topicPartition, initialOffset);
			}
			logger.info("Initialized log consumer");
		}

		this.logDealer = zmqContext.createSocket(SocketType.DEALER);
		logDealer.bind(inLogAddress);

		// subscriber to get the offsets written by the message writer
		if (skipWrittenOffsets) {
			this.offsetSubscriber = zmqContext.createSocket(SocketType.SUB);
			offsetSubscriber.connect(offsetPubAddress);
			offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

			new OffsetUpdater(offsetSubscriber).start();
			logger.info("Initialized offset notifier thread");
		}

		logger.info("Initialized zmq sockets");

		connectionsOpen = true;
		logger.info("All connections open");
	}

	private void closeConnections() {

		if (consumer != null) {
			consumer.close();
			logger.info("Closed log consumer");
		}

		if (logDealer != null) {
			logDealer.close();
		}

		if (offsetSubscriber != null) {
			offsetSubscriber.close();
		}

		logger.info("All connections closed");
	}

	@Override
	public final void run() {

		long iterations = 0;

		//wait for connections established
		while (!connectionsOpen) {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				//what to do
			}
		}

main_loop:
		while (isRunning() && !Thread.interrupted()) {

			while (!acceptingRequests) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// break out via thread interrupt
					break main_loop;
				} // break out by stopping the service
				if (shutdownRequested) { // we need a way out if the service is stopped while we're here
					break main_loop;
				}
			}

			// read from kafka
			ConsumerRecords<String, ExecMessage> records;
			long t0;
			t0 = System.nanoTime();
			records = consumer.poll(pollDuration);
			totalPollingNanos.getAndAdd(System.nanoTime() - t0);
			totalPolls.getAndIncrement();

			if (logger.isDebugEnabled() && records.count() > 0) {
				logger.debug("Records read: {}", records.count());
			}

			// process records if any
			for (ConsumerRecord record : records) {

				messagesRcvd.getAndIncrement();

				if (logger.isDebugEnabled()) {
					logger.debug("Processing received record # {} with offset {} :\n {}", messagesRcvd, record.offset(),
						record);
				}

				final long messageOffset = record.offset();
				lastOffsetRead = messageOffset;

				if (!recordProducedOrDispatchingBySelf(record.headers())) {
					final ExecMessage execMessage = (ExecMessage) record.value();

					// send request to DEALER socket
					logDealer.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
					logDealer.send(String.valueOf(messageOffset), ZMQ.SNDMORE);
					logDealer.send(execMessage.toByteArray(), 0);
					if (logger.isDebugEnabled()) {
						logger.debug("Dealt new log message with uuid: {}", execMessage.getMessageUuid());
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("skipped msg with offset: {}", messageOffset);
					}
				}

				// get next offset to poll
				if (skipWrittenOffsets) {
					Long nextOffset = nextOffset();
					if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
						if (logger.isDebugEnabled()) {
							logger.debug("Skipping received records. Jumping from offset: {} to: {}", lastOffsetRead,
								nextOffset);
						}
						consumer.seek(topicPartition, nextOffset);
						break;
					}
				}
			}

			// short pause, not to be eager
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}

			// get next offset to poll
			if (skipWrittenOffsets) {
				Long nextOffset = nextOffset();
				if ((nextOffset != null) && (nextOffset > (lastOffsetRead + 1))) {
					if (logger.isDebugEnabled()) {
						logger.debug("Jumping from offset: {} to: {}", lastOffsetRead, nextOffset);
					}
					consumer.seek(topicPartition, nextOffset);
				}
			}
		}

		closeConnections();
	}

	private boolean recordProducedOrDispatchingBySelf(Headers headers) {

		return Stream.of("produced-by", "dispatching-by").anyMatch(hdrName -> {
			for (Header header : headers.headers(hdrName)) {
				UUID uuidInHeader = UUIDUtils.fromBytes(header.value());
				if (peerUuid.equals(uuidInHeader)) {
					if (logger.isDebugEnabled()) {
						logger.debug("will skip message {} self", hdrName);
					}
					return true;
				}
			}
			return false;
		});
	}

	private Long nextOffset() {
		if (logger.isTraceEnabled()) {
			final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
			logger.trace("in w/ lastOffsetRead = {}, and queue: {}", lastOffsetRead, queueStr);
		}

		// initial candidate == last read + 1
		Long nextToRead = lastOffsetRead + 1;

		Long nextOffsetToSkip = skipOffsets.peek();

		// clean up all possible offsets up to and including last read
		while ((nextOffsetToSkip != null) && (nextOffsetToSkip < nextToRead)) {
			skipOffsets.poll();
			nextOffsetToSkip = skipOffsets.peek();
		}

		// while queue not empty, pop next offsets in sequence
		while (nextToRead.equals(nextOffsetToSkip)) {
			skipOffsets.poll();
			nextToRead++;
			nextOffsetToSkip = skipOffsets.peek();
		}

		if (logger.isTraceEnabled()) {
			final String queueStr = skipOffsets.peek() == null ? "empty" : skipOffsets.toString();
			logger.trace("out w/ nextToRead = {} with lastOffsetRead = {}, and final queue: {}",
				nextToRead, lastOffsetRead, queueStr);
		}
		return nextToRead;
	}

	@Override
	protected void startUp() {
		openConnections();
	}

	@Override
	protected void triggerShutdown() {

		logger.info("Log reader shutting down.");
		//TODO: clean up, send uncommitted offset, etc.
		shutdownRequested = true;
		acceptingRequests = false;
	}

	@Override
	protected void shutDown() {

		logger.info("Log reader shut down.");
	}

	protected void printDebugStats() {
		if (logger.isDebugEnabled()) {
			logger.debug("--------STATS--------");
			logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
			logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
			logger.debug("# polls: {}", totalPolls.get());
			logger.debug("-----END OF STATS-----");
		}
	}

	public boolean isAcceptingRequests() {
		return acceptingRequests;
	}

	public void acceptConnections(boolean acceptConnections) {
		this.acceptingRequests = acceptConnections;
	}
}
