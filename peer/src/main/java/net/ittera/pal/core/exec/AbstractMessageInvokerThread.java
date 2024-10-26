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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.ittera.pal.core.exec.java.IncomingMessageDispatcher;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.serdes.colfer.JsonSerializers;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/** Base class for Log and Peer Invoker threads. */
public abstract class AbstractMessageInvokerThread extends Thread {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final AtomicLong requestsDispatched = new AtomicLong(0);
  private final AtomicLong requestsDismissed = new AtomicLong(0);
  private final List<MessageDispatchListener> messageDispatchListeners = new ArrayList<>();

  // zmq stuff
  protected final ZContext zmqContext;

  protected final UUID peerUuid;
  private final IncomingMessageDispatcher incomingMessageDispatcher;
  protected final DispatcherConnector dispatcherConnector;
  protected final MessageBuilder messageBuilder;

  // used to serialize JSON-RPC messages
  protected Gson gson;

  AbstractMessageInvokerThread(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      DispatcherConnector dispatcherConnector,
      UUID peerUuid) {
    super(group, null, name);
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = dispatcherConnector;
    this.peerUuid = peerUuid;
    initializeGson();
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized message invoker thread named: {}", name);
    }
  }

  // Constructor exclusive for unit-testing -- to avoid ExecutorService and ThreadFactory
  // dependencies. NOTE: dispatcherConnector is set to null, since it's not required
  AbstractMessageInvokerThread(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.dispatcherConnector = null;
    this.peerUuid = peerUuid;
    initializeGson();
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized new message invoker thread");
    }
  }

  private void initializeGson() {
    this.gson =
        new GsonBuilder()
            .registerTypeAdapter(
                StaticFieldPutDone.class, new JsonSerializers.StaticFieldPutDoneAdapter())
            .registerTypeAdapter(
                InstanceFieldPutDone.class, new JsonSerializers.InstanceFieldPutDoneAdapter())
            .registerTypeAdapter(ReturnValue.class, new JsonSerializers.ReturnValueAdapter())
            .create();
  }

  protected final String getMessageUuid(Message msg) {
    final ExecMessage execMessage = msg.getExecMessage();
    if (execMessage != null) {
      return execMessage.getMessageUuid();
    }

    final ControlMessage controlMessage = msg.getControlMessage();
    if (controlMessage != null) {
      return controlMessage.getMessageUuid();
    }

    return null;
  }

  protected void closeConnections() {
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

  // This method returns void and is only used by LogMessageInvoker threads
  protected final void dispatch(Message message, Long recordOffset) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      dispatch(message.getExecMessage(), recordOffset);
      notifyMessageDispatched(message);
      return;
    }

    // Log message invoker can only dispatch ExecMessages
    throw new IllegalArgumentException(format("No handler for message: %s", message));
  }

  protected final Message dispatch(Message message) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      final Message reply = messageBuilder.wrap(dispatch(message.getExecMessage(), null));
      notifyMessageDispatched(message);
      return reply;
    }

    final ControlMessage controlMessage = message.getControlMessage();
    if (controlMessage != null) {
      final Message reply = messageBuilder.wrap(dispatch(message.getControlMessage()));
      notifyMessageDispatched(message);
      return reply;
    }

    throw new IllegalArgumentException(
        format("No dispatch handler for this message type: %s", message));
  }

  private ExecMessage dispatch(ExecMessage requestMsg, @Nullable Long recordOffset) {
    final boolean isDirectRequest = recordOffset == null;

    ExecMessage replyMsg;
    try {
      replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, isDirectRequest);
    } catch (UnsupportedMessageException e) {
      logger.warn("Unsupported incoming message", e);
      return null;
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker dispatched Exec Message w/uuid: {} and recordOffset: {}, reply uuid: {}",
          requestMsg.getMessageUuid(),
          recordOffset,
          replyMsg.getMessageUuid());
    }
    updateCounters();
    return replyMsg;
  }

  private ControlMessage dispatch(ControlMessage controlMsg) {
    ControlMessage replyMsg = null;
    try {
      replyMsg = incomingMessageDispatcher.incomingControlMessage(controlMsg);
    } catch (UnsupportedMessageException e) {
      logger.warn("Unsupported incoming message", e);
    }
    if (replyMsg != null) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Invoker dispatched Exec Message w/uuid: {} , reply uuid: {}",
            controlMsg.getMessageUuid(),
            replyMsg.getMessageUuid());
      }
      updateCounters();
    }
    return replyMsg;
  }

  private void updateCounters() {
    requestsDispatched.getAndIncrement();
    messageBuilder.resetThreadLocalSequence();
  }

  final AtomicLong getRequestsDispatched() {
    return requestsDispatched;
  }

  public void addMessageDispatchListener(MessageDispatchListener listener) {
    messageDispatchListeners.add(listener);
  }

  @SuppressWarnings("unused")
  public void removeMessageDispatchListener(MessageDispatchListener listener) {
    messageDispatchListeners.remove(listener);
  }

  private void notifyMessageDispatched(Message message) {
    for (MessageDispatchListener listener : messageDispatchListeners) {
      listener.onMessageDispatched(message);
    }
  }

  protected void logMessageDispatch(Message requestMsg, String replyId, long dispatchStart) {
    logMessageDispatch(getMessageUuid(requestMsg), replyId, dispatchStart);
  }

  protected void logMessageDispatch(Message requestMsg, Message replyMsg, long dispatchStart) {
    logMessageDispatch(getMessageUuid(requestMsg), getMessageUuid(replyMsg), dispatchStart);
  }

  protected void logMessageDispatch(String requestId, String replyId, long dispatchStart) {
    if (logger.isDebugEnabled()) {
      final long took = System.currentTimeMillis() - dispatchStart;
      logger.debug(
          "Dispatched and sent message w/id: {} in reply to request w/id: {} in {} ms",
          replyId,
          requestId,
          took);
    }
  }
}
