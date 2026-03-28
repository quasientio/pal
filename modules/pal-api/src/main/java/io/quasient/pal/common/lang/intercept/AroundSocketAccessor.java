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
   * @param proceedTimeoutMs timeout for receiving AFTER request in milliseconds (0 = infinite)
   * @return the AFTER phase data from interceptable peer
   * @throws AroundTimeoutException if timeout exceeded waiting for AFTER request
   */
  AfterPhaseData sendBeforeAndReceiveAfter(
      InterceptCallbackResponseMessage beforeResponse, int proceedTimeoutMs)
      throws AroundTimeoutException;
}
