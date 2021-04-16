/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.cxn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.ittera.pal.common.directory.nodes.LogReply;
import net.ittera.pal.common.directory.nodes.LogRequest;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * <pre>
 * How ExecMessageFuture is used by class X:
 * 1. X sends an ExecMessage sent to the log. It does not wait for the reply consuming the log.
 * 2. X then creates an instance of ExecMessageFuture representing the message, holding required references
 * 3. X adds a Request Node to ZK asynchronously, with the instance of ExecMessageFuture as BackgroundCallback
 * 4. When the BackgroundCallback method, processResult(), is called, a call to getChildren() is made on the
 * Request Node, also asynchronously, setting itself as BackgroundCallback, and as a CuratorWatcher
 * 5. When the BackgroundCallback method is called with the children evt type, if getChildren() is !empty, it calls
 * process(). This is possible, but not likely. It's more likely that children will be empty, and later the
 * CuratorWatcher callback will receive the event of having a new child (i.e. the replyNode).
 * 6. When/If the CuratorWatcher callback method, process(WatchedEvent) is called, if the request node's children has
 * changed, then it calls process()
 * 7. When process() is called (either from step 5 or 6), it gets the reply node, and then uses the message offset
 * to retrieve the message from the log. Fetching the reply node is the only synchronous call to ZK by this class, but
 * as the rest of process(), it's invoked asynchronously by the executor service. At the end of process(), the actual
 * message reply retrieved from is put() in the future, completing it.
 *
 * See ExecMessageFutureTest for an example
 * </pre>
 */
class ExecMessageFuture implements BackgroundCallback, CuratorWatcher, Future<ExecMessage> {

  private static final Logger logger = LoggerFactory.getLogger(ExecMessageFuture.class);

  private final CountDownLatch latch = new CountDownLatch(1);
  private ExecMessage value;
  private boolean cancelled;
  private final ThinPeer thinPeer;
  private final PALDirectory palDirectory;
  private final String logName;
  private final LogRequest logRequest;
  private final ExecutorService executorService;

  ExecMessageFuture(
      ThinPeer thinPeer,
      PALDirectory palDirectory,
      ExecutorService executorService,
      String logName,
      LogRequest logRequest) {
    this.thinPeer = thinPeer;
    this.palDirectory = palDirectory;
    this.executorService = executorService;
    this.logName = logName;
    this.logRequest = logRequest;
  }

  // <editor-fold defaultstate="collapsed" desc="FUTURE Interface">
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    cancelled = true;
    return true;
  }

  @Override
  public boolean isCancelled() {
    // TODO (shouldn't we countDown and return true?)
    return cancelled;
  }

  @Override
  public boolean isDone() {
    return latch.getCount() == 0;
  }

  @Override
  public ExecMessage get() throws InterruptedException {
    latch.await();
    return value;
  }

  @Override
  public ExecMessage get(long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException {
    if (latch.await(timeout, unit)) {
      return value;
    } else {
      throw new TimeoutException();
    }
  }

  /**
   * To be called just once
   *
   * @param result
   */
  private void put(ExecMessage result) {
    value = result;
    latch.countDown();
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Curator Watcher Interface">
  @Override
  public void process(WatchedEvent evt) {
    if (logger.isDebugEnabled()) {
      logger.debug("NodeChildrenChanged event: {} for node of request: {}", evt, logRequest);
    }
    if (evt.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
      process();
    }
  }
  // </editor-fold>

  @Override
  public String toString() {
    return String.format(
        "uuid: %s done: %s cancelled: %s", logRequest.getUuid(), isDone(), isCancelled());
  }

  private void process() {

    if (isDone() || isCancelled()) {
      return;
    }

    // let the executor service fetch the replyNode from ZK, and the message from the log
    executorService.submit(
        () -> {
          final LogReply logReply = getReplyNode();

          if (logReply == null) {
            logger.warn(
                "Null reply for request node {}. May haven been already processed -> ignoring.",
                logRequest);
          }

          // set msg value to complete future
          ExecMessage messageReply =
              thinPeer.getMessageAtOffset(logReply.getOffset()).getExecMessage();
          if (logger.isDebugEnabled()) {
            logger.debug(
                "completing future reply msg w/uuid: {} for request w/uuid: {}",
                messageReply.getMessageUuid(),
                messageReply.getFollowingUuid());
          }
          ExecMessageFuture.this.put(messageReply);
          // delete request and reply nodes
          deleteRequestNode();
        });
  }

  private LogReply getReplyNode() {

    LogReply logReply = null;
    try {
      Set<LogReply> replySet = palDirectory.getRepliesTo(logName, logRequest);
      if (!replySet.isEmpty()) {
        logReply = replySet.iterator().next();
      }
    } catch (Exception ex) {
      logger.warn("Error getting LogReply object for request: {}", logRequest, ex);
    }

    return logReply;
  }

  private void deleteRequestNode() {
    try {
      palDirectory.deleteLogRequestAsync(logName, logRequest);
    } catch (Exception e) {
      logger.error("Error deleting directory request node: {} for log: {}", logRequest, logName);
    }
  }

  // <editor-fold defaultstate="collapsed" desc="BackgroundCallback Interface">
  @Override
  public void processResult(CuratorFramework curatorFramework, CuratorEvent curatorEvent)
      throws Exception {
    // LogRequest node is created
    if (curatorEvent.getType().equals(CuratorEventType.CREATE)) {
      if (KeeperException.Code.get(curatorEvent.getResultCode()) == KeeperException.Code.OK) {
        // set watch to get notified about changes to children
        palDirectory.getRequestNodeChildrenAsyncWithWatch(
            logName, logRequest.getUuid(), this, this);
      } else {
        logger.error(
            "Not OK adding log request for {}, error code: {}",
            logRequest.getUuid(),
            curatorEvent.getResultCode());
      }
    } // getChildren(requestNode) returns
    else if (curatorEvent.getType().equals(CuratorEventType.CHILDREN)) {
      List<String> children = curatorEvent.getChildren();
      if (logger.isDebugEnabled()) {
        logger.debug(
            "getChildren returned for request: {}, with {} children", logRequest, children.size());
      }
      if (!children.isEmpty()) {
        if (logger.isDebugEnabled()) {
          StringBuilder sb = new StringBuilder("children:\n");
          children.forEach(s -> sb.append(s).append('\n'));
          logger.debug(sb.toString());
        }
        process();
      }
    }
  }
  // </editor-fold>
}
