package com.ittera.cometa.core;

import com.google.common.primitives.Longs;

import com.ittera.cometa.LogReply;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.common.util.Strings;
import com.ittera.cometa.cxn.NoLogRequestNodeException;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.UUIDUtils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.CuratorWatcher;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher.Event;
import org.apache.zookeeper.WatchedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.UUID;

/**
 * Helper class used by LogWriter to relate ExecMessage with offset, so that we can asynchronously
 * write replies' offsets to PALDirectory (i.e. zookeeper).
 * Implements both Zookeeper Watcher and Kafka Producer Callback interfaces
 * NOTE 1: All calls to this class must be made by same thread (kafka's IO thread), unless publishOffsets = false.
 * The reason is we use zmq to publish received offsets and zmq sockets aren't thread-safe!
 * NOTE 2: This class writes Reply nodes under the corresponding Request nodes. That is why we are passing in
 * `inLog`, since KafkaWriter may be writing to a different log than reading from. This class doesn't use inLog to
 * directly write to it, but to look for the corresponding Request Node in the peerLogDirectory.
 * NOTE 3: TODO we shouldn't block kafka's IO thread for too long, or we'll slow down writing of records.
 * Ideally move work to an Executor class.
 */
class MessageOffsetInformer implements
	BackgroundCallback,
	CuratorWatcher,
	Callback {

	private final UUID messageUuid;
	private final UUID followingUuid;
	private LogReply logReply;
	private boolean done;
	private final boolean publishOffsets;
	private final boolean writeReplyNodes;
	private Throwable lastError;
	private final Socket offsetPublisher;
	private final PALDirectory palDirectory;
	private final LogInfo inLog;
	private final UUID peerUuid;

	private static final Logger logger = LoggerFactory.getLogger(MessageOffsetInformer.class);

	MessageOffsetInformer(UUID messageUuid, UUID followingUuid, boolean publishOffsets, boolean writeReplyNodes,
												Socket offsetPublisher, PALDirectory palDirectory, LogInfo inLog, UUID peerUuid) {
		this.messageUuid = messageUuid;
		this.followingUuid = followingUuid;
		this.publishOffsets = publishOffsets;
		this.writeReplyNodes = writeReplyNodes;
		this.offsetPublisher = offsetPublisher;
		this.palDirectory = palDirectory;
		this.inLog = inLog;
		this.peerUuid = peerUuid;
	}

	boolean isDone() {
		return done;
	}

	Throwable getLastError() {
		return lastError;
	}

	/**
	 * Kafka producer Callback interface
	 *
	 * @param recordMetadata
	 * @param e
	 */
	@Override
	public void onCompletion(RecordMetadata recordMetadata, Exception e) {

		// publish new record offset
		if (publishOffsets) {
			offsetPublisher.send(Longs.toByteArray(recordMetadata.offset()), ZMQ.SNDMORE);
			offsetPublisher.send(UUIDUtils.toBytes(messageUuid));
		}
		if (logger.isDebugEnabled()) {
			logger.debug("New offset {} for message w/uuid: {}", recordMetadata.offset(), messageUuid);
		}

		// if message is reply, save offset to zookeeper
		if (writeReplyNodes && followingUuid != null) {
			this.logReply = new LogReply(messageUuid, peerUuid, followingUuid, recordMetadata.offset());
			try {
				palDirectory.addLogReplyAsync(inLog.getName(), logReply, this);
			} catch (NoLogRequestNodeException nrne) {
				if (logger.isDebugEnabled()) {
					logger.debug("Log request node {} does not exist, will add ourselves as watcher and wait", nrne.getLogRequest());
				}
				// request node doesn't exist yet, add ourselves as watcher to get notified when created
				try {
					palDirectory.logRequestExistsAsync(inLog.getName(), followingUuid, this, this);
				} catch (Exception ex) {
					logger.error("Error in call to logRequestExistsAsync()", ex);
					lastError = ex;
				}
			} catch (Exception ex) {
				logger.error("Unhandled error creating reply message offset for request w/uuid: {}. Giving up.",
					followingUuid, ex);
				lastError = ex;
			}
		}
	}

	/**
	 * Curator Watcher interface
	 * Callback for the times when we want to reply with the offset, but the request node doesn't exist yet
	 *
	 * @param watchedEvent
	 */
	@Override
	public void process(WatchedEvent watchedEvent) {
		if (logger.isDebugEnabled()) {
			logger.debug("Received watchedEvent relating replyNode {}: {}", logReply, watchedEvent);
		}

		if (watchedEvent.getType() == Event.EventType.NodeCreated) {
			if (logger.isDebugEnabled()) {
				logger.debug("node created event: {}, will retry to write reply node", watchedEvent);
			}
			try {
				palDirectory.addLogReplyAsync(inLog.getName(), logReply, this);
			} catch (Exception ex) {
				logger.error("Error creating reply message offset for request w/uuid: {}. Giving up.",
					followingUuid, ex);
				lastError = ex;
			}
		}
	}

	/**
	 * Curator BackgroundCallback interface
	 *
	 * @param curatorFramework
	 * @param curatorEvent
	 * @throws Exception
	 */
	@Override
	public void processResult(CuratorFramework curatorFramework, CuratorEvent curatorEvent) throws Exception {
		if (curatorEvent.getType().equals(CuratorEventType.CREATE)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Got curatorEvent: {}", curatorEvent);
			}
			String eventNode = Strings.stringAfterLast(curatorEvent.getName(), "/");
			// callback is about the Reply node
			if (messageUuid.toString().equalsIgnoreCase(eventNode)) {
				if (Code.get(curatorEvent.getResultCode()) == Code.OK) {
					if (logger.isDebugEnabled()) {
						logger.debug("reply node now created for message w/uuid: {}", messageUuid);
					}
					done = true;
				} else {
					logger.error("reply node NOT created (error code: {}) for message w/uuid: {}", curatorEvent.getResultCode(),
						messageUuid);
				}
			}
			// callback is about the Request node
			else if (followingUuid != null && followingUuid.toString().equalsIgnoreCase(eventNode)) {
				if (Code.get(curatorEvent.getResultCode()) == Code.OK) {
					if (logger.isDebugEnabled()) {
						logger.debug("node exists now: will retry to write reply node");
					}
					try {
						palDirectory.addLogReplyAsync(inLog.getName(), logReply, this);
					} catch (Exception ex) {
						logger.error("Unhandled error creating reply node w/uuid: {}. Giving up.", followingUuid, ex);
						lastError = ex;
					}
				} else {
					logger.error("request node NOT created (error code: {}) for message w/uuid: {}", curatorEvent.getResultCode(),
						followingUuid);
				}
			}
		}
	}
}
