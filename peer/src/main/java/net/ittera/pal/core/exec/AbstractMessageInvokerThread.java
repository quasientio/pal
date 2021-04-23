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

package net.ittera.pal.core.exec;

import static java.lang.String.format;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptReply;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

/** Base class for Log and Peer Invoker threads */
public abstract class AbstractMessageInvokerThread extends Thread {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final AtomicLong requestsDispatched = new AtomicLong(0);
  private final AtomicLong requestsDismissed = new AtomicLong(0);

  // zmq stuff
  protected final ZContext zmqContext;
  protected final String dealerAddress;
  protected ZMQ.Socket socket;

  private final UUID peerUuid;
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
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(group, target, name);
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.dealerAddress = dealerAddress;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = dispatcherConnector;
    this.peerUuid = peerUuid;
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
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.dealerAddress = dealerAddress;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = null;
    this.peerUuid = peerUuid;
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized message invoker thread with dealerAddress: {}", dealerAddress);
    }
  }

  protected final String getMessageUuid(Message msg) {
    final ExecMessage execMessage = msg.getExecMessage();
    if (execMessage != null) {
      return execMessage.getMessageUuid();
    }

    final InterceptMessage interceptMessage = msg.getInterceptMessage();
    if (interceptMessage != null) {
      return interceptMessage.getMessageUuid();
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
        dispatcherConnector.closeThreadLocalSockets();
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

  @Override
  public abstract void run();

  protected final void dispatch(Message message, Long recordOffset) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      dispatch(message.getExecMessage(), recordOffset);
      return;
    }

    final InterceptMessage interceptMessage = message.getInterceptMessage();
    if (interceptMessage != null) {
      dispatch(message.getInterceptMessage(), recordOffset);
      return;
    }

    logger.error("Ignoring dispatch of msg of unknown type: {}", ColferUtils.format(message));
  }

  protected final Message dispatch(Message message) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      return messageBuilder.wrap(dispatch(message.getExecMessage(), null));
    }

    final InterceptMessage interceptMessage = message.getInterceptMessage();
    if (interceptMessage != null) {
      return messageBuilder.wrap(dispatch(message.getInterceptMessage(), null));
    }

    throw new IllegalArgumentException(
        format("No dispatch handler for this message type: %s", message));
  }

  private ExecMessage dispatch(ExecMessage requestMsg, @Nullable Long recordOffset) {
    final boolean isDirectRequest = recordOffset == null;

    ExecMessage replyMsg = null;
    try {
      replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, isDirectRequest);
    } catch (UnsupportedMessageException e) {
      logger.error("Unsupported incoming message", e);
    }
    if (replyMsg != null) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Invoker dispatched Exec Message w/uuid: {} and recordOffset: {}, reply uuid: {}",
            requestMsg.getMessageUuid(),
            recordOffset,
            replyMsg.getMessageUuid());
      }
      updateCounters();
    }
    return replyMsg;
  }

  private InterceptReply dispatch(InterceptMessage interceptMsg, @Nullable Long recordOffset) {
    final boolean isDirectRequest = recordOffset == null;
    final boolean result =
        incomingMessageDispatcher.incomingIntercept(interceptMsg, isDirectRequest);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker dispatched Intercept Request w/uuid: {} and recordOffset: {}, reply: {}",
          interceptMsg.getMessageUuid(),
          recordOffset,
          result);
    }

    final InterceptReply interceptReply =
        messageBuilder.buildInterceptReply(
            peerUuid, UUID.fromString(interceptMsg.getMessageUuid()), result);
    updateCounters();
    return interceptReply;
  }

  private void updateCounters() {
    requestsDispatched.getAndIncrement();
    messageBuilder.resetThreadLocalSequence();
  }

  final AtomicLong getRequestsDispatched() {
    return requestsDispatched;
  }
}
