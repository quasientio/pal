package com.ittera.cometa.cxn;

import com.ittera.cometa.LogReply;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.Set;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogMessageReplyWatcher implements Watcher {

  protected final static Logger logger = LoggerFactory.getLogger(LogMessageReplyWatcher.class);

  private final DataMessageFuture messageFuture;
  private final ThinPeer thinPeer;
  private final PeerLogDirectory peerLogDirectory;
  private final String logName, requestUuid;

  public LogMessageReplyWatcher(DataMessageFuture messageFuture, ThinPeer thinPeer, PeerLogDirectory peerLogDirectory,
                                String logName, String requestUuid) {
    this.messageFuture = messageFuture;
    this.thinPeer = thinPeer;
    this.peerLogDirectory = peerLogDirectory;
    this.logName = logName;
    this.requestUuid = requestUuid;
  }

  @Override
  public void process(WatchedEvent evt) {

    if (evt.getType() == Event.EventType.NodeChildrenChanged) {
      LogReply logReply = null;
      try {
        Set<LogReply> replySet = peerLogDirectory.getRepliesTo(logName, requestUuid);
        if (!replySet.isEmpty()) {
          logReply = (LogReply) replySet.toArray()[0];
        }
      } catch (Exception ex) {
        logger.error("Error getting LogReply object for request msg w/uuid: {}. Cancelling future.", requestUuid, ex);
      } finally {
        if (logReply == null)  {
          messageFuture.cancel(true);
          return;
        }
      }

      // set msg value to complete future
      DataMessage messageReply = thinPeer.getMessageAtOffset(logReply.getOffset());
      logger.debug("completing future reply msg w/uuid: {} for request w/uuid: {}",
        messageReply.getMessageUuid(), messageReply.getFollowingUuid());
      messageFuture.put(messageReply);
    }
  }
}

