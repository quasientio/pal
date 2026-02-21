/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.core.execution.ThreadAffinityDispatcher;
import io.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import io.quasient.pal.core.intercept.AroundInterceptChainBuilder;
import io.quasient.pal.core.intercept.InFlightDispatchTracker;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher;
import io.quasient.pal.core.intercept.InterceptChecker;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a foundational implementation for dispatchers by configuring essential components via
 * dependency injection.
 *
 * <p>Subclasses should extend this abstract class to implement specific dispatch logic.
 */
abstract class AbstractDispatcher {
  /** Logger instance used for logging internal events and error messages. */
  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  /** Unique identifier for the peer involved in dispatch operations. */
  protected UUID peerUuid;

  /** Set of runtime options that dictate behavior such as, PUB, WAL, intercepts, etc. */
  protected Set<RunOptions> runOptions;

  /** Instance used to construct messages. */
  protected MessageBuilder messageBuilder;

  /** Store used for mapping object references. */
  protected ObjectLookupStore objectLookupStore;

  /** Helper instance used to perform reflective operations on classes and methods. */
  protected ReflectionHelper reflectionHelper;

  /** Connector responsible for managing the routing and dispatching of calls. */
  protected OutboundMessageGateway messageGateway;

  /**
   * Flag indicating whether non-public methods and fields can be accessed during RPC invocation.
   */
  protected boolean allowNonPublicAccess;

  /** Checker for matching intercepts without creating ExecMessage (hot-path optimization). */
  protected InterceptChecker interceptChecker;

  /** Dispatcher for sending intercept callbacks to remote peers. */
  protected InterceptCallbackDispatcher interceptCallbackDispatcher;

  /** Dispatcher for sending intercept callbacks to local handlers (same JVM). */
  protected LocalInterceptCallbackDispatcher localInterceptCallbackDispatcher;

  /** Builder for AROUND intercept chains. */
  protected AroundInterceptChainBuilder aroundChainBuilder;

  /** Tracker for in-flight dispatch operations, used for intercept coordination. */
  protected InFlightDispatchTracker inFlightDispatchTracker;

  /**
   * Whether the source log and WAL refer to the same log. Used as a circularity guard to prevent
   * infinite feedback loops when writing incoming LOG_RPC messages to WAL.
   */
  @SuppressFBWarnings(
      value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD",
      justification = "Wired for use by dispatchIncoming() in BaseExecMessageDispatcher")
  protected boolean sourceAndWalAreSameLog;

  /**
   * Dispatcher for routing invocations based on thread affinity (e.g., FX thread).
   *
   * <p>Initialized with a default instance (direct execution only) so that {@code
   * dispatchIncoming()} is safe before Guice injection completes or in test harnesses that do not
   * inject this field.
   */
  protected ThreadAffinityDispatcher threadAffinityDispatcher = new ThreadAffinityDispatcher();

  /**
   * Sets the unique identifier (UUID) for the peer.
   *
   * @param peerUuid the unique identifier assigned to the peer; must not be null.
   */
  @Inject
  final void setPeerUuid(UUID peerUuid) {
    this.peerUuid = peerUuid;
  }

  /**
   * Sets the {@link RunOptions}.
   *
   * @param runOptions the run options.
   */
  @Inject
  final void setRunOptions(Set<RunOptions> runOptions) {
    this.runOptions = runOptions;
  }

  /**
   * Specifies the {@link MessageBuilder} instance used for assembling messages.
   *
   * @param messageBuilder the message builder to use; cannot be null.
   */
  @Inject
  final void setMessageBuilder(MessageBuilder messageBuilder) {
    this.messageBuilder = messageBuilder;
  }

  /**
   * Configures the {@link ObjectLookupStore} for mapping object references.
   *
   * @param objectLookupStore the object lookup store to be used for mapping refs -> objects.
   */
  @Inject
  final void setObjectLookupStore(ObjectLookupStore objectLookupStore) {
    this.objectLookupStore = objectLookupStore;
  }

  /**
   * Assigns the {@link OutboundMessageGateway} which handles the routing of calls.
   *
   * @param messageGateway the gateway for routing outgoing messages; must be a valid, initialized
   *     instance.
   */
  @Inject
  final void setMessageGateway(OutboundMessageGateway messageGateway) {
    this.messageGateway = messageGateway;
  }

  /**
   * Injects the {@link ReflectionHelper} instance used for reflective access during RPC processing.
   *
   * @param reflectionHelper the helper instance to use for reflection; expected to be non-null.
   */
  @Inject
  final void setReflectionHelper(ReflectionHelper reflectionHelper) {
    this.reflectionHelper = reflectionHelper;
  }

  /**
   * Configures whether non-public access is allowed during RPC invocations.
   *
   * @param allowNonPublicAccess a String representing a boolean value ("true" or "false") that
   *     enables or disables non-public member access.
   */
  @Inject
  final void setAllowNonPublicAccess(@Named("rpc.allow_nonpublic") String allowNonPublicAccess) {
    this.allowNonPublicAccess = Boolean.parseBoolean(allowNonPublicAccess);
  }

  /**
   * Sets the {@link InterceptChecker} for checking intercepts without message creation.
   *
   * @param interceptChecker the intercept checker instance
   */
  @Inject
  final void setInterceptChecker(InterceptChecker interceptChecker) {
    this.interceptChecker = interceptChecker;
  }

  /**
   * Sets the {@link InterceptCallbackDispatcher} for sending intercept callbacks.
   *
   * @param interceptCallbackDispatcher the callback dispatcher instance
   */
  @Inject
  final void setInterceptCallbackDispatcher(
      InterceptCallbackDispatcher interceptCallbackDispatcher) {
    this.interceptCallbackDispatcher = interceptCallbackDispatcher;
  }

  /**
   * Sets the {@link LocalInterceptCallbackDispatcher} for sending local intercept callbacks.
   *
   * @param localInterceptCallbackDispatcher the local callback dispatcher instance
   */
  @Inject
  final void setLocalInterceptCallbackDispatcher(
      LocalInterceptCallbackDispatcher localInterceptCallbackDispatcher) {
    this.localInterceptCallbackDispatcher = localInterceptCallbackDispatcher;
  }

  /**
   * Sets the {@link AroundInterceptChainBuilder} for building AROUND intercept chains.
   *
   * @param aroundChainBuilder the chain builder instance
   */
  @Inject
  final void setAroundChainBuilder(AroundInterceptChainBuilder aroundChainBuilder) {
    this.aroundChainBuilder = aroundChainBuilder;
  }

  /**
   * Sets the {@link InFlightDispatchTracker} for tracking in-flight dispatch operations.
   *
   * <p>This tracker enables coordinated intercept activation with guaranteed quiescence, allowing
   * intercepts to wait for in-flight method calls to complete before activation.
   *
   * @param inFlightDispatchTracker the in-flight dispatch tracker instance
   */
  @Inject
  final void setInFlightDispatchTracker(InFlightDispatchTracker inFlightDispatchTracker) {
    this.inFlightDispatchTracker = inFlightDispatchTracker;
  }

  /**
   * Sets the {@link ThreadAffinityDispatcher} for routing invocations based on thread affinity.
   *
   * <p>This dispatcher routes incoming RPC invocations to the appropriate thread based on the
   * thread affinity specified in the {@link io.quasient.pal.messages.colfer.ExecMessage}. For
   * example, calls tagged with {@code "fx-thread"} affinity are routed to the JavaFX Application
   * Thread.
   *
   * @param threadAffinityDispatcher the thread affinity dispatcher instance
   */
  @Inject
  final void setThreadAffinityDispatcher(ThreadAffinityDispatcher threadAffinityDispatcher) {
    this.threadAffinityDispatcher = threadAffinityDispatcher;
  }

  /**
   * Sets whether the source log and WAL are the same log.
   *
   * <p>When {@code true}, the {@code WITH_WAL_ALL_INCOMING_RPC} option is overridden by a
   * circularity guard to prevent infinite feedback loops from writing LOG_RPC messages back to the
   * same log they were read from.
   *
   * @param sourceAndWalAreSameLog string representation of the boolean flag
   */
  @Inject
  final void setSourceAndWalAreSameLog(
      @Named("log.sourceAndWalAreSameLog") String sourceAndWalAreSameLog) {
    this.sourceAndWalAreSameLog = Boolean.parseBoolean(sourceAndWalAreSameLog);
  }
}
