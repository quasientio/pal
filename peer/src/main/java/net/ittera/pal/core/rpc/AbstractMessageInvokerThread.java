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

package net.ittera.pal.core.rpc;

import static java.lang.String.format;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getMessageId;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.MetaMessage;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.types.MessageFamily;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.JsonSerializers;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/** Base class for Log and Peer Invoker threads. */
public abstract class AbstractMessageInvokerThread extends Thread {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  private final AtomicLong execRequestsDispatched = new AtomicLong(0);
  private final AtomicLong execRequestErrors = new AtomicLong(0);
  private final AtomicLong controlRequestsDispatched = new AtomicLong(0);
  private final AtomicLong controlRequestErrors = new AtomicLong(0);
  private final AtomicLong metaRequestsDispatched = new AtomicLong(0);
  private final AtomicLong metaRequestErrors = new AtomicLong(0);
  private final List<MessageDispatchListener> messageDispatchListeners = new ArrayList<>();

  // zmq stuff
  protected final ZContext zmqContext;

  protected final UUID peerUuid;
  private final IncomingMessageDispatcher incomingMessageDispatcher;
  protected final DispatcherConnector dispatcherConnector;
  protected final MessageBuilder messageBuilder;

  // used to serialize JSON-RPC messages
  protected Gson gson;

  enum DispatchResultType {
    OK,
    DISPATCH_ERROR
  }

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
          "Stopped invoker thread: {}"
              + ", EXEC requests: dispatched={}, errors={}"
              + "; CONTROL requests dispatched={}, errors={}"
              + "; META requests dispatched={}, errors={}",
          getName(),
          getExecRequestsDispatched(),
          getExecRequestErrors(),
          getControlRequestsDispatched(),
          getControlRequestErrors(),
          getMetaRequestsDispatched(),
          getMetaRequestErrors());
    }
  }

  @Override
  public abstract void run();

  // This method returns void and is only used by LogMessageInvoker threads
  protected final void dispatch(Message message, Long recordOffset) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      dispatch(
          message.getExecMessage(), MessageType.fromId(message.getMessageType()), recordOffset);
      notifyMessageDispatched(message);
      return;
    }

    // Log message invoker can only dispatch ExecMessages
    throw new IllegalArgumentException(format("No handler for message: %s", message));
  }

  protected final Message dispatch(Message message) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      final Message reply =
          messageBuilder.wrap(
              dispatch(
                  message.getExecMessage(), MessageType.fromId(message.getMessageType()), null));
      notifyMessageDispatched(message);
      return reply;
    }

    final ControlMessage controlMessage = message.getControlMessage();
    if (controlMessage != null) {
      final Message reply = messageBuilder.wrap(dispatch(message.getControlMessage()));
      notifyMessageDispatched(message);
      return reply;
    }

    final MetaMessage metaMessage = message.getMetaMessage();
    if (metaMessage != null) {
      final Message reply = messageBuilder.wrap(dispatch(message.getMetaMessage()));
      notifyMessageDispatched(message);
      return reply;
    }

    throw new IllegalArgumentException(
        format("No dispatch handler for this message type: %s", message));
  }

  private ExecMessage dispatch(
      ExecMessage requestMsg, MessageType messageType, @Nullable Long recordOffset) {
    final boolean isDirectRequest = recordOffset == null;
    ExecMessage replyMsg;
    boolean dispatched = false;
    try {
      replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, messageType, isDirectRequest);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.EXEC, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker successfully dispatched Exec Message w/id: {} and recordOffset: {}, reply id: {}",
          requestMsg.getMessageId(),
          recordOffset,
          replyMsg.getMessageId());
    }

    return replyMsg;
  }

  private ControlMessage dispatch(ControlMessage controlMsg) {
    boolean dispatched = false;
    ControlMessage replyMsg;
    try {
      replyMsg = incomingMessageDispatcher.incomingControlMessage(controlMsg);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.CONTROL, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker successfully dispatched Control Message w/id: {} , reply id: {}",
          controlMsg.getMessageId(),
          replyMsg.getMessageId());
    }
    return replyMsg;
  }

  private MetaMessage dispatch(MetaMessage metaMessage) {
    boolean dispatched = false;
    MetaMessage replyMsg;
    try {
      replyMsg = incomingMessageDispatcher.incomingMetaMessage(metaMessage);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.META, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker successfully dispatched Meta Message w/id: {} , reply id: {}",
          metaMessage.getMessageId(),
          replyMsg.getMessageId());
    }
    return replyMsg;
  }

  private void endDispatch(MessageFamily dispatchedMessageType, DispatchResultType resultType) {
    switch (dispatchedMessageType) {
      case EXEC:
        if (resultType.equals(DispatchResultType.OK)) {
          execRequestsDispatched.getAndIncrement();
        } else {
          execRequestErrors.getAndIncrement();
        }
        break;
      case CONTROL:
        if (resultType.equals(DispatchResultType.OK)) {
          controlRequestsDispatched.getAndIncrement();
        } else {
          controlRequestErrors.getAndIncrement();
        }
        break;
      case META:
        if (resultType.equals(DispatchResultType.OK)) {
          metaRequestsDispatched.getAndIncrement();
        } else {
          metaRequestErrors.getAndIncrement();
        }
        break;
      default:
    }

    // reset MessageBuilder's dispatch sequence
    messageBuilder.resetThreadLocalSequence();
  }

  final long getRequestsDispatched() {
    return getExecRequestsDispatched()
        + getControlRequestsDispatched()
        + getMetaRequestsDispatched();
  }

  final long getExecRequestsDispatched() {
    return execRequestsDispatched.get();
  }

  final long getControlRequestsDispatched() {
    return controlRequestsDispatched.get();
  }

  final long getMetaRequestsDispatched() {
    return metaRequestsDispatched.get();
  }

  final long getRequestErrors() {
    return getExecRequestErrors() + getControlRequestErrors() + getMetaRequestErrors();
  }

  final long getExecRequestErrors() {
    return execRequestErrors.get();
  }

  final long getControlRequestErrors() {
    return controlRequestErrors.get();
  }

  final long getMetaRequestErrors() {
    return metaRequestErrors.get();
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
    logMessageDispatch(getMessageId(requestMsg), replyId, dispatchStart);
  }

  protected void logMessageDispatch(Message requestMsg, Message replyMsg, long dispatchStart) {
    logMessageDispatch(getMessageId(requestMsg), getMessageId(replyMsg), dispatchStart);
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
