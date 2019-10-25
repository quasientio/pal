package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** Base class for Log and Peer Invoker threads */
public abstract class AbstractMessageInvokerThread extends Thread {

  protected static final Logger logger =
      LoggerFactory.getLogger(AbstractMessageInvokerThread.class);

  private final AtomicLong requestsDispatched = new AtomicLong(0);
  private final AtomicLong requestsDismissed = new AtomicLong(0);

  // zmq stuff
  protected final ZContext zmqContext;
  protected final String dealerAddress;
  protected ZMQ.Socket socket;

  private final IncomingMessageDispatcher incomingMessageDispatcher;
  protected final DispatcherConnector dispatcherConnector;
  protected final MessageBuilder messageBuilder;

  AbstractMessageInvokerThread(
      ThreadGroup group,
      Runnable target,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector) {
    super(group, target, name);
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.dealerAddress = dealerAddress;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = dispatcherConnector;
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Initialized message invoker thread named: {} with dealerAddress: {}",
          name,
          dealerAddress);
    }
  }

  /**
   * Constructor exclusive for unit-testing -- to avoid ExecutorService and ThreadFactory
   * dependencies. NOTE: dispatcherConnector is set to null, since it's not required
   */
  AbstractMessageInvokerThread(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      String dealerAddress,
      IncomingMessageDispatcher incomingMessageDispatcher) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.dealerAddress = dealerAddress;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = null;
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized message invoker thread with dealerAddress: {}", dealerAddress);
    }
  }

  protected final String getMessageUuid(Object msg) {
    if (msg instanceof ExecMessage) {
      return ((ExecMessage) msg).getMessageUuid();
    } else if (msg instanceof InterceptRequest) {
      return ((InterceptRequest) msg).getMessageUuid();
    }
    return null;
  }

  protected void closeConnections() {
    try {
      socket.close();
    } catch (Exception e) {
      logger.debug("Error closing socket", e);
    }

    if (dispatcherConnector != null) {
      try {
        dispatcherConnector.closeThreadLocalSocket();
      } catch (Exception e) {
        logger.debug("Error closing dispatcher local socket", e);
      }
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Stopped invoker thread: {}, dispatched={} dismissed={}",
          getName(),
          requestsDispatched.get(),
          requestsDismissed.get());
    }
  }

  public abstract void run();

  protected final void dispatch(Object msg, Long recordOffset) {
    if (msg instanceof ExecMessage) {
      dispatch((ExecMessage) msg, recordOffset);
    } else if (msg instanceof InterceptRequest) {
      dispatch((InterceptRequest) msg, recordOffset);
    } else {
      logger.error("Ignoring dispatch of msg of unknown type: {}", msg.getClass().getName());
    }
  }

  protected final ExecMessage dispatch(ExecMessage requestMsg) {
    return dispatch(requestMsg, null);
  }

  private ExecMessage dispatch(ExecMessage requestMsg, @Nullable Long recordOffset) {
    // assumption: if recordOffset is Null, this is a peer (i.e. direct) request
    boolean isDirectRequest = recordOffset == null;
    ExecMessage replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, isDirectRequest);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker dispatched request message uuid: {} and recordOffset: {}, reply uuid: {}",
          requestMsg.getMessageUuid(),
          recordOffset,
          replyMsg.getMessageUuid());
    }
    updateCounters();
    return replyMsg;
  }

  private void dispatch(InterceptRequest interceptMsg, Long recordOffset) {
    boolean result = incomingMessageDispatcher.incomingCall(interceptMsg);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker dispatched request message uuid: {} and recordOffset: {}, reply: {}",
          interceptMsg.getMessageUuid(),
          recordOffset,
          result);
    }

    updateCounters();
  }

  private void updateCounters() {
    requestsDispatched.getAndIncrement();
    messageBuilder.resetThreadLocalSequence();
  }

  final AtomicLong getRequestsDispatched() {
    return requestsDispatched;
  }
}
