package com.ittera.cometa.cxn;

import com.ittera.cometa.LogReply;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.List;
import java.util.Set;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.AsyncCallback;

public class DataMessageFuture implements Future<DataMessage>, Watcher, AsyncCallback.ChildrenCallback {
  private final CountDownLatch latch = new CountDownLatch(1);
  private DataMessage value;

  protected final static Logger logger = LoggerFactory.getLogger(DataMessageFuture.class);

  private final ThinPeer thinPeer;
  private final PeerLogDirectory peerLogDirectory;
  private final String logName, requestUuid;
  private final ExecutorService executorService;

  DataMessageFuture(ThinPeer thinPeer, PeerLogDirectory peerLogDirectory,
                                ExecutorService executorService, String logName, String requestUuid) {
    this.thinPeer = thinPeer;
    this.peerLogDirectory = peerLogDirectory;
    this.executorService = executorService;
    this.logName = logName;
    this.requestUuid = requestUuid;
  }

  // <editor-fold defaultstate="collapsed" desc="FUTURE Interface">
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  @Override
  public boolean isCancelled() {
    //TODO (shouldn't we countDown and return true?)
    return false;
  }

  @Override
  public boolean isDone() {
    return latch.getCount() == 0;
  }

  @Override
  public DataMessage get() throws InterruptedException {
    latch.await();
    return value;
  }

  @Override
  public DataMessage get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    if (latch.await(timeout, unit)) {
      return value;
    } else {
      throw new TimeoutException();
    }
  }

   /**
   * To be called just once
   * @param result
   */
  private void put(DataMessage result) {
    value = result;
    latch.countDown();
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Zk Watcher Interface">
  @Override
  public void process(WatchedEvent evt) {
    logger.debug("NodeChildrenChanged event for node of request w/uuid: {}", requestUuid);

    if (evt.getType() == Event.EventType.NodeChildrenChanged) {
      process();
    }
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Zk Callback Interface">
  @Override
  public void processResult(int rc, String path, Object ctx, List<String> children) {
    logger.debug("getChildren returned for request w/uuid: {}, with {} children", requestUuid, children.size());

    if (!children.isEmpty()) {
      process();
    }
  }
  // </editor-fold>

  private void process() {
      final LogReply logReply = getReplyNode();
      if (logReply == null) {
        logger.error("Null LogReply object for request msg w/uuid: {}. Cancelling future.", requestUuid);
        this.cancel(true);
        return;
      }

      // let the executor service fetch the message from the log
      executorService.submit(new Runnable() {
        @Override
        public void run() {
          // set msg value to complete future
          DataMessage messageReply = thinPeer.getMessageAtOffset(logReply.getOffset());
          logger.debug("completing future reply msg w/uuid: {} for request w/uuid: {}",
            messageReply.getMessageUuid(), messageReply.getFollowingUuid());
          DataMessageFuture.this.put(messageReply);
        }
      });
  }

  private LogReply getReplyNode() {

    LogReply logReply = null;
    try {
      Set<LogReply> replySet = peerLogDirectory.getRepliesTo(logName, requestUuid);
      if (!replySet.isEmpty()) {
        logReply = (LogReply) replySet.toArray()[0];
      }
    } catch (Exception ex) {
      logger.error("Error getting LogReply object for request msg w/uuid: {}", requestUuid, ex);
    }

    return logReply;
  }
}
