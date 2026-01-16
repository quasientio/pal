/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;

/**
 * Interface for AROUND intercept socket operations.
 *
 * <p>Allows {@code ctx.proceed()} to send/receive on the socket-owning thread's socket without
 * violating ZMQ's threading constraints. ZMQ sockets cannot be shared across threads, so all socket
 * operations must happen in the thread that owns the socket.
 *
 * <p>Implementations are created by {@code SocketRpcInvoker} and passed to the callback dispatcher
 * for AROUND intercepts.
 *
 * <p><b>Thread Safety:</b> Implementations are single-threaded by design. The accessor is created
 * and used entirely within a single socket-owning thread.
 *
 * @see InterceptContext#proceed()
 */
@FunctionalInterface
public interface AroundSocketAccessor {

  /**
   * Sends BEFORE response and blocks until AFTER request arrives.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Sends the BEFORE phase response to the interceptable peer
   *   <li>Blocks waiting for the AFTER phase request (method execution result)
   *   <li>Parses and returns the AFTER phase data
   * </ol>
   *
   * @param beforeResponse the BEFORE phase response to send
   * @param timeoutMs timeout for receiving AFTER request in milliseconds (0 = infinite)
   * @return the AFTER phase data from interceptable peer
   * @throws AroundTimeoutException if timeout exceeded waiting for AFTER request
   */
  AfterPhaseData sendBeforeAndReceiveAfter(
      InterceptCallbackResponseMessage beforeResponse, int timeoutMs) throws AroundTimeoutException;
}
