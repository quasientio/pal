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

import static com.quasient.pal.serdes.colfer.ColferUtils.toBytes;

import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptCallbackRequest;
import com.quasient.pal.messages.colfer.InterceptCallbackResponse;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.ExceptionSerdes;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Dispatches intercept callbacks to remote peers.
 *
 * <p>This class is responsible for sending callback messages to peers that have registered
 * intercepts. It manages per-thread ZeroMQ sockets for callback communication and handles both
 * synchronous (BEFORE/AFTER) and asynchronous (BEFORE_ASYNC/AFTER_ASYNC) intercept types.
 *
 * <p><b>Socket Management:</b> This dispatcher uses two separate socket caches: REQ sockets for
 * synchronous callbacks (which require request-reply pattern) and DEALER sockets for asynchronous
 * callbacks (which allow fire-and-forget). The server-side ROUTER socket in ZmqRpcServer accepts
 * connections from both REQ and DEALER clients.
 *
 * <p><b>Thread Safety:</b> This class uses ThreadLocal storage for sockets, making it safe for
 * multithreaded use. Each thread maintains its own socket connections to target peers.
 *
 * <h2>Argument Mutation Aggregation Strategy</h2>
 *
 * <p>When multiple BEFORE callbacks are invoked for the same operation, each callback receives the
 * <b>original, unmodified arguments</b>. Callbacks do not see mutations made by other callbacks
 * that executed before them. This design ensures:
 *
 * <ul>
 *   <li><b>Callback independence:</b> Each callback sees the same view of the arguments, making
 *       behavior predictable and avoiding order-dependent side effects
 *   <li><b>Simplified reasoning:</b> Callbacks don't need to account for modifications by other
 *       callbacks
 *   <li><b>Deterministic results:</b> The final argument state is determined by aggregating all
 *       mutations, not by chaining transformations
 * </ul>
 *
 * <p><b>Mutation Aggregation Rules:</b>
 *
 * <ol>
 *   <li>All callbacks execute and return their mutations independently
 *   <li>Mutations are collected in a map: {@code Map<argumentIndex, mutatedValue>}
 *   <li>If multiple callbacks mutate the same argument index, the <b>last callback's mutation
 *       wins</b> (based on intercept registration order in etcd)
 *   <li>The aggregated mutations are applied once, after all callbacks complete
 *   <li>Only mutated arguments are modified; unmutated arguments retain their original values
 * </ol>
 *
 * <p><b>Example:</b> If three callbacks are registered for {@code foo(x, y)} and:
 *
 * <ul>
 *   <li>Callback A mutates arg[0] to 10
 *   <li>Callback B mutates arg[0] to 20 and arg[1] to 30
 *   <li>Callback C mutates arg[1] to 40
 * </ul>
 *
 * <p>Then the final arguments will be: {@code foo(20, 40)} (Callback B's mutation of arg[0] wins,
 * Callback C's mutation of arg[1] wins).
 *
 * <p><b>Ordering Implications:</b> Since the last mutation wins, the order in which intercepts are
 * registered (and thus the order they appear in etcd) determines which callback's mutations take
 * precedence when there are conflicts. Intercepts are processed in the order they are returned by
 * the directory service.
 *
 * <h2>Exception Handling</h2>
 *
 * <p>If any callback throws an exception (via {@link
 * com.quasient.pal.common.lang.intercept.InterceptCallbackResponse#setExceptionToThrow}), callback
 * processing stops immediately and the exception is propagated to the caller. No subsequent
 * callbacks are invoked, and any mutations from callbacks that executed before the exception are
 * discarded.
 *
 * <h2>Execution Flow Control (AROUND Intercepts Only)</h2>
 *
 * <p>BEFORE intercepts cannot control whether the intercepted method proceeds with execution. Only
 * AROUND intercepts can prevent execution by setting {@code shouldProceed = false}. BEFORE
 * intercepts are designed for observation and argument transformation, not for execution control.
 */
@Singleton
public class InterceptCallbackDispatcher {

  /** Logger instance for debugging callback dispatch operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptCallbackDispatcher.class);

  /** Timeout in milliseconds for receiving responses from synchronous callback requests. */
  private static final int CALLBACK_RECEIVE_TIMEOUT_MS = 3000;

  /** The unique identifier of this peer. */
  private final UUID peerUuid;

  /** ZeroMQ context used for creating sockets. */
  private final ZContext zmqContext;

  /** Message builder for constructing callback messages. */
  private final MessageBuilder messageBuilder;

  /** Provider for accessing the PAL directory to look up peer endpoints. */
  private final DirectoryConnectionProvider directoryConnectionProvider;

  /**
   * Per-thread socket cache for synchronous callback communication. Each thread maintains its own
   * map of peer UUID to connected REQ socket for BEFORE/AFTER intercepts.
   */
  private final ThreadLocal<Map<UUID, Socket>> syncCallbackSockets =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * Per-thread socket cache for asynchronous callback communication. Each thread maintains its own
   * map of peer UUID to connected DEALER socket for BEFORE_ASYNC/AFTER_ASYNC intercepts.
   */
  private final ThreadLocal<Map<UUID, Socket>> asyncCallbackSockets =
      ThreadLocal.withInitial(HashMap::new);

  /**
   * Constructs a new InterceptCallbackDispatcher with the specified dependencies.
   *
   * @param peerUuid the unique identifier for this peer
   * @param zmqContext the ZeroMQ context for socket creation
   * @param messageBuilder the message builder for constructing callback messages
   * @param directoryConnectionProvider provider for accessing peer directory information
   */
  @Inject
  public InterceptCallbackDispatcher(
      UUID peerUuid,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider) {
    this.peerUuid = peerUuid;
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
  }

  /**
   * Sends callbacks for all matching remote intercepts.
   *
   * <p>This method iterates through all remote intercepts in the check result and sends callback
   * messages to the corresponding peers. Synchronous intercepts (BEFORE/AFTER) block until a
   * response is received or timeout occurs. Asynchronous intercepts (BEFORE_ASYNC/AFTER_ASYNC)
   * fire-and-forget without waiting for responses.
   *
   * @param interceptCheckResult result from InterceptChecker containing matched intercepts
   * @param execMessage the execution message to send in callbacks
   */
  public void sendCallbacks(InterceptCheckResult interceptCheckResult, ExecMessage execMessage) {
    if (!interceptCheckResult.hasRemoteIntercepts()) {
      return;
    }

    List<InterceptMessage> remoteIntercepts = interceptCheckResult.getRemoteIntercepts();

    for (InterceptMessage interceptMessage : remoteIntercepts) {
      UUID interceptorPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
      InterceptType interceptType = InterceptType.fromByte(interceptMessage.getInterceptType());

      ExecMessage callbackMessage =
          messageBuilder.buildCallbackForInterceptRequest(peerUuid, execMessage, interceptMessage);

      try {
        if (interceptType.equals(InterceptType.BEFORE_ASYNC)
            || interceptType.equals(InterceptType.AFTER_ASYNC)) {
          sendAsyncCallbackToPeer(interceptorPeerUuid, callbackMessage);
        } else if (interceptType.equals(InterceptType.BEFORE)
            || interceptType.equals(InterceptType.AFTER)) {
          @SuppressWarnings("unused")
          byte[] unusedResponse = sendSyncCallbackToPeer(interceptorPeerUuid, callbackMessage);
        } else {
          logger.error("Unsupported callback type: {}", interceptType);
        }
      } catch (Exception ex) {
        logger.error(
            "Error sending callback to peer w/uuid: {}, callback execMessage: {}",
            interceptorPeerUuid,
            ColferUtils.format(callbackMessage),
            ex);
      }
    }
  }

  /**
   * Sends an asynchronous callback message to the specified peer (fire-and-forget).
   *
   * <p>Uses DEALER socket which allows sending without waiting for response, unlike REQ sockets
   * which require strict send-recv alternation. DEALER sockets must send an empty delimiter frame
   * before the payload to match the ROUTER socket's expected envelope format.
   *
   * @param targetPeerUuid the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @throws Exception if an error occurs while sending the message
   */
  private void sendAsyncCallbackToPeer(UUID targetPeerUuid, ExecMessage callbackMessage)
      throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending async callback message: {} to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          targetPeerUuid);
    }

    // Get DEALER socket for peer and send callback msg (no response expected)
    // DEALER sends: [empty delimiter, payload]
    Socket dealer = getConnectedDealerSocketFor(targetPeerUuid);
    dealer.sendMore(new byte[0]); // Empty delimiter frame
    final boolean sentOk = dealer.send(toBytes(messageBuilder.wrap(callbackMessage)), 0);

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent async callback message: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          sentOk,
          targetPeerUuid);
    }
  }

  /**
   * Sends a synchronous callback message to the specified peer and waits for a response.
   *
   * @param targetPeerUuid the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @return the response bytes received from the target peer
   * @throws Exception if an error occurs while sending the message or receiving the response
   */
  private byte[] sendSyncCallbackToPeer(UUID targetPeerUuid, ExecMessage callbackMessage)
      throws Exception {
    return sendCallbackMessageToPeer(targetPeerUuid, callbackMessage);
  }

  /**
   * Sends a callback message to the specified peer, optionally waiting for a response.
   *
   * @param interceptor the unique identifier of the target peer
   * @param callbackMessage the callback message to send
   * @return the response bytes if getResponse is true, null otherwise
   * @throws Exception if an error occurs during message sending or receiving
   */
  private byte[] sendCallbackMessageToPeer(UUID interceptor, ExecMessage callbackMessage)
      throws Exception {

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending callback message: {} to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          interceptor);
    }

    byte[] response;
    // get socket for peer and send callback msg
    Socket req = getConnectedReqSocketFor(interceptor);
    final boolean sentOk = req.send(toBytes(messageBuilder.wrap(callbackMessage)), 0);

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent callback message: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.format(callbackMessage),
          sentOk,
          interceptor);
    }

    // block until we get a response or peer is disconnected
    response = null;
    boolean peerIsUp = true;
    boolean gotResponse = false;
    while (!gotResponse && peerIsUp) {
      response = req.recv(0);
      if (response != null) {
        gotResponse = true;
        if (logger.isDebugEnabled()) {
          final Message callbackResponseMessage = new Message();
          callbackResponseMessage.unmarshal(response, 0);
          logger.debug(
              "Got response from callback: {}", ColferUtils.format(callbackResponseMessage));
        }
      } else { // we hit the timeout, check if peer is alive
        final PalDirectory palDirectory =
            directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
        peerIsUp = palDirectory.peerExists(interceptor);
        if (peerIsUp) {
          logger.warn(
              "Timed out waiting for callback response from peer w/uuid: {}. "
                  + "Peer still exists, will retry.",
              interceptor);
        } else {
          logger.warn(
              "Timed out waiting for callback response from peer w/uuid: {}. "
                  + "Peer no longer exists.",
              interceptor);
        }
      }
    }
    return response;
  }

  /**
   * Retrieves an existing or creates a new thread-local REQ socket connected to the specified peer.
   *
   * <p>The connection is established using the peer's ZMQ RPC address obtained from the directory
   * provider. Sockets are cached per-thread to avoid repeated connection overhead.
   *
   * @param peer the unique identifier of the target peer
   * @return a connected REQ Socket for communication with the specified peer
   * @throws Exception if the peer's information cannot be retrieved from the directory
   */
  private Socket getConnectedReqSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (syncCallbackSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing REQ socket for peer w/uuid: {}", peer);
      }
      return syncCallbackSockets.get().get(peer);
    }

    // else, create and connect new socket
    if (logger.isDebugEnabled()) {
      logger.debug("Connecting new REQ socket to peer w/uuid: {}", peer);
    }

    final Socket reqSocket = zmqContext.createSocket(SocketType.REQ);
    // set receive timeout
    reqSocket.setReceiveTimeOut(CALLBACK_RECEIVE_TIMEOUT_MS);

    // get peer's address
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    String interceptorAddress = palDirectory.getPeer(peer).getZmqRpcAddress();
    reqSocket.connect(interceptorAddress);

    // store in thread-local peer->socket map
    syncCallbackSockets.get().put(peer, reqSocket);

    return reqSocket;
  }

  /**
   * Retrieves an existing or creates a new thread-local DEALER socket connected to the specified
   * peer.
   *
   * <p>DEALER sockets are used for asynchronous callbacks because they allow multiple sends without
   * requiring a response, unlike REQ sockets which require strict send-recv alternation. The
   * server-side ROUTER socket in ZmqRpcServer accepts connections from both REQ and DEALER clients.
   *
   * <p>The connection is established using the peer's ZMQ RPC address obtained from the directory
   * provider. Sockets are cached per-thread to avoid repeated connection overhead.
   *
   * @param peer the unique identifier of the target peer
   * @return a connected DEALER Socket for communication with the specified peer
   * @throws Exception if the peer's information cannot be retrieved from the directory
   */
  private Socket getConnectedDealerSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (asyncCallbackSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing DEALER socket for peer w/uuid: {}", peer);
      }
      return asyncCallbackSockets.get().get(peer);
    }

    // else, create and connect new socket
    if (logger.isDebugEnabled()) {
      logger.debug("Connecting new DEALER socket to peer w/uuid: {}", peer);
    }

    final Socket dealerSocket = zmqContext.createSocket(SocketType.DEALER);

    // get peer's address
    final PalDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    String interceptorAddress = palDirectory.getPeer(peer).getZmqRpcAddress();
    dealerSocket.connect(interceptorAddress);

    // store in thread-local peer->socket map
    asyncCallbackSockets.get().put(peer, dealerSocket);

    return dealerSocket;
  }

  /**
   * Sends BEFORE-phase callbacks for all matching intercepts and collects responses.
   *
   * <p>This method sends {@link InterceptCallbackRequest} messages to callback peers for each
   * matching BEFORE intercept. It waits for {@link InterceptCallbackResponse} from each callback
   * and collects any argument mutations.
   *
   * <p><b>Important:</b> Each callback receives the <b>original, unmodified arguments</b> from the
   * {@code execMessage}. Callbacks do not see mutations made by previously-executed callbacks. This
   * ensures callback independence and predictable behavior.
   *
   * <p><b>Mutation Handling:</b> Argument mutations from all callbacks are collected into a map. If
   * multiple callbacks mutate the same argument index, the last callback's mutation wins (based on
   * intercept registration order). The aggregated mutations are returned in the {@link
   * ConsolidatedCallbackResponse} and applied by the caller after all callbacks complete.
   *
   * <p><b>Exception Handling:</b> If any callback throws an exception, processing stops
   * immediately. No further callbacks are invoked, and all mutations collected so far are
   * discarded.
   *
   * @param interceptCheckResult result from InterceptChecker containing matched intercepts
   * @param execMessage the execution message containing operation metadata and original arguments
   * @param args the method arguments (used for validation but not mutated by this method)
   * @return a consolidated response containing all argument mutations and control flow decisions
   */
  public ConsolidatedCallbackResponse sendBeforeCallbacks(
      InterceptCheckResult interceptCheckResult, ExecMessage execMessage, Object[] args) {

    if (!interceptCheckResult.hasRemoteIntercepts()) {
      return ConsolidatedCallbackResponse.proceed();
    }

    List<InterceptMessage> remoteIntercepts = interceptCheckResult.getRemoteIntercepts();
    List<InterceptMessage> beforeIntercepts = new ArrayList<>();

    // Filter for BEFORE intercepts only
    for (InterceptMessage intercept : remoteIntercepts) {
      InterceptType type = InterceptType.fromByte(intercept.getInterceptType());
      if (type == InterceptType.BEFORE) {
        beforeIntercepts.add(intercept);
      }
    }

    if (beforeIntercepts.isEmpty()) {
      return ConsolidatedCallbackResponse.proceed();
    }

    // Track mutations across all callbacks
    Map<Integer, Object> mutatedArgs = new HashMap<>();
    boolean shouldProceed = true;
    Throwable exceptionToThrow = null;

    for (InterceptMessage interceptMessage : beforeIntercepts) {
      try {
        UUID callbackPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());

        // Build the callback request
        InterceptCallbackRequest request =
            messageBuilder.buildInterceptCallbackRequest(
                peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

        // Send and await response
        InterceptCallbackResponse response = sendCallbackRequest(callbackPeerUuid, request);

        // Check for exception
        if (response.getThrowException()) {
          exceptionToThrow = ExceptionSerdes.deserializeException(response.getException());
          break; // Stop processing further callbacks
        }

        // Collect argument mutations
        if (response.getMutatedArgs() != null && response.getMutatedArgs().length > 0) {
          Obj[] responseMutatedArgs = response.getMutatedArgs();
          for (int i = 0; i < responseMutatedArgs.length && i < args.length; i++) {
            if (responseMutatedArgs[i] != null) {
              mutatedArgs.put(i, deserializeArg(responseMutatedArgs[i]));
            }
          }
        }

        // Note: shouldProceed is not checked here because BEFORE intercepts
        // cannot control execution flow. Only AROUND intercepts can do this,
        // and they are handled differently in the caller.

      } catch (Exception ex) {
        logger.error(
            "Error sending BEFORE callback to peer: {}, continuing with remaining callbacks",
            interceptMessage.getPeerUuid(),
            ex);
        // Continue with other callbacks on error
      }
    }

    return new ConsolidatedCallbackResponse(shouldProceed, mutatedArgs, exceptionToThrow);
  }

  /**
   * Sends an {@link InterceptCallbackRequest} to the specified peer and awaits the response.
   *
   * @param callbackPeerUuid the UUID of the callback peer
   * @param request the callback request to send
   * @return the callback response
   * @throws Exception if sending or receiving fails
   */
  private InterceptCallbackResponse sendCallbackRequest(
      UUID callbackPeerUuid, InterceptCallbackRequest request) throws Exception {

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending InterceptCallbackRequest to peer {}: callbackId={}",
          callbackPeerUuid,
          request.getCallbackId());
    }

    // Get REQ socket for the callback peer
    Socket reqSocket = getConnectedReqSocketFor(callbackPeerUuid);

    // Wrap the request in a Message and send
    Message requestMessage = messageBuilder.wrap(request);
    byte[] requestBytes = toBytes(requestMessage);
    boolean sentOk = reqSocket.send(requestBytes, 0);

    if (!sentOk) {
      throw new RuntimeException("Failed to send callback request to peer: " + callbackPeerUuid);
    }

    // Await response
    byte[] responseBytes = reqSocket.recv(0);

    if (responseBytes == null) {
      // Timeout occurred
      logger.warn(
          "Timeout waiting for callback response from peer {}. Returning proceed=true fallback.",
          callbackPeerUuid);
      // Return default "proceed" response on timeout
      InterceptCallbackResponse fallback = new InterceptCallbackResponse();
      fallback.setCallbackId(request.getCallbackId());
      fallback.setPhase(request.getPhase());
      fallback.setShouldProceed(true);
      return fallback;
    }

    // Unmarshal response
    Message responseMessage = new Message();
    responseMessage.unmarshal(responseBytes, 0);

    InterceptCallbackResponse response = responseMessage.getInterceptCallbackResponse();
    if (response == null) {
      throw new RuntimeException("Expected InterceptCallbackResponse but got: " + responseMessage);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Received InterceptCallbackResponse from peer {}: callbackId={}",
          callbackPeerUuid,
          response.getCallbackId());
    }

    return response;
  }

  /**
   * Deserializes an argument from Colfer {@link Obj} format.
   *
   * @param obj the serialized object
   * @return the deserialized value
   */
  private Object deserializeArg(Obj obj) {
    try {
      return com.quasient.pal.serdes.Unwrapper.unwrapObject(obj);
    } catch (Exception e) {
      logger.error("Failed to deserialize argument", e);
      throw new RuntimeException("Failed to deserialize argument", e);
    }
  }

  /**
   * Consolidated response from multiple callback invocations.
   *
   * <p>This class aggregates responses from multiple callback handlers, including argument
   * mutations, proceed control, and exception handling.
   */
  public static class ConsolidatedCallbackResponse {
    /** Whether the intercepted method should proceed with execution. */
    private final boolean shouldProceed;

    /** Map of argument index to mutated value. */
    private final Map<Integer, Object> mutatedArgs;

    /** Exception to throw instead of normal execution (may be null). */
    private final Throwable exceptionToThrow;

    /**
     * Constructs a consolidated response.
     *
     * @param shouldProceed whether execution should proceed
     * @param mutatedArgs map of argument index to mutated value
     * @param exceptionToThrow exception to throw (may be null)
     */
    public ConsolidatedCallbackResponse(
        boolean shouldProceed, Map<Integer, Object> mutatedArgs, Throwable exceptionToThrow) {
      this.shouldProceed = shouldProceed;
      this.mutatedArgs = mutatedArgs;
      this.exceptionToThrow = exceptionToThrow;
    }

    /**
     * Creates a default "proceed" response with no mutations.
     *
     * @return a proceed response
     */
    public static ConsolidatedCallbackResponse proceed() {
      return new ConsolidatedCallbackResponse(true, new HashMap<>(), null);
    }

    /**
     * Returns whether execution should proceed.
     *
     * @return true if execution should proceed
     */
    public boolean shouldProceed() {
      return shouldProceed;
    }

    /**
     * Returns whether any arguments were mutated.
     *
     * @return true if arguments were mutated
     */
    public boolean hasArgMutations() {
      return !mutatedArgs.isEmpty();
    }

    /**
     * Returns the map of mutated arguments (index → value).
     *
     * @return the mutated arguments map
     */
    public Map<Integer, Object> getMutatedArgs() {
      return mutatedArgs;
    }

    /**
     * Returns whether an exception should be thrown.
     *
     * @return true if an exception should be thrown
     */
    public boolean shouldThrowException() {
      return exceptionToThrow != null;
    }

    /**
     * Returns the exception to throw.
     *
     * @return the exception (may be null)
     */
    public Throwable getExceptionToThrow() {
      return exceptionToThrow;
    }
  }

  /**
   * Cleans up thread-local sockets when a thread terminates.
   *
   * <p>This method should be called when a thread that has used this dispatcher is about to
   * terminate, to properly close all open sockets and free resources. It cleans up both synchronous
   * (REQ) and asynchronous (DEALER) socket caches.
   */
  public void cleanup() {
    // Clean up sync sockets (REQ)
    Map<UUID, Socket> syncSockets = syncCallbackSockets.get();
    syncSockets.values().forEach(Socket::close);
    syncSockets.clear();
    syncCallbackSockets.remove();

    // Clean up async sockets (DEALER)
    Map<UUID, Socket> asyncSockets = asyncCallbackSockets.get();
    asyncSockets.values().forEach(Socket::close);
    asyncSockets.clear();
    asyncCallbackSockets.remove();
  }
}
