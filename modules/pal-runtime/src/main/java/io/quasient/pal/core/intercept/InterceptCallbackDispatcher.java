/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static io.quasient.pal.serdes.colfer.ColferUtils.toBytes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.ExceptionSerdes;
import io.quasient.pal.serdes.colfer.MessageBuilder;
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
 * io.quasient.pal.common.lang.intercept.InterceptCallbackResponse#setExceptionToThrow}), callback
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
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Dispatcher pattern with shared mutable state for callback handling")
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

  /** Resolver for exception policies applied to callback exceptions. */
  private final ExceptionPolicyResolver exceptionPolicyResolver;

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
   * @param exceptionPolicyResolver resolver for exception policies applied to callback exceptions
   */
  @Inject
  public InterceptCallbackDispatcher(
      UUID peerUuid,
      ZContext zmqContext,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider,
      ExceptionPolicyResolver exceptionPolicyResolver) {
    this.peerUuid = peerUuid;
    this.zmqContext = zmqContext;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.exceptionPolicyResolver = exceptionPolicyResolver;
  }

  /**
   * Sends an asynchronous callback message to the specified peer (fire-and-forget).
   *
   * <p>Uses DEALER socket which allows sending without waiting for response, unlike REQ sockets
   * which require strict send-recv alternation. DEALER sockets must send an empty delimiter frame
   * before the payload to match the ROUTER socket's expected envelope format.
   *
   * @param targetPeerUuid the unique identifier of the target peer
   * @param request the callback request to send
   * @throws Exception if an error occurs while sending the message
   */
  private void sendAsyncCallbackToPeer(UUID targetPeerUuid, InterceptCallbackRequestMessage request)
      throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending async callback request: {} to peer w/uuid: {}",
          ColferUtils.toJson(request),
          targetPeerUuid);
    }

    // Get DEALER socket for peer and send callback msg (no response expected)
    // DEALER sends: [empty delimiter, payload]
    Socket dealer = getConnectedDealerSocketFor(targetPeerUuid);
    dealer.sendMore(new byte[0]); // Empty delimiter frame
    final boolean sentOk = dealer.send(toBytes(messageBuilder.wrap(request)), 0);

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent async callback request: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.toJson(request),
          sentOk,
          targetPeerUuid);
    }
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
   * <p>This method sends {@link InterceptCallbackRequestMessage} messages to callback peers for
   * each matching BEFORE intercept. It waits for {@link InterceptCallbackResponseMessage} from each
   * callback and collects any argument mutations.
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
    List<InterceptMessage> beforeSyncIntercepts = new ArrayList<>();
    List<InterceptMessage> beforeAsyncIntercepts = new ArrayList<>();

    // Filter for BEFORE and BEFORE_ASYNC intercepts
    for (InterceptMessage intercept : remoteIntercepts) {
      InterceptType type = InterceptType.fromByte(intercept.getInterceptType());
      if (type == InterceptType.BEFORE) {
        beforeSyncIntercepts.add(intercept);
      } else if (type == InterceptType.BEFORE_ASYNC) {
        beforeAsyncIntercepts.add(intercept);
      }
    }

    if (beforeSyncIntercepts.isEmpty() && beforeAsyncIntercepts.isEmpty()) {
      return ConsolidatedCallbackResponse.proceed();
    }

    // Track mutations across all callbacks
    Map<Integer, Object> mutatedArgs = new HashMap<>();
    boolean shouldProceed = true;
    Throwable exceptionToThrow = null;

    // Process synchronous BEFORE intercepts (wait for response)
    for (InterceptMessage interceptMessage : beforeSyncIntercepts) {
      try {
        UUID callbackPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
        logger.info(
            "===== BEFORE callback: interceptMessage.getPeerUuid()='{}', "
                + "interceptMessage.getMessageId()='{}', interceptMessage.getClazz()='{}', "
                + "interceptMessage.getCallbackClass()='{}', interceptMessage.getCallbackMethod()='{}'",
            interceptMessage.getPeerUuid(),
            interceptMessage.getMessageId(),
            interceptMessage.getClazz(),
            interceptMessage.getCallbackClass(),
            interceptMessage.getCallbackMethod());

        // Build the callback request
        InterceptCallbackRequestMessage request =
            messageBuilder.buildInterceptCallbackRequest(
                peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

        // Send and await response
        InterceptCallbackResponseMessage response = sendCallbackRequest(callbackPeerUuid, request);

        // Process exception with policy
        Throwable callbackException =
            processCallbackException(response, interceptMessage, InterceptType.BEFORE);
        if (callbackException != null) {
          exceptionToThrow = callbackException;
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

    // Process asynchronous BEFORE_ASYNC intercepts (fire-and-forget)
    for (InterceptMessage interceptMessage : beforeAsyncIntercepts) {
      try {
        UUID callbackPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
        logger.info(
            "===== BEFORE_ASYNC callback: interceptMessage.getPeerUuid()='{}', "
                + "interceptMessage.getMessageId()='{}', interceptMessage.getClazz()='{}', "
                + "interceptMessage.getCallbackClass()='{}', interceptMessage.getCallbackMethod()='{}'",
            interceptMessage.getPeerUuid(),
            interceptMessage.getMessageId(),
            interceptMessage.getClazz(),
            interceptMessage.getCallbackClass(),
            interceptMessage.getCallbackMethod());

        // Build the callback request for async
        InterceptCallbackRequestMessage request =
            messageBuilder.buildInterceptCallbackRequest(
                peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);

        // Send asynchronously (fire-and-forget, no response expected)
        sendAsyncCallbackToPeer(callbackPeerUuid, request);

        // Note: Async callbacks cannot mutate arguments or throw exceptions
        // since we don't wait for a response

      } catch (Exception ex) {
        logger.error(
            "Error sending BEFORE_ASYNC callback to peer: {}, continuing with remaining callbacks",
            interceptMessage.getPeerUuid(),
            ex);
        // Continue with other callbacks on error
      }
    }

    return new ConsolidatedCallbackResponse(shouldProceed, mutatedArgs, exceptionToThrow);
  }

  /**
   * Sends AFTER-phase callbacks for all matching intercepts and collects responses.
   *
   * <p>This method sends {@link InterceptCallbackRequestMessage} messages to callback peers for
   * each matching AFTER intercept. It waits for {@link InterceptCallbackResponseMessage} from each
   * callback and collects any return value overrides.
   *
   * <p><b>Return Value Override:</b> If multiple callbacks override the return value, the last
   * callback's override wins (based on intercept registration order). The aggregated override is
   * returned in the {@link ConsolidatedCallbackResponse} and applied by the caller after all
   * callbacks complete.
   *
   * <p><b>Exception Handling:</b> If any callback throws an exception, processing stops
   * immediately. No further callbacks are invoked, and all overrides collected so far are
   * discarded.
   *
   * @param interceptCheckResult result from InterceptChecker containing matched intercepts
   * @param execMessage the execution message containing operation metadata
   * @param returnValue the return value from the intercepted method (may be null)
   * @param isVoid whether the intercepted method has void return type
   * @param thrownException the exception thrown by the intercepted method (may be null)
   * @return a consolidated response containing return value override and control flow decisions
   */
  public ConsolidatedCallbackResponse sendAfterCallbacks(
      InterceptCheckResult interceptCheckResult,
      ExecMessage execMessage,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException) {

    System.out.println(
        "===== sendAfterCallbacks CALLED: returnValue=" + returnValue + ", isVoid=" + isVoid);
    logger.info("===== sendAfterCallbacks called: returnValue={}, isVoid={}", returnValue, isVoid);

    if (!interceptCheckResult.hasRemoteIntercepts()) {
      System.out.println("===== sendAfterCallbacks: NO remote intercepts");
      logger.info("===== sendAfterCallbacks: no remote intercepts, returning proceed");
      return ConsolidatedCallbackResponse.proceed();
    }

    List<InterceptMessage> remoteIntercepts = interceptCheckResult.getRemoteIntercepts();
    List<InterceptMessage> afterSyncIntercepts = new ArrayList<>();
    List<InterceptMessage> afterAsyncIntercepts = new ArrayList<>();

    // Filter for AFTER and AFTER_ASYNC intercepts
    for (InterceptMessage intercept : remoteIntercepts) {
      InterceptType type = InterceptType.fromByte(intercept.getInterceptType());
      if (type == InterceptType.AFTER) {
        afterSyncIntercepts.add(intercept);
      } else if (type == InterceptType.AFTER_ASYNC) {
        afterAsyncIntercepts.add(intercept);
      }
    }

    logger.info(
        "===== sendAfterCallbacks: found {} AFTER intercepts, {} AFTER_ASYNC intercepts",
        afterSyncIntercepts.size(),
        afterAsyncIntercepts.size());

    if (afterSyncIntercepts.isEmpty() && afterAsyncIntercepts.isEmpty()) {
      logger.info(
          "===== sendAfterCallbacks: no AFTER/AFTER_ASYNC intercepts after filtering, returning proceed");
      return ConsolidatedCallbackResponse.proceed();
    }

    // Track return value override across all callbacks
    Object currentReturnValue = returnValue;
    boolean hasOverride = false;
    boolean shouldProceed = true;
    Throwable exceptionToThrow = null;

    // Process synchronous AFTER intercepts (wait for response)
    for (InterceptMessage interceptMessage : afterSyncIntercepts) {
      try {
        UUID callbackPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
        logger.info(
            "===== AFTER callback: interceptMessage.getPeerUuid()='{}', "
                + "interceptMessage.getMessageId()='{}', interceptMessage.getClazz()='{}', "
                + "interceptMessage.getCallbackClass()='{}', interceptMessage.getCallbackMethod()='{}'",
            interceptMessage.getPeerUuid(),
            interceptMessage.getMessageId(),
            interceptMessage.getClazz(),
            interceptMessage.getCallbackClass(),
            interceptMessage.getCallbackMethod());

        // Build the callback request with return value and exception
        InterceptCallbackRequestMessage request =
            messageBuilder.buildInterceptCallbackRequest(
                peerUuid,
                interceptMessage,
                execMessage,
                InterceptPhase.AFTER,
                returnValue,
                isVoid,
                thrownException);

        // Send and await response
        InterceptCallbackResponseMessage response = sendCallbackRequest(callbackPeerUuid, request);

        // Process exception with policy
        Throwable callbackException =
            processCallbackException(response, interceptMessage, InterceptType.AFTER);
        if (callbackException != null) {
          exceptionToThrow = callbackException;
          break; // Stop processing further callbacks
        }

        // Collect return value override
        if (response.getOverrideReturn()) {
          logger.info("===== sendAfterCallbacks: response has overrideReturn=true");
          Obj overriddenReturnObj = response.getNewReturnValue();
          if (overriddenReturnObj != null) {
            currentReturnValue = deserializeArg(overriddenReturnObj);
            hasOverride = true;
            logger.info(
                "===== sendAfterCallbacks: overridden return value: {}", currentReturnValue);
          } else {
            logger.warn("===== sendAfterCallbacks: overrideReturn=true but newReturnValue is null");
          }
        } else {
          logger.info("===== sendAfterCallbacks: response has overrideReturn=false");
        }

        // Note: shouldProceed is not checked here because AFTER intercepts
        // cannot control execution flow. Only AROUND intercepts can do this,
        // and they are handled differently in the caller.

      } catch (Exception ex) {
        logger.error(
            "Error sending AFTER callback to peer: {}, continuing with remaining callbacks",
            interceptMessage.getPeerUuid(),
            ex);
        // Continue with other callbacks on error
      }
    }

    // Process asynchronous AFTER_ASYNC intercepts (fire-and-forget)
    for (InterceptMessage interceptMessage : afterAsyncIntercepts) {
      try {
        UUID callbackPeerUuid = UUID.fromString(interceptMessage.getPeerUuid());
        logger.info(
            "===== AFTER_ASYNC callback: interceptMessage.getPeerUuid()='{}', "
                + "interceptMessage.getMessageId()='{}', interceptMessage.getClazz()='{}', "
                + "interceptMessage.getCallbackClass()='{}', interceptMessage.getCallbackMethod()='{}'",
            interceptMessage.getPeerUuid(),
            interceptMessage.getMessageId(),
            interceptMessage.getClazz(),
            interceptMessage.getCallbackClass(),
            interceptMessage.getCallbackMethod());

        // Build the callback request for async with return value and exception
        InterceptCallbackRequestMessage request =
            messageBuilder.buildInterceptCallbackRequest(
                peerUuid,
                interceptMessage,
                execMessage,
                InterceptPhase.AFTER,
                returnValue,
                isVoid,
                thrownException);

        // Send asynchronously (fire-and-forget, no response expected)
        sendAsyncCallbackToPeer(callbackPeerUuid, request);

        // Note: Async callbacks cannot override return value or throw exceptions
        // since we don't wait for a response

      } catch (Exception ex) {
        logger.error(
            "Error sending AFTER_ASYNC callback to peer: {}, continuing with remaining callbacks",
            interceptMessage.getPeerUuid(),
            ex);
        // Continue with other callbacks on error
      }
    }

    logger.info(
        "===== sendAfterCallbacks: returning hasOverride={}, currentReturnValue={}",
        hasOverride,
        currentReturnValue);
    return new ConsolidatedCallbackResponse(
        shouldProceed, new HashMap<>(), exceptionToThrow, currentReturnValue, hasOverride);
  }

  /**
   * Processes exception from callback response according to policy.
   *
   * <p>This method handles both API misuse errors and business exceptions:
   *
   * <ul>
   *   <li><b>API misuse errors:</b> Logged and swallowed (returns null)
   *   <li><b>Business exceptions:</b> Deserialized and policy applied to determine if they should
   *       propagate
   * </ul>
   *
   * @param response the callback response containing exception information
   * @param interceptMessage the intercept message for policy resolution
   * @param interceptType the type of intercept for policy resolution
   * @return the exception to throw (if policy allows), or null if swallowed
   */
  private Throwable processCallbackException(
      InterceptCallbackResponseMessage response,
      InterceptMessage interceptMessage,
      InterceptType interceptType) {

    if (!response.getThrowException()) {
      return null;
    }

    // Check for API misuse error
    if (response.getIsApiMisuseError()) {
      // API misuse errors are logged but not propagated
      logger.error(
          "API misuse error in callback for intercept type {}: {}. "
              + "This indicates improper callback handler implementation. "
              + "The error will be logged but not propagated to the caller.",
          interceptType,
          response.getException() != null
              ? ExceptionSerdes.deserializeException(response.getException())
              : "Unknown error");
      return null; // Swallow API misuse errors
    }

    // Deserialize the business exception
    Throwable exception = ExceptionSerdes.deserializeException(response.getException());

    // Resolve propagation policy for this intercept
    ExceptionPropagationPolicy policy =
        exceptionPolicyResolver.resolvePropagationPolicy(interceptMessage, interceptType);

    // Apply policy
    boolean shouldPropagate =
        switch (policy) {
          case PROPAGATE_ALL -> true;
          case SWALLOW_ALL -> false;
          case PROPAGATE_EXPLICIT_ONLY, PROPAGATE_CONTROLLED_ONLY -> {
            // For these policies, exceptions set via setExceptionToThrow should propagate
            // (which is indicated by throwException=true and isApiMisuseError=false)
            yield true;
          }
        };

    if (shouldPropagate) {
      logger.debug(
          "Propagating business exception from callback (policy={}): {}", policy, exception);
      return exception;
    } else {
      logger.info("Swallowing business exception from callback (policy={}): {}", policy, exception);
      return null;
    }
  }

  /**
   * Sends an {@link InterceptCallbackRequestMessage} to the specified peer and awaits the response.
   *
   * @param callbackPeerUuid the UUID of the callback peer
   * @param request the callback request to send
   * @return the callback response
   * @throws Exception if sending or receiving fails
   */
  private InterceptCallbackResponseMessage sendCallbackRequest(
      UUID callbackPeerUuid, InterceptCallbackRequestMessage request) throws Exception {

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending InterceptCallbackRequestMessage to peer {}: callbackId={}",
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
      InterceptCallbackResponseMessage fallback = new InterceptCallbackResponseMessage();
      fallback.setCallbackId(request.getCallbackId());
      fallback.setPhase(request.getPhase());
      fallback.setShouldProceed(true);
      return fallback;
    }

    // Unmarshal response
    Message responseMessage = new Message();
    responseMessage.unmarshal(responseBytes, 0);

    InterceptCallbackResponseMessage response =
        responseMessage.getInterceptCallbackResponseMessage();
    if (response == null) {
      throw new RuntimeException(
          "Expected InterceptCallbackResponseMessage but got: " + responseMessage);
    }

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Received InterceptCallbackResponseMessage from peer {}: callbackId={}",
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
      return Unwrapper.unwrapObject(obj);
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

    /** Overridden return value from AFTER callbacks (may be null). */
    private final Object overriddenReturnValue;

    /** Whether the return value was overridden by a callback. */
    private final boolean hasReturnValueOverride;

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
      this.overriddenReturnValue = null;
      this.hasReturnValueOverride = false;
    }

    /**
     * Constructs a consolidated response with return value override.
     *
     * @param shouldProceed whether execution should proceed
     * @param mutatedArgs map of argument index to mutated value
     * @param exceptionToThrow exception to throw (may be null)
     * @param overriddenReturnValue the overridden return value (may be null)
     * @param hasReturnValueOverride whether the return value was overridden
     */
    public ConsolidatedCallbackResponse(
        boolean shouldProceed,
        Map<Integer, Object> mutatedArgs,
        Throwable exceptionToThrow,
        Object overriddenReturnValue,
        boolean hasReturnValueOverride) {
      this.shouldProceed = shouldProceed;
      this.mutatedArgs = mutatedArgs;
      this.exceptionToThrow = exceptionToThrow;
      this.overriddenReturnValue = overriddenReturnValue;
      this.hasReturnValueOverride = hasReturnValueOverride;
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

    /**
     * Returns whether the return value was overridden.
     *
     * @return true if the return value was overridden
     */
    public boolean hasReturnValueOverride() {
      return hasReturnValueOverride;
    }

    /**
     * Returns the overridden return value.
     *
     * @return the overridden return value (may be null)
     */
    public Object getOverriddenReturnValue() {
      return overriddenReturnValue;
    }
  }

  // ---- AROUND intercept support with ctx.proceed() API ----

  /** Default timeout in milliseconds for AROUND intercept callbacks. */
  private static final int DEFAULT_AROUND_TIMEOUT_MS = 30000;

  /**
   * State tracking for a pending AROUND callback that needs AFTER phase.
   *
   * @param intercept the intercept message
   * @param callbackId the callback ID for correlation
   * @param callbackPeer the callback peer UUID
   */
  public record AroundCallbackState(
      InterceptMessage intercept, String callbackId, UUID callbackPeer) {}

  /**
   * Sends a chained AROUND BEFORE request to a remote callback peer.
   *
   * <p>This method is used by {@link AroundInterceptChain} when processing remote AROUND intercepts
   * in the chain. This method processes a single intercept at a time, allowing proper chaining
   * where each proceed() invokes the next layer.
   *
   * @param intercept the intercept message
   * @param callbackPeerUuid the callback peer UUID
   * @param callbackId the callback ID for correlation
   * @param execMessage the execution message
   * @param args the current arguments
   * @return the BEFORE phase result
   */
  public AroundInterceptChain.RemoteAroundBeforeResult sendChainedAroundBefore(
      InterceptMessage intercept,
      UUID callbackPeerUuid,
      String callbackId,
      ExecMessage execMessage,
      Object[] args) {

    try {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Sending chained AROUND BEFORE to peer {}: callbackId={}",
            callbackPeerUuid,
            callbackId);
      }

      // Build the BEFORE phase callback request
      InterceptCallbackRequestMessage request =
          buildAroundBeforeRequest(intercept, execMessage, callbackId, DEFAULT_AROUND_TIMEOUT_MS);

      // Send and await response
      InterceptCallbackResponseMessage response = sendCallbackRequest(callbackPeerUuid, request);

      // Check for exception
      if (response.getThrowException()) {
        Throwable ex = ExceptionSerdes.deserializeException(response.getException());
        return new AroundInterceptChain.RemoteAroundBeforeResult(false, Map.of(), null, ex);
      }

      // Check shouldProceed
      if (!response.getShouldProceed()) {
        Object skipReturnValue = null;
        if (response.getOverrideReturn() && response.getNewReturnValue() != null) {
          skipReturnValue = deserializeArg(response.getNewReturnValue());
        }
        return new AroundInterceptChain.RemoteAroundBeforeResult(
            false, Map.of(), skipReturnValue, null);
      }

      // Collect argument mutations
      Map<Integer, Object> mutations = new HashMap<>();
      if (response.getMutatedArgs() != null && response.getMutatedArgs().length > 0) {
        Obj[] responseMutatedArgs = response.getMutatedArgs();
        for (int i = 0; i < responseMutatedArgs.length && i < args.length; i++) {
          if (responseMutatedArgs[i] != null) {
            mutations.put(i, deserializeArg(responseMutatedArgs[i]));
          }
        }
      }

      return new AroundInterceptChain.RemoteAroundBeforeResult(true, mutations, null, null);

    } catch (Exception ex) {
      logger.error("Error sending chained AROUND BEFORE to peer: {}", callbackPeerUuid, ex);
      return new AroundInterceptChain.RemoteAroundBeforeResult(false, Map.of(), null, ex);
    }
  }

  /**
   * Sends a chained AROUND AFTER request to a remote callback peer.
   *
   * <p>This method is used by {@link AroundInterceptChain} when processing remote AROUND intercepts
   * in the chain. It sends the AFTER phase for a single intercept with the return value from inner
   * layers.
   *
   * @param intercept the intercept message
   * @param callbackPeerUuid the callback peer UUID
   * @param callbackId the callback ID (must match the BEFORE request)
   * @param execMessage the execution message
   * @param returnValue the return value from inner layers
   * @param isVoid whether the method is void
   * @param thrownException exception from inner layers
   * @return the AFTER phase result
   */
  public AroundInterceptChain.RemoteAroundAfterResult sendChainedAroundAfter(
      InterceptMessage intercept,
      UUID callbackPeerUuid,
      String callbackId,
      ExecMessage execMessage,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException) {

    try {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Sending chained AROUND AFTER to peer {}: callbackId={}", callbackPeerUuid, callbackId);
      }

      // Build the AFTER phase callback request
      InterceptCallbackRequestMessage request =
          buildAroundAfterRequest(
              intercept, execMessage, callbackId, returnValue, isVoid, thrownException);

      // Send and await response
      InterceptCallbackResponseMessage response = sendCallbackRequest(callbackPeerUuid, request);

      // Check for exception
      if (response.getThrowException()) {
        Throwable ex = ExceptionSerdes.deserializeException(response.getException());
        return new AroundInterceptChain.RemoteAroundAfterResult(false, null, ex);
      }

      // Check for return value override
      if (response.getOverrideReturn() && response.getNewReturnValue() != null) {
        Object overridden = deserializeArg(response.getNewReturnValue());
        return new AroundInterceptChain.RemoteAroundAfterResult(true, overridden, null);
      }

      return new AroundInterceptChain.RemoteAroundAfterResult(false, null, null);

    } catch (Exception ex) {
      logger.error("Error sending chained AROUND AFTER to peer: {}", callbackPeerUuid, ex);
      return new AroundInterceptChain.RemoteAroundAfterResult(false, null, ex);
    }
  }

  /**
   * Builds an AROUND BEFORE phase callback request with timeout.
   *
   * @param interceptMessage the intercept message
   * @param execMessage the execution message
   * @param callbackId the unique callback ID
   * @param timeoutMs the timeout in milliseconds
   * @return the callback request
   */
  private InterceptCallbackRequestMessage buildAroundBeforeRequest(
      InterceptMessage interceptMessage,
      ExecMessage execMessage,
      String callbackId,
      int timeoutMs) {
    InterceptCallbackRequestMessage request =
        messageBuilder.buildInterceptCallbackRequest(
            peerUuid, interceptMessage, execMessage, InterceptPhase.BEFORE, null, false, null);
    request.setCallbackId(callbackId);
    request.setTimeoutMs(timeoutMs);
    return request;
  }

  /**
   * Builds an AROUND AFTER phase callback request.
   *
   * @param interceptMessage the intercept message
   * @param execMessage the execution message
   * @param callbackId the callback ID (must match BEFORE phase)
   * @param returnValue the return value from method execution
   * @param isVoid whether the method has void return type
   * @param thrownException exception thrown by the method
   * @return the callback request
   */
  private InterceptCallbackRequestMessage buildAroundAfterRequest(
      InterceptMessage interceptMessage,
      ExecMessage execMessage,
      String callbackId,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException) {
    InterceptCallbackRequestMessage request =
        messageBuilder.buildInterceptCallbackRequest(
            peerUuid,
            interceptMessage,
            execMessage,
            InterceptPhase.AFTER,
            returnValue,
            isVoid,
            thrownException);
    request.setCallbackId(callbackId);
    return request;
  }

  /**
   * Consolidated response for AROUND intercept BEFORE phase callbacks.
   *
   * <p>Contains the result of processing AROUND BEFORE callbacks, including whether to proceed with
   * method execution and any pending callbacks for the AFTER phase.
   */
  public static class AroundConsolidatedResponse {
    /** Whether the intercepted method should proceed with execution. */
    private final boolean shouldProceed;

    /** Map of argument index to mutated value. */
    private final Map<Integer, Object> mutatedArgs;

    /** Exception to throw instead of normal execution (may be null). */
    private final Throwable exceptionToThrow;

    /** Overridden return value when shouldProceed=false (may be null). */
    private final Object skipReturnValue;

    /** Pending callbacks that need AFTER phase (only if shouldProceed=true). */
    private final List<AroundCallbackState> pendingCallbacks;

    /**
     * Constructs an AroundConsolidatedResponse for proceeding with execution.
     *
     * @param shouldProceed whether execution should proceed
     * @param mutatedArgs map of argument index to mutated value
     * @param exceptionToThrow exception to throw (may be null)
     * @param pendingCallbacks callbacks that need AFTER phase
     */
    public AroundConsolidatedResponse(
        boolean shouldProceed,
        Map<Integer, Object> mutatedArgs,
        Throwable exceptionToThrow,
        List<AroundCallbackState> pendingCallbacks) {
      this.shouldProceed = shouldProceed;
      this.mutatedArgs = mutatedArgs;
      this.exceptionToThrow = exceptionToThrow;
      this.skipReturnValue = null;
      this.pendingCallbacks = pendingCallbacks;
    }

    /**
     * Constructs an AroundConsolidatedResponse for skipping execution.
     *
     * @param skipReturnValue the return value to use instead of executing
     * @param exceptionToThrow exception to throw (may be null)
     */
    private AroundConsolidatedResponse(Object skipReturnValue, Throwable exceptionToThrow) {
      this.shouldProceed = false;
      this.mutatedArgs = new HashMap<>();
      this.exceptionToThrow = exceptionToThrow;
      this.skipReturnValue = skipReturnValue;
      this.pendingCallbacks = new ArrayList<>();
    }

    /**
     * Creates a default "proceed" response with no pending callbacks.
     *
     * @return a proceed response
     */
    public static AroundConsolidatedResponse proceed() {
      return new AroundConsolidatedResponse(true, new HashMap<>(), null, new ArrayList<>());
    }

    /**
     * Creates a "skip" response with the specified return value.
     *
     * @param returnValue the return value to use instead of executing
     * @param exceptionToThrow exception to throw (may be null)
     * @return a skip response
     */
    public static AroundConsolidatedResponse skipWithReturn(
        Object returnValue, Throwable exceptionToThrow) {
      return new AroundConsolidatedResponse(returnValue, exceptionToThrow);
    }

    /** Returns whether execution should proceed. */
    public boolean shouldProceed() {
      return shouldProceed;
    }

    /** Returns whether any arguments were mutated. */
    public boolean hasArgMutations() {
      return !mutatedArgs.isEmpty();
    }

    /** Returns the map of mutated arguments. */
    public Map<Integer, Object> getMutatedArgs() {
      return mutatedArgs;
    }

    /** Returns whether an exception should be thrown. */
    public boolean shouldThrowException() {
      return exceptionToThrow != null;
    }

    /** Returns the exception to throw. */
    public Throwable getExceptionToThrow() {
      return exceptionToThrow;
    }

    /** Returns the return value for skip (when shouldProceed=false). */
    public Object getSkipReturnValue() {
      return skipReturnValue;
    }

    /** Returns the pending callbacks for AFTER phase. */
    public List<AroundCallbackState> getPendingCallbacks() {
      return pendingCallbacks;
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
