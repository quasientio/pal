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

import com.quasient.pal.common.lang.intercept.InterceptCallback;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds {@link AroundInterceptChain} instances from intercept check results.
 *
 * <p>This class coordinates between {@link CallbackResolver} for local callbacks and {@link
 * InterceptCallbackDispatcher} for remote callbacks.
 */
@Singleton
public class AroundInterceptChainBuilder {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(AroundInterceptChainBuilder.class);

  /** Resolver for local callbacks. */
  private final CallbackResolver callbackResolver;

  /** Dispatcher for remote callbacks. */
  private final InterceptCallbackDispatcher remoteDispatcher;

  /** This peer's UUID. */
  private final UUID peerUuid;

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
    this.peerUuid = peerUuid;
  }

  /**
   * Builds an AROUND chain from intercept check results.
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

    AroundInterceptChain.Builder builder = AroundInterceptChain.builder();

    // Add local AROUND intercepts first (outermost)
    if (interceptCheckResult.hasLocalIntercepts()) {
      List<InterceptMessage> localArounds =
          interceptCheckResult.getLocalIntercepts().stream()
              .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
              .toList();

      for (InterceptMessage im : localArounds) {
        try {
          InterceptCallback callback =
              callbackResolver.resolve(null, im.getCallbackClass(), im.getCallbackMethod());
          builder.addLocal(im, callback, className, methodName, paramTypes, peerUuid.toString());
        } catch (Exception e) {
          logger.error(
              "Failed to resolve local AROUND callback: class={}, method={}",
              im.getCallbackClass(),
              im.getCallbackMethod(),
              e);
        }
      }
    }

    // Add remote AROUND intercepts (inner layers)
    if (interceptCheckResult.hasRemoteIntercepts()) {
      List<InterceptMessage> remoteArounds =
          interceptCheckResult.getRemoteIntercepts().stream()
              .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
              .toList();

      for (InterceptMessage im : remoteArounds) {
        UUID callbackPeerUuid = UUID.fromString(im.getPeerUuid());
        builder.addRemote(im, callbackPeerUuid);
      }
    }

    // Set the method invoker and remote dispatcher
    builder.methodInvoker(methodInvoker);
    builder.remoteDispatcher(createRemoteDispatcher());

    return builder.build();
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
