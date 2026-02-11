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

import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link AroundInterceptChain} instances from intercept check results.
 *
 * <p>This class coordinates between {@link CallbackResolver} for local callbacks and {@link
 * InterceptCallbackDispatcher} for remote callbacks.
 *
 * <p>Optimizations (vs. the original non-optimized path):
 *
 * <ul>
 *   <li>Accepts pre-partitioned AROUND intercept lists, eliminating {@code
 *       stream().filter().toList()} calls
 *   <li>Pools the {@link AroundInterceptChain.Builder} via {@code ThreadLocal} to avoid per-build
 *       allocation
 *   <li>Replaces {@code UUID.randomUUID()} with an {@link AtomicLong} counter for callback IDs
 *   <li>Caches parsed {@link UUID} objects to avoid repeated {@code UUID.fromString()} calls
 *   <li>Reuses a single {@link AroundInterceptChain.RemoteAroundDispatcher} instance
 * </ul>
 */
@Singleton
public class AroundInterceptChainBuilder {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(AroundInterceptChainBuilder.class);

  /** Thread-local pooled builder to avoid per-build allocation. */
  private static final ThreadLocal<AroundInterceptChain.Builder> TL_BUILDER =
      ThreadLocal.withInitial(AroundInterceptChain.Builder::new);

  /** Counter for generating unique callback IDs without UUID.randomUUID() overhead. */
  private static final AtomicLong CALLBACK_ID_COUNTER = new AtomicLong();

  /** Cache for parsed UUID objects, keyed by UUID string representation. */
  private final ConcurrentHashMap<String, UUID> uuidCache = new ConcurrentHashMap<>();

  /** Resolver for local callbacks. */
  private final CallbackResolver callbackResolver;

  /** Dispatcher for remote callbacks. */
  private final InterceptCallbackDispatcher remoteDispatcher;

  /** This peer's UUID as a string, cached to avoid repeated toString() calls. */
  private final String peerUuidString;

  /** Reusable remote dispatcher instance (stateless, delegates to remoteDispatcher). */
  private final AroundInterceptChain.RemoteAroundDispatcher reusableRemoteDispatcher;

  /**
   * Constructs a new AroundInterceptChainBuilder.
   *
   * @param callbackResolver the callback resolver for local callbacks
   * @param remoteDispatcher the dispatcher for remote callbacks
   * @param peerUuid this peer's UUID
   */
  @Inject
  public AroundInterceptChainBuilder(
      CallbackResolver callbackResolver,
      InterceptCallbackDispatcher remoteDispatcher,
      UUID peerUuid) {
    this.callbackResolver = callbackResolver;
    this.remoteDispatcher = remoteDispatcher;
    this.peerUuidString = peerUuid.toString();
    this.reusableRemoteDispatcher = createRemoteDispatcher();
  }

  /**
   * Builds an AROUND chain from intercept check results.
   *
   * <p>This is the backward-compatible entry point that delegates to the optimized {@link
   * #build(List, List, String, String, List, AroundInterceptChain.MethodInvoker)} method after
   * filtering AROUND intercepts from the check result.
   *
   * @param interceptCheckResult the intercept check result containing matched intercepts
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter types
   * @param methodInvoker the method invoker for the innermost layer
   * @return the built chain (may be empty if no AROUND intercepts)
   */
  public AroundInterceptChain build(
      InterceptCheckResult interceptCheckResult,
      String className,
      String methodName,
      List<String> paramTypes,
      AroundInterceptChain.MethodInvoker methodInvoker) {

    AroundInterceptChain.Builder builder = TL_BUILDER.get();
    builder.reset();

    // Add local AROUND intercepts first (outermost)
    if (interceptCheckResult.hasLocalIntercepts()) {
      List<InterceptMessage> localArounds =
          interceptCheckResult.getLocalIntercepts().stream()
              .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
              .toList();

      addLocalAroundsToBuilder(builder, localArounds, className, methodName, paramTypes);
    }

    // Add remote AROUND intercepts (inner layers)
    if (interceptCheckResult.hasRemoteIntercepts()) {
      List<InterceptMessage> remoteArounds =
          interceptCheckResult.getRemoteIntercepts().stream()
              .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
              .toList();

      addRemoteAroundsToBuilder(builder, remoteArounds);
    }

    // Set the method invoker and remote dispatcher
    builder.methodInvoker(methodInvoker);
    builder.remoteDispatcher(reusableRemoteDispatcher);

    return builder.build();
  }

  /**
   * Builds an AROUND chain from pre-partitioned local and remote AROUND intercept lists.
   *
   * <p>This is the optimized entry point that accepts pre-partitioned lists directly from {@link
   * InterceptPartition}, eliminating the internal {@code stream().filter().toList()} calls. The
   * builder is pooled via {@code ThreadLocal} to avoid per-build allocation.
   *
   * @param localAroundIntercepts pre-partitioned list of local AROUND intercepts
   * @param remoteAroundIntercepts pre-partitioned list of remote AROUND intercepts
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter types
   * @param methodInvoker the method invoker for the innermost layer
   * @return the built chain (may be empty if no AROUND intercepts)
   */
  public AroundInterceptChain build(
      List<InterceptMessage> localAroundIntercepts,
      List<InterceptMessage> remoteAroundIntercepts,
      String className,
      String methodName,
      List<String> paramTypes,
      AroundInterceptChain.MethodInvoker methodInvoker) {

    AroundInterceptChain.Builder builder = TL_BUILDER.get();
    builder.reset();

    // Add local AROUND intercepts first (outermost) - no filtering needed
    addLocalAroundsToBuilder(builder, localAroundIntercepts, className, methodName, paramTypes);

    // Add remote AROUND intercepts (inner layers) - no filtering needed
    addRemoteAroundsToBuilder(builder, remoteAroundIntercepts);

    // Set the method invoker and remote dispatcher
    builder.methodInvoker(methodInvoker);
    builder.remoteDispatcher(reusableRemoteDispatcher);

    return builder.build();
  }

  /**
   * Adds local AROUND intercepts to the builder, resolving callbacks for each.
   *
   * @param builder the builder to add handles to
   * @param localArounds the local AROUND intercepts
   * @param className the intercepted class name
   * @param methodName the intercepted method name
   * @param paramTypes the parameter types
   */
  private void addLocalAroundsToBuilder(
      AroundInterceptChain.Builder builder,
      List<InterceptMessage> localArounds,
      String className,
      String methodName,
      List<String> paramTypes) {
    for (int i = 0; i < localArounds.size(); i++) {
      InterceptMessage im = localArounds.get(i);
      try {
        InterceptCallback callback =
            callbackResolver.resolve(null, im.getCallbackClass(), im.getCallbackMethod());
        builder.addLocal(im, callback, className, methodName, paramTypes, peerUuidString);
      } catch (Exception e) {
        logger.error(
            "Failed to resolve local AROUND callback: class={}, method={}",
            im.getCallbackClass(),
            im.getCallbackMethod(),
            e);
      }
    }
  }

  /**
   * Adds remote AROUND intercepts to the builder, using cached UUID parsing and counter-based
   * callback IDs.
   *
   * @param builder the builder to add handles to
   * @param remoteArounds the remote AROUND intercepts
   */
  private void addRemoteAroundsToBuilder(
      AroundInterceptChain.Builder builder, List<InterceptMessage> remoteArounds) {
    for (int i = 0; i < remoteArounds.size(); i++) {
      InterceptMessage im = remoteArounds.get(i);
      UUID callbackPeerUuid = parsePeerUuid(im.getPeerUuid());
      String callbackId = peerUuidString + "-" + CALLBACK_ID_COUNTER.getAndIncrement();
      builder.addRemote(im, callbackPeerUuid, callbackId);
    }
  }

  /**
   * Parses a UUID string, caching the result for subsequent lookups.
   *
   * <p>This avoids repeated {@code UUID.fromString()} calls for the same peer UUID across multiple
   * chain builds.
   *
   * @param uuidString the UUID string to parse
   * @return the parsed UUID
   */
  private UUID parsePeerUuid(String uuidString) {
    return uuidCache.computeIfAbsent(uuidString, UUID::fromString);
  }

  /**
   * Creates a RemoteAroundDispatcher that uses the InterceptCallbackDispatcher.
   *
   * @return the remote dispatcher
   */
  private AroundInterceptChain.RemoteAroundDispatcher createRemoteDispatcher() {
    return new AroundInterceptChain.RemoteAroundDispatcher() {
      @Override
      public AroundInterceptChain.RemoteAroundBeforeResult sendBefore(
          AroundInterceptChain.RemoteAroundHandle handle, ExecMessage execMessage, Object[] args) {
        return remoteDispatcher.sendChainedAroundBefore(
            handle.intercept(), handle.callbackPeerUuid(), handle.callbackId(), execMessage, args);
      }

      @Override
      public AroundInterceptChain.RemoteAroundAfterResult sendAfter(
          AroundInterceptChain.RemoteAroundHandle handle,
          ExecMessage execMessage,
          Object returnValue,
          boolean isVoid,
          Throwable thrownException) {
        return remoteDispatcher.sendChainedAroundAfter(
            handle.intercept(),
            handle.callbackPeerUuid(),
            handle.callbackId(),
            execMessage,
            returnValue,
            isVoid,
            thrownException);
      }
    };
  }
}
