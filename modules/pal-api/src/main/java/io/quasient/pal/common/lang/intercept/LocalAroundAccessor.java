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

import javax.annotation.Nullable;

/**
 * Interface for local AROUND intercept method invocation.
 *
 * <p>Unlike {@link AroundSocketAccessor} which communicates over the network for remote intercepts,
 * {@code LocalAroundAccessor} directly invokes the intercepted method within the same JVM. This
 * eliminates serialization overhead and network latency for same-peer intercepts.
 *
 * <p>Implementations are created by the dispatch flow in {@code BaseExecMessageDispatcher} and
 * capture the necessary context (join point, target object) to invoke the method when {@link
 * InterceptContext#proceed()} is called.
 *
 * <p><b>Benefits of local invocation:</b>
 *
 * <ul>
 *   <li>No serialization: Arguments and return values are live Java objects
 *   <li>No network latency: Direct method call (~1μs vs ~1ms for remote)
 *   <li>Same heap: No ObjectRef translation needed
 *   <li>Simpler error handling: No timeouts or network failures
 * </ul>
 *
 * <p><b>Thread Safety:</b> Implementations are single-threaded by design. The accessor is created
 * and used entirely within the dispatch thread.
 *
 * @see AroundSocketAccessor for remote AROUND intercepts
 * @see InterceptContext#proceed()
 */
@FunctionalInterface
public interface LocalAroundAccessor {

  /**
   * Invokes the intercepted method with the given arguments and returns the result.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Invokes the actual method (via AspectJ's {@code ProceedingJoinPoint.proceed()})
   *   <li>Captures the return value or thrown exception
   *   <li>Returns the result as {@link AfterPhaseData}
   * </ol>
   *
   * <p>If the method throws an exception, it is captured in {@link
   * AfterPhaseData#thrownException()} rather than propagated. This allows the callback to inspect
   * the exception and decide whether to suppress it, transform it, or let it propagate.
   *
   * @param args the arguments to pass to the method (may be mutated from original)
   * @return the result of method execution, including return value or thrown exception
   */
  AfterPhaseData invokeMethod(@Nullable Object[] args);
}
