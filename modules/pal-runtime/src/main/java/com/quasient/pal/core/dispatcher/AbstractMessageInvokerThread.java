/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.dispatcher;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageId;
import static java.lang.String.format;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InstanceFieldPutDone;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.MetaMessage;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.colfer.StaticFieldPutDone;
import com.quasient.pal.messages.types.MessageFamily;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.JsonSerializers;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

/**
 * An abstract thread responsible for dispatching different types of messages (EXEC, CONTROL, and
 * META) to the appropriate handlers via an incoming message dispatcher. This base class is used by
 * both Log and Socket RPC Invoker threads to execute remote calls and handle dispatch errors while
 * keeping track of dispatch metrics.
 */
public abstract class AbstractMessageInvokerThread extends Thread {

  /** Logger instance. */
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** Counter tracking the number of EXEC requests successfully dispatched. */
  private final AtomicLong execRequestsDispatched = new AtomicLong(0);

  /** Counter tracking the number of EXEC dispatch errors encountered. */
  private final AtomicLong execRequestErrors = new AtomicLong(0);

  /** Counter tracking the number of CONTROL requests successfully dispatched. */
  private final AtomicLong controlRequestsDispatched = new AtomicLong(0);

  /** Counter tracking the number of CONTROL dispatch errors encountered. */
  private final AtomicLong controlRequestErrors = new AtomicLong(0);

  /** Counter tracking the number of META requests successfully dispatched. */
  private final AtomicLong metaRequestsDispatched = new AtomicLong(0);

  /** Counter tracking the number of META dispatch errors encountered. */
  private final AtomicLong metaRequestErrors = new AtomicLong(0);

  /** List of listeners to be notified whenever a message is dispatched. */
  private final List<MessageDispatchListener> messageDispatchListeners = new ArrayList<>();

  // zmq stuff

  /** ZeroMQ context used for managing socket communications within the thread. */
  protected final ZContext zmqContext;

  /** Unique identifier representing the peer associated with this invoker thread. */
  protected final UUID peerUuid;

  /** Dispatcher handling incoming messages and delegating them to the appropriate services. */
  private final IncomingMessageDispatcher incomingMessageDispatcher;

  /** Connector for managing communication with the dispatcher; may be null during unit testing. */
  protected final OutboundMessageGateway outboundMessageGateway;

  /** Builder component used to create and wrap messages for dispatch responses. */
  protected final MessageBuilder messageBuilder;

  /**
   * Gson instance used for serializing and deserializing JSON-RPC messages with custom type
   * adapters.
   */
  protected Gson gson;

  /** Enumeration representing the outcome of a message dispatch operation. */
  enum DispatchResultType {
    /** Indicates that the dispatch was successful. */
    OK,
    /** Indicates that an error occurred during dispatch. */
    DISPATCH_ERROR
  }

  /**
   * Constructs a new message invoker thread with the specified configuration.
   *
   * @param group the ThreadGroup to which this thread belongs
   * @param name the name of the thread
   * @param zmqContext the ZeroMQ context used for socket communications
   * @param messageBuilder the builder for creating and wrapping messages
   * @param incomingMessageDispatcher the dispatcher handling incoming messages
   * @param outboundMessageGateway the gateway responsible for maintaining dispatcher connections
   * @param peerUuid the unique identifier for the associated peer
   */
  AbstractMessageInvokerThread(
      ThreadGroup group,
      String name,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      OutboundMessageGateway outboundMessageGateway,
      UUID peerUuid) {
    super(group, null, name);
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.outboundMessageGateway = outboundMessageGateway;
    this.peerUuid = peerUuid;
    initializeGson();
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized message invoker thread named: {}", name);
    }
  }

  /**
   * Constructor intended for unit testing that avoids dependencies on ExecutorService and
   * ThreadFactory. In this case, the outboundMessageGateway is set to null as it is not required.
   *
   * @param zmqContext the ZeroMQ context used for socket communications
   * @param messageBuilder the builder for creating and wrapping messages
   * @param incomingMessageDispatcher the dispatcher handling incoming messages
   * @param peerUuid the unique identifier for the associated peer
   */
  AbstractMessageInvokerThread(
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      IncomingMessageDispatcher incomingMessageDispatcher,
      UUID peerUuid) {
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.outboundMessageGateway = null;
    this.peerUuid = peerUuid;
    initializeGson();
    if (logger.isDebugEnabled()) {
      logger.debug("Initialized new message invoker thread");
    }
  }

  /**
   * Initializes and configures the Gson instance with custom type adapters used for JSON-RPC
   * serialization.
   */
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

  /**
   * Closes connections managed by the {@link OutboundMessageGateway} and logs dispatch metrics.
   * This method should be called during thread shutdown to ensure proper resource cleanup.
   */
  protected void closeConnections() {
    if (outboundMessageGateway != null) {
      try {
        outboundMessageGateway.closeThreadLocalSockets();
      } catch (Exception e) {
        logger.debug("Error closing dispatcher local socket", e);
      }
    }

    if (logger.isInfoEnabled()) {
      logger.info(
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

  /**
   * {@inheritDoc}
   *
   * <p>Subclasses must implement this method to define their specific message dispatching logic.
   */
  @Override
  public abstract void run();

  /**
   * Dispatches a Message based on its internal type (ExecMessage, ControlMessage, or MetaMessage)
   * and returns the corresponding wrapped response. Listeners are notified after successful
   * dispatch.
   *
   * @param message the message to be dispatched
   * @param channelType the channel (transport) through which the message was received
   * @return the response message wrapped via the MessageBuilder
   * @throws IllegalArgumentException if the message type is not supported for dispatching
   */
  protected final Message dispatch(Message message, MessageChannelType channelType) {
    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage != null) {
      final Message response =
          messageBuilder.wrap(
              dispatch(
                  message.getExecMessage(),
                  MessageType.fromId(message.getMessageType()),
                  channelType,
                  null));
      notifyMessageDispatched(message);
      return response;
    }

    final ControlMessage controlMessage = message.getControlMessage();
    if (controlMessage != null) {
      final Message response = messageBuilder.wrap(dispatch(message.getControlMessage()));
      notifyMessageDispatched(message);
      return response;
    }

    final MetaMessage metaMessage = message.getMetaMessage();
    if (metaMessage != null) {
      final Message response = messageBuilder.wrap(dispatch(message.getMetaMessage()));
      notifyMessageDispatched(message);
      return response;
    }

    throw new IllegalArgumentException(
        format("No dispatch handler for this message type: %s", message));
  }

  /**
   * Dispatches the given ExecMessage by delegating to the incoming message dispatcher.
   *
   * @param requestMsg the ExecMessage to be dispatched
   * @param messageType the type of the message derived from the message's type identifier
   * @param channelType the channel/transport from which the message came
   * @param recordOffset the record offset associated with the message, when coming from a Log
   * @return the response ExecMessage generated after processing
   */
  protected ExecMessage dispatch(
      ExecMessage requestMsg,
      MessageType messageType,
      MessageChannelType channelType,
      @Nullable Long recordOffset) {
    ExecMessage responseMessage;
    boolean dispatched = false;
    try {
      responseMessage =
          incomingMessageDispatcher.incomingCall(requestMsg, messageType, channelType);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.EXEC, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Successfully dispatched Exec Message w/id: {} and recordOffset: {}, response id: {}",
          requestMsg.getMessageId(),
          recordOffset,
          responseMessage.getMessageId());
    }

    return responseMessage;
  }

  /**
   * Dispatches the provided ControlMessage by delegating to the incoming message dispatcher,
   * updates dispatch metrics, and logs the operation's outcome.
   *
   * @param controlMsg the ControlMessage to be processed
   * @return the response ControlMessage received from the dispatcher
   */
  private ControlMessage dispatch(ControlMessage controlMsg) {
    boolean dispatched = false;
    ControlMessage responseMsg;
    try {
      responseMsg = incomingMessageDispatcher.incomingControlMessage(controlMsg);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.CONTROL, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker successfully dispatched Control Message w/id: {} , response id: {}",
          controlMsg.getMessageId(),
          responseMsg.getMessageId());
    }
    return responseMsg;
  }

  /**
   * Dispatches the given MetaMessage by utilizing the incoming message dispatcher, updates relevant
   * dispatch metrics, and logs the dispatch details.
   *
   * @param metaMessage the MetaMessage to be dispatched
   * @return the response MetaMessage generated after processing
   */
  private MetaMessage dispatch(MetaMessage metaMessage) {
    boolean dispatched = false;
    MetaMessage responseMsg;
    try {
      responseMsg = incomingMessageDispatcher.incomingMetaMessage(metaMessage);
      dispatched = true;
    } finally {
      DispatchResultType resultType =
          dispatched ? DispatchResultType.OK : DispatchResultType.DISPATCH_ERROR;
      endDispatch(MessageFamily.META, resultType);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Invoker successfully dispatched Meta Message w/id: {} , response id: {}",
          metaMessage.getMessageId(),
          responseMsg.getMessageId());
    }
    return responseMsg;
  }

  /**
   * Finalizes the dispatch process by updating dispatch counts for the specified message family
   * based on the result of the dispatch operation. It also resets the dispatch sequence in the
   * MessageBuilder.
   *
   * @param dispatchedMessageType the family of the dispatched message (EXEC, CONTROL, or META)
   * @param resultType the outcome of the dispatch operation (OK or DISPATCH_ERROR)
   */
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

  /**
   * Retrieves the total number of requests dispatched across all message families.
   *
   * @return the sum of dispatched EXEC, CONTROL, and META requests
   */
  final long getRequestsDispatched() {
    return getExecRequestsDispatched()
        + getControlRequestsDispatched()
        + getMetaRequestsDispatched();
  }

  /**
   * Retrieves the number of successfully dispatched EXEC requests.
   *
   * @return the count of dispatched EXEC requests
   */
  final long getExecRequestsDispatched() {
    return execRequestsDispatched.get();
  }

  /**
   * Retrieves the number of successfully dispatched CONTROL requests.
   *
   * @return the count of dispatched CONTROL requests
   */
  final long getControlRequestsDispatched() {
    return controlRequestsDispatched.get();
  }

  /**
   * Retrieves the number of successfully dispatched META requests.
   *
   * @return the count of dispatched META requests
   */
  final long getMetaRequestsDispatched() {
    return metaRequestsDispatched.get();
  }

  /**
   * Retrieves the total number of dispatch errors across all message families.
   *
   * @return the sum of EXEC, CONTROL, and META dispatch errors
   */
  final long getRequestErrors() {
    return getExecRequestErrors() + getControlRequestErrors() + getMetaRequestErrors();
  }

  /**
   * Retrieves the number of dispatch errors encountered for EXEC messages.
   *
   * @return the count of EXEC dispatch errors
   */
  final long getExecRequestErrors() {
    return execRequestErrors.get();
  }

  /**
   * Retrieves the number of dispatch errors encountered for CONTROL messages.
   *
   * @return the count of CONTROL dispatch errors
   */
  final long getControlRequestErrors() {
    return controlRequestErrors.get();
  }

  /**
   * Retrieves the number of dispatch errors encountered for META messages.
   *
   * @return the count of META dispatch errors
   */
  final long getMetaRequestErrors() {
    return metaRequestErrors.get();
  }

  /**
   * Registers a MessageDispatchListener to be notified when a message has been dispatched.
   *
   * @param listener the MessageDispatchListener to register
   */
  public void addMessageDispatchListener(MessageDispatchListener listener) {
    messageDispatchListeners.add(listener);
  }

  /**
   * Removes a previously registered MessageDispatchListener.
   *
   * @param listener the MessageDispatchListener to remove
   */
  @SuppressWarnings("unused")
  public void removeMessageDispatchListener(MessageDispatchListener listener) {
    messageDispatchListeners.remove(listener);
  }

  /**
   * Notifies all registered listeners that the specified message has been dispatched.
   *
   * @param message the message that has been processed and dispatched
   */
  protected void notifyMessageDispatched(Message message) {
    for (MessageDispatchListener listener : messageDispatchListeners) {
      listener.onMessageDispatched(message);
    }
  }

  /**
   * Logs detailed information regarding message dispatch using the provided request message,
   * response identifier, and dispatch start time.
   *
   * @param requestMsg the original request message whose identifier will be extracted
   * @param responseId the identifier of the response message
   * @param dispatchStart the timestamp marking the beginning of the dispatch process
   */
  protected void logMessageDispatch(Message requestMsg, String responseId, long dispatchStart) {
    logMessageDispatch(getMessageId(requestMsg), responseId, dispatchStart);
  }

  /**
   * Logs the dispatch event using both the request and response Message objects to extract their
   * identifiers. It calculates and logs the time taken to dispatch the message.
   *
   * @param requestMsg the original request message
   * @param responseMessage the response message generated after dispatch
   * @param dispatchStart the timestamp marking the beginning of the dispatch process
   */
  protected void logMessageDispatch(
      Message requestMsg, Message responseMessage, long dispatchStart) {
    logMessageDispatch(getMessageId(requestMsg), getMessageId(responseMessage), dispatchStart);
  }

  /**
   * Logs details about the dispatch event including request and response identifiers and the
   * elapsed time since the given start timestamp.
   *
   * @param requestId the identifier of the request message
   * @param responseId the identifier of the response message
   * @param dispatchStart the timestamp marking the start of the dispatch process
   */
  protected void logMessageDispatch(String requestId, String responseId, long dispatchStart) {
    if (logger.isDebugEnabled()) {
      final long took = System.currentTimeMillis() - dispatchStart;
      logger.debug(
          "Dispatched and sent message w/id: {} in response to request w/id: {} in {} ms",
          responseId,
          requestId,
          took);
    }
  }

  /**
   * Logs details about the dispatch event including request identifier and the elapsed time since
   * the given start timestamp.
   *
   * @param requestId the identifier of the request message
   * @param dispatchStart the timestamp marking the start of the dispatch process
   */
  protected void logMessageDispatch(String requestId, long dispatchStart) {
    if (logger.isDebugEnabled()) {
      final long took = System.currentTimeMillis() - dispatchStart;
      logger.debug("Dispatched request w/id: {} in {} ms", requestId, took);
    }
  }
}
