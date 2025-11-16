/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.core.internal.messages.InterceptEventMsg;
import com.quasient.pal.core.service.ConnectedService;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * InterceptMatcher is responsible for managing intercept registration requests and providing
 * matching intercept messages.
 *
 * <p>It listens for registration and unregistration events using a ZeroMQ REP socket and maintains
 * a mapping of intercept requests organized by intercept type. It also provides methods to retrieve
 * intercepts based on execution messages, message types, and execution phases.
 */
@Singleton
public class InterceptMatcher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptMatcher.class);

  /** ZeroMQ REP socket used to receive intercept registration and unregistration events. */
  private Socket registerSocket;

  /** The network address endpoint used for intercept registration communication. */
  private final String interceptRegAddress;

  /** Response code indicating successful intercept registration. */
  public static final String REGISTER_OK_RESPONSE = "0";

  /** Response code indicating successful intercept unregistration. */
  public static final String UNREGISTER_OK_RESPONSE = "0";

  /** Response code indicating that the intercept registration request is a duplicate. */
  public static final String REGISTER_DUP_RESPONSE = "1";

  /**
   * Response code indicating an error occurred while parsing the intercept registration request.
   */
  public static final String REGISTER_PARSING_ERROR_RESPONSE = "2";

  /** Response code indicating an unknown error during intercept registration. */
  public static final String REGISTER_UNKNOWN_ERROR_RESPONSE = "3";

  /** Response code indicating an unknown error during intercept unregistration. */
  public static final String UNREGISTER_UNKNOWN_ERROR_RESPONSE = "4";

  /** Map containing registered intercept requests organized by their intercept type. */
  private final Map<InterceptType, InterceptRequests> allIntercepts =
      new EnumMap<>(InterceptType.class);

  /**
   * Constructs a new InterceptMatcher instance.
   *
   * <p>This constructor initializes the intercept matcher by setting up the underlying connected
   * service and preparing the registry for intercept registration requests across all intercept
   * types.
   *
   * @param peerUuid the unique identifier for this peer in the runtime system
   * @param context the ZeroMQ context used for socket communication
   * @param syncSocketAddress the address used for service synchronization
   * @param serviceThreadGroup the thread group managing service threads
   * @param serviceName the name of the intercept service
   * @param interceptRegAddress the network address endpoint for intercept registration
   */
  @Inject
  public InterceptMatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Intercepts.service") String serviceName,
      @Named("intercepts.reg") String interceptRegAddress) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.interceptRegAddress = interceptRegAddress;
    // initialize intercept registry
    for (InterceptType interceptType : InterceptType.values()) {
      allIntercepts.put(interceptType, new InterceptRequests());
    }
  }

  /**
   * Opens the necessary connections for intercept registration.
   *
   * <p>This method creates a ZeroMQ REP socket and binds it to the configured intercept
   * registration address.
   */
  @Override
  protected void openConnections() {
    registerSocket = zmqContext.createSocket(SocketType.REP);
    registerSocket.bind(interceptRegAddress);
  }

  /**
   * Registers an incoming intercept request.
   *
   * <p>The method identifies the proper intercept request registry based on the intercept type
   * contained in the provided message, and registers the intercept request. A duplicate request
   * will trigger a DuplicateInterceptException.
   *
   * @param incomingInterceptMessage the intercept message containing registration data
   * @throws DuplicateInterceptException if an intercept request with the same identifier is already
   *     registered
   */
  private void registerInterceptRequest(InterceptMessage incomingInterceptMessage)
      throws DuplicateInterceptException {
    InterceptRequests registeredIntercepts =
        allIntercepts.get(InterceptType.fromByte(incomingInterceptMessage.getInterceptType()));
    registeredIntercepts.registerInterceptRequest(incomingInterceptMessage);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Registered incoming intercept message: {}",
          ColferUtils.format(incomingInterceptMessage));
    }
  }

  /**
   * Retrieves intercept messages that match the given execution parameters.
   *
   * <p>Depending on the specified execution phase, the method aggregates intercept requests
   * corresponding to pre-execution (BEFORE) or post-execution (AFTER) phases. If an unsupported
   * execution phase is provided, an UnsupportedOperationException is thrown.
   *
   * @param className the fully qualified class name
   * @param executableName the method/field/constructor name
   * @param parameterTypes the parameter types (null for fields)
   * @param messageType the type of message for which intercepts should be matched
   * @param phase the execution phase during which intercepts should be gathered (BEFORE or AFTER)
   * @return a list of intercept messages that match the provided criteria
   * @throws UnsupportedOperationException if the specified execution phase is not supported
   */
  public List<InterceptMessage> getMatchingIntercepts(
      String className,
      String executableName,
      String[] parameterTypes,
      MessageType messageType,
      ExecPhase phase) {
    if (ExecPhase.BEFORE.equals(phase)) {
      final List<InterceptMessage> beforeIntercepts =
          allIntercepts
              .get(InterceptType.BEFORE)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType);
      final List<InterceptMessage> beforeAsyncIntercepts =
          allIntercepts
              .get(InterceptType.BEFORE_ASYNC)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType);
      final List<InterceptMessage> aroundIntercepts =
          allIntercepts
              .get(InterceptType.AROUND)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType);
      final List<InterceptMessage> interceptMessages =
          new ArrayList<>(
              beforeIntercepts.size() + beforeAsyncIntercepts.size() + aroundIntercepts.size());
      interceptMessages.addAll(beforeIntercepts);
      interceptMessages.addAll(beforeAsyncIntercepts);
      interceptMessages.addAll(aroundIntercepts);
      return interceptMessages;
    }
    if (ExecPhase.AFTER.equals(phase)) {
      final List<InterceptMessage> afterIntercepts =
          allIntercepts
              .get(InterceptType.AFTER)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType);
      final List<InterceptMessage> afterAsyncIntercepts =
          allIntercepts
              .get(InterceptType.AFTER_ASYNC)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType);
      final List<InterceptMessage> interceptMessages =
          new ArrayList<>(afterIntercepts.size() + afterAsyncIntercepts.size());
      interceptMessages.addAll(afterIntercepts);
      interceptMessages.addAll(afterAsyncIntercepts);
      return interceptMessages;
    }

    throw new UnsupportedOperationException("Unsupported execution phase: " + phase);
  }

  /**
   * Continuously polls for intercept registration events and processes them.
   *
   * <p>This method runs in a loop, receiving intercept event messages and dispatching them for
   * registration or unregistration. It terminates the polling when the thread is interrupted or
   * when critical errors occur.
   */
  @Override
  public final void run() {
    while (!Thread.interrupted()) {
      // poll registerSocket and dispatch
      try {
        registerNewAndGoneIntercepts();
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. No more polling for new intercepts.");
          }
          break;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. No more polling for new intercepts.");
          }
          break;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving message. No more polling for new intercepts.", e);
        break;
      }
    }
  }

  /**
   * Processes new intercept registration and unregistration events received over the network.
   *
   * <p>This method reads an intercept event message from the REP socket. For registration requests,
   * it parses the message, registers the intercept, and sends a response code. For unregistration
   * requests, it removes the intercept from all registries and sends the corresponding response.
   */
  private void registerNewAndGoneIntercepts() {
    InterceptEventMsg interceptEventMsg = InterceptEventMsg.receive(registerSocket, true);
    if (interceptEventMsg == null) {
      return;
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Received new intercept evt message ({} bytes)", interceptEventMsg.getSize());
    }
    // parse message
    if (interceptEventMsg.getType().equals(InterceptEventMsg.Type.REGISTER)) {
      InterceptMessage interceptMessage = null;
      try {
        interceptMessage = new InterceptMessage();
        interceptMessage.unmarshal(interceptEventMsg.getBody(), 0);
      } catch (Exception e) {
        logger.error("Error parsing intercept request message", e);
        registerSocket.send(REGISTER_PARSING_ERROR_RESPONSE);
      }
      if (interceptMessage != null) {
        try {
          registerInterceptRequest(interceptMessage);
          registerSocket.send(REGISTER_OK_RESPONSE);
        } catch (DuplicateInterceptException e) {
          logger.warn("Cannot register duplicate intercept request", e);
          registerSocket.send(REGISTER_DUP_RESPONSE);
        } catch (Exception e) {
          registerSocket.send(REGISTER_UNKNOWN_ERROR_RESPONSE);
        }
      }
    } else { // Type.UNREGISTER
      String interceptMessageId = interceptEventMsg.getInterceptMessageId();
      if (interceptMessageId == null) {
        logger.error("Intercept id is null. Cannot unregister intercept request.");
        registerSocket.send(UNREGISTER_UNKNOWN_ERROR_RESPONSE);
        return;
      }
      allIntercepts
          .values()
          .forEach(
              interceptRequests ->
                  interceptRequests.unregisterInterceptRequest(interceptMessageId));
      registerSocket.send(UNREGISTER_OK_RESPONSE);
    }
  }

  /**
   * Closes established connections associated with intercept registration.
   *
   * <p>This method safely closes the ZeroMQ REP socket used for intercept communication and logs
   * errors if they occur.
   */
  @Override
  protected void closeConnections() {
    closeConnection(registerSocket, "Error closing register (REP) socket");
  }
}
