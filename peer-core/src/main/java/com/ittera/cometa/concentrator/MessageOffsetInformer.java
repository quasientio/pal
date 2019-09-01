package com.ittera.cometa.concentrator;

import com.google.common.primitives.Longs;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.LogInfo;

import com.ittera.cometa.cxn.NoLogRequestNodeException;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.cxn.ZkClient;

import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;

import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;

import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.UUID;

/**
 * Helper class used by LogWriter to relate DataMessage with offset, so that we can asynchronously
 * write replies' offsets to peerLogDirectory (i.e. zookeeper).
 * <p>
 * Implements both Zookeeper Watcher and Kafka Producer Callback interfaces
 * <p>
 * NOTE 1: All calls to this class must be made by same thread (kafka's IO thread), unless publishOffsets = false.
 * The reason is we use zmq to publish received offsets and zmq sockets aren't thread-safe!
 * NOTE 2: This class writes Reply nodes under the their corresponding Request nodes. That is why we are passing in
 * `inLog`, since KafkaWriter may be writing to a different log than reading from. This class doesn't use inLog to
 * directly write to it, but to look for the corresponding Request Node in the peerLogDirectory.
 * NOTE 3: TODO we shouldn't block kafka's IO thread for too long, or we'll slow down writing of records.
 * Ideally move work to an Executor class.
 * <p>
 */
class MessageOffsetInformer implements Callback, Watcher {
	private final DataMessage message;
	private LogReply logReply;
	private boolean done;
	private final boolean publishOffsets;
	private Throwable lastError;
	private final Socket offsetPublisher;
	private final PeerLogDirectory peerLogDirectory;
	private final LogInfo inLog;
	private final UUID peerUuid;

	private static final Logger logger = LoggerFactory.getLogger(MessageOffsetInformer.class);

	private final AsyncCallback.StringCallback addReplyCallback = new AsyncCallback.StringCallback() {
		@Override
		public void processResult(int rc, String path, Object ctx, String name) {
			if (Code.get(rc) == Code.OK) {
				if (logger.isDebugEnabled()) {
					logger.debug("reply node created for message w/uuid: {}", message.getMessageUuid());
				}
				done = true;
			} else {
				logger.error("reply node NOT created (error code: {}) for message w/uuid: {}", rc,
					message.getMessageUuid());
			}
		}
	};

	private final AsyncCallback.StatCallback statCallback = new AsyncCallback.StatCallback() {
		@Override
		public void processResult(int rc, String path, Object ctx, Stat stat) {
			if (logger.isDebugEnabled()) {
				logger.debug("processResult with rc: {}, path: {}, and stat: {}", rc, path, stat);
			}
			if (Code.get(rc) == Code.OK && stat != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("node exists now: will retry to write reply node");
				}
				try {
					((ZkClient) peerLogDirectory).addLogReply(inLog.getName(), logReply, addReplyCallback);
				} catch (Exception ex) {
					logger.error("Unhandled error creating reply message offset for request w/uuid: {}. Giving up.",
						message.getFollowingUuid(), ex);
					lastError = ex;
				}
			}
		}
	};

	MessageOffsetInformer(DataMessage message, boolean publishOffsets, Socket offsetPublisher,
												PeerLogDirectory peerLogDirectory, LogInfo inLog, UUID peerUuid) {
		this.message = message;
		this.publishOffsets = publishOffsets;
		this.offsetPublisher = offsetPublisher;
		this.peerLogDirectory = peerLogDirectory;
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
			offsetPublisher.send(UUIDUtils.toBytes(UUID.fromString(message.getMessageUuid())));
		}
		if (logger.isDebugEnabled()) {
			logger.debug("New offset {} for message w/uuid: {}", recordMetadata.offset(), message.getMessageUuid());
		}

		// if message is reply, save offset to zookeeper
		if (message.hasFollowingUuid()) {
			this.logReply = new LogReply(UUID.fromString(message.getMessageUuid()), peerUuid,
				UUID.fromString(message.getFollowingUuid()), recordMetadata.offset());
			try {
				((ZkClient) peerLogDirectory).addLogReply(inLog.getName(), logReply, addReplyCallback);
			} catch (NoLogRequestNodeException nrne) {
				if (logger.isDebugEnabled()) {
					logger.debug("Log request node {} does not exist, will add ourselves as watcher and wait", nrne.getLogRequest());
				}
				// request node doesn't exist yet, add ourselves as watcher to get notified when created
				((ZkClient) peerLogDirectory).requestExists(inLog.getName(), UUID.fromString(message.getFollowingUuid()),
					this, statCallback);
			} catch (Exception ex) {
				logger.error("Unhandled error creating reply message offset for request w/uuid: {}. Giving up.",
					message.getFollowingUuid(), ex);
				lastError = ex;
			}
		}
	}

	/**
	 * Zookeeper Watcher interface
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
				((ZkClient) peerLogDirectory).addLogReply(inLog.getName(), logReply, addReplyCallback);
			} catch (Exception ex) {
				logger.error("Error creating reply message offset for request w/uuid: {}. Giving up.",
					message.getFollowingUuid(), ex);
				lastError = ex;
			}
		}
	}
}
