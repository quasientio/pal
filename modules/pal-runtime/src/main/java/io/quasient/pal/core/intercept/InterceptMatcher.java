/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.internal.messages.InterceptEventMsg;
import io.quasient.pal.core.service.ConnectedService;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jctools.queues.MessagePassingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
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
 *
 * <p>This class also polls the pending activations queue for intercepts that have completed their
 * drain phase and are ready for registration. This allows multiple drain operations to run in
 * parallel while maintaining single-writer semantics for the intercept registry.
 */
@Singleton
public class InterceptMatcher extends ConnectedService {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptMatcher.class);

  /** ZeroMQ REP socket used to receive intercept registration and unregistration events. */
  private Socket registerSocket;

  /** ZeroMQ poller for non-blocking socket polling. */
  private Poller poller;

  /** Poller index for the register socket. */
  private int registerSocketPollIndex;

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

  /**
   * Response code indicating that intercept activation is pending asynchronously.
   *
   * <p>This response is returned when in-flight tracking is enabled and the intercept requires
   * drain before activation. The actual activation will happen in a background thread once
   * quiescence is achieved.
   */
  public static final String REGISTER_ASYNC_PENDING_RESPONSE = "A";

  /** Polling timeout in milliseconds for non-blocking socket polling. */
  private static final long POLL_TIMEOUT_MS = 10;

  /** Maximum number of pending activations to process per poll iteration. */
  private static final int MAX_PENDING_PER_POLL = 16;

  /** Map containing registered intercept requests organized by their intercept type. */
  private final Map<InterceptType, InterceptRequests> allIntercepts =
      new EnumMap<>(InterceptType.class);

  /**
   * The coordinator that orchestrates safe intercept activation with optional in-flight tracking
   * and drain mechanism.
   */
  private final InterceptActivationCoordinator activationCoordinator;

  /**
   * Queue for pending intercept activations that have completed drain and are ready for
   * registration.
   *
   * <p>This MPSC queue is shared with {@link InterceptActivationCoordinator}. After quiescence is
   * achieved, drain threads enqueue completed activations here. This class (the single consumer)
   * polls the queue and registers the intercepts, maintaining single-writer semantics for the
   * intercept registry.
   */
  private final MessagePassingQueue<PendingInterceptActivation> pendingActivationsQueue;

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
   * @param activationCoordinator the coordinator for safe intercept activation
   * @param pendingActivationsQueue the MPSC queue for completed activations
   */
  @Inject
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Queue intentionally shared between coordinator and matcher for MPSC pattern")
  public InterceptMatcher(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Intercepts.service") String serviceName,
      @Named("intercepts.reg") String interceptRegAddress,
      InterceptActivationCoordinator activationCoordinator,
      @Named("intercept.pending.activations.queue")
          MessagePassingQueue<PendingInterceptActivation> pendingActivationsQueue) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.interceptRegAddress = interceptRegAddress;
    this.activationCoordinator = activationCoordinator;
    this.pendingActivationsQueue = pendingActivationsQueue;
    // initialize intercept registry
    for (InterceptType interceptType : InterceptType.values()) {
      allIntercepts.put(interceptType, new InterceptRequests());
    }
  }

  /**
   * Opens the necessary connections for intercept registration.
   *
   * <p>This method creates a ZeroMQ REP socket and binds it to the configured intercept
   * registration address. It also sets up a poller for non-blocking socket polling.
   */
  @Override
  protected void openConnections() {
    registerSocket = zmqContext.createSocket(SocketType.REP);
    registerSocket.bind(interceptRegAddress);

    // Set up poller for non-blocking socket polling
    poller = zmqContext.createPoller(1);
    registerSocketPollIndex = poller.register(registerSocket, ZMQ.Poller.POLLIN);
  }

  /**
   * Registers an incoming intercept request.
   *
   * <p>The method identifies the proper intercept request registry based on the intercept type
   * contained in the provided message, and registers the intercept request. A duplicate request
   * will trigger a DuplicateInterceptException.
   *
   * <p>This method is package-private to allow {@link InterceptActivationCoordinator} to call it
   * after quiescence is achieved when in-flight tracking is enabled.
   *
   * @param incomingInterceptMessage the intercept message containing registration data
   * @throws DuplicateInterceptException if an intercept request with the same identifier is already
   *     registered
   */
  void registerInterceptRequest(InterceptMessage incomingInterceptMessage)
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
   * Thread-local reusable list for consolidating matching intercept messages across intercept
   * types.
   *
   * <p>This avoids allocating a new {@code ArrayList} and performing {@code addAll()} on every
   * intercept check. Callers must consume the returned list before any operation that could
   * re-enter this method on the same thread.
   */
  private static final ThreadLocal<ArrayList<InterceptMessage>> TL_CONSOLIDATED =
      ThreadLocal.withInitial(ArrayList::new);

  /**
   * Retrieves intercept messages that match the given execution parameters.
   *
   * <p>Depending on the specified execution phase, the method aggregates intercept requests
   * corresponding to pre-execution (BEFORE) or post-execution (AFTER) phases. If an unsupported
   * execution phase is provided, an UnsupportedOperationException is thrown.
   *
   * <p>The returned list is a thread-local reusable list. Callers must consume it before any
   * operation that could re-enter this method on the same thread.
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
    ArrayList<InterceptMessage> consolidated = TL_CONSOLIDATED.get();
    consolidated.clear();

    if (ExecPhase.BEFORE.equals(phase)) {
      appendMatches(
          consolidated,
          allIntercepts
              .get(InterceptType.BEFORE)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType));
      appendMatches(
          consolidated,
          allIntercepts
              .get(InterceptType.BEFORE_ASYNC)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType));
      appendMatches(
          consolidated,
          allIntercepts
              .get(InterceptType.AROUND)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType));
      return consolidated;
    }
    if (ExecPhase.AFTER.equals(phase)) {
      appendMatches(
          consolidated,
          allIntercepts
              .get(InterceptType.AFTER)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType));
      appendMatches(
          consolidated,
          allIntercepts
              .get(InterceptType.AFTER_ASYNC)
              .getMatchingIntercepts(className, executableName, parameterTypes, messageType));
      return consolidated;
    }

    throw new UnsupportedOperationException("Unsupported execution phase: " + phase);
  }

  /**
   * Appends all elements from the source list to the target list using indexed iteration.
   *
   * <p>This is used instead of {@link List#addAll} to avoid intermediate array copies. The source
   * list may be a thread-local reusable list from {@link InterceptRequests}, so its contents must
   * be copied into the target before the next call to {@link
   * InterceptRequests#getMatchingIntercepts}.
   *
   * @param target the list to append to
   * @param source the list to copy from
   */
  private static void appendMatches(List<InterceptMessage> target, List<InterceptMessage> source) {
    for (int i = 0; i < source.size(); i++) {
      target.add(source.get(i));
    }
  }

  /**
   * Continuously polls for intercept registration events and pending activations, processing them.
   *
   * <p>This method runs in a loop, using a ZMQ poller to non-blocking poll the REP socket for
   * intercept event messages. It also polls the pending activations queue for intercepts that have
   * completed their drain phase and are ready for registration.
   *
   * <p>The dual-polling design allows:
   *
   * <ul>
   *   <li>Multiple drain operations to run in parallel (in the activation coordinator's thread
   *       pool)
   *   <li>Single-writer semantics for the intercept registry (only this thread registers)
   *   <li>Non-blocking operation (can process both sockets and queue in the same loop iteration)
   * </ul>
   *
   * <p>It terminates the polling when the thread is interrupted or when critical errors occur.
   */
  @Override
  public final void run() {
    while (!Thread.interrupted()) {
      try {
        // Poll socket with timeout to allow checking the pending queue regularly
        int pollResult = poller.poll(POLL_TIMEOUT_MS);

        if (pollResult == -1) {
          // Poll was interrupted
          if (logger.isDebugEnabled()) {
            logger.debug("Poller interrupted, exiting run loop");
          }
          break;
        }

        // Check if socket has data
        if (poller.pollin(registerSocketPollIndex)) {
          processSocketEvent();
        }

        // Process pending activations from the queue (up to MAX_PENDING_PER_POLL)
        processPendingActivations();

      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during polling. No more polling for new intercepts.");
          }
          break;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during polling. No more polling for new intercepts.");
          }
          break;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error in intercept processing loop. No more polling for new intercepts.", e);
        break;
      }
    }
  }

  /**
   * Processes a single intercept event from the socket.
   *
   * <p>This method is called when the poller indicates data is available on the register socket.
   */
  private void processSocketEvent() {
    try {
      registerNewAndGoneIntercepts();
    } catch (Exception e) {
      logger.error("Error processing socket event", e);
      // Continue processing - don't break the loop for single event errors
    }
  }

  /**
   * Processes pending activations from the MPSC queue.
   *
   * <p>This method drains pending activations from the queue and registers them. It processes up to
   * {@link #MAX_PENDING_PER_POLL} activations per call to avoid blocking the socket polling for too
   * long.
   */
  private void processPendingActivations() {
    int processed = 0;
    PendingInterceptActivation pending;

    while (processed < MAX_PENDING_PER_POLL && (pending = pendingActivationsQueue.poll()) != null) {
      try {
        registerInterceptRequest(pending.interceptMessage());
        processed++;
        if (logger.isInfoEnabled()) {
          logger.info(
              "Registered pending intercept for {}.{} (from async drain)",
              pending.classPattern(),
              pending.methodPattern());
        }
      } catch (DuplicateInterceptException e) {
        if (logger.isWarnEnabled()) {
          logger.warn(
              "Cannot register pending duplicate intercept for {}.{}",
              pending.classPattern(),
              pending.methodPattern(),
              e);
        }
      } catch (Exception e) {
        logger.error(
            "Error registering pending intercept for {}.{}",
            pending.classPattern(),
            pending.methodPattern(),
            e);
      } finally {
        // Signal the drain thread that registration is complete (or failed).
        // This must happen regardless of success/failure so the drain thread can stop fencing.
        pending.signalRegistered();
      }
    }

    if (processed > 0 && logger.isDebugEnabled()) {
      logger.debug("Processed {} pending activations from queue", processed);
    }
  }

  /**
   * Processes new intercept registration and unregistration events received over the network.
   *
   * <p>This method reads an intercept event message from the REP socket. For registration requests,
   * it parses the message and delegates to {@link InterceptActivationCoordinator} which handles the
   * coordination of in-flight tracking, fencing, and drain (if enabled) before registering the
   * intercept. For unregistration requests, it removes the intercept from all registries and sends
   * the corresponding response.
   *
   * <p>The registration flow:
   *
   * <ol>
   *   <li>Receive and parse the intercept message
   *   <li>Call activationCoordinator.activateIntercept() which:
   *       <ul>
   *         <li>Checks if drain is required (based on WITH_IN_FLIGHT_TRACKING and forceImmediate)
   *         <li>If drain required: fence → wait for quiescence → enqueue → unfence
   *         <li>If drain not required: register immediately
   *       </ul>
   *   <li>For drain-based activations, the coordinator enqueues to the pending activations queue
   *   <li>This method (run loop) polls the queue and registers pending intercepts
   *   <li>Send response code to the caller
   * </ol>
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
          // Delegate to coordinator for safe activation with optional drain
          InterceptActivationCoordinator.ActivationResult result =
              activationCoordinator.activateIntercept(interceptMessage);

          if (result.isSuccess()) {
            registerSocket.send(REGISTER_OK_RESPONSE);
          } else if (result.isAsyncPending()) {
            // Drain-based activation is in progress asynchronously
            registerSocket.send(REGISTER_ASYNC_PENDING_RESPONSE);
          } else {
            // Check if it was a duplicate (coordinator returns failure for duplicates)
            if (result.getMessage().contains("Duplicate")) {
              registerSocket.send(REGISTER_DUP_RESPONSE);
            } else {
              registerSocket.send(REGISTER_UNKNOWN_ERROR_RESPONSE);
            }
          }
        } catch (Exception e) {
          logger.error("Unexpected error during intercept activation", e);
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
   * <p>This method safely closes the ZeroMQ poller and REP socket used for intercept communication
   * and logs errors if they occur.
   */
  @Override
  protected void closeConnections() {
    if (poller != null) {
      try {
        poller.close();
      } catch (Exception e) {
        logger.error("Error closing poller", e);
      }
    }
    closeConnection(registerSocket, "Error closing register (REP) socket");
  }
}
