/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

/**
 * Functional interface for handling intercept callbacks within the PAL runtime.
 *
 * <p>Implementations of this interface are invoked when an intercept matches an operation. The
 * callback can inspect operation metadata, access and modify arguments, override return values, and
 * control execution flow.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe. For {@link InterceptType#AROUND}
 * intercepts, the same callback instance may be invoked concurrently for different operations, and
 * will be invoked twice sequentially (BEFORE and AFTER phases) for each individual operation. Any
 * shared state must be properly synchronized.
 *
 * <p><b>Basic Usage:</b>
 *
 * <pre>{@code
 * public class MyCallback implements InterceptCallback {
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // Access operation details
 *         String methodName = ctx.getExec().getMethodName();
 *         Object[] args = ctx.getArgs();
 *
 *         // Modify arguments (BEFORE phase)
 *         if (ctx.getPhase() == InterceptPhase.BEFORE) {
 *             ctx.setArg(0, transformedValue);
 *         }
 *
 *         // Access/modify return value (AFTER phase)
 *         if (ctx.getPhase() == InterceptPhase.AFTER) {
 *             Object returnValue = ctx.getReturnValue();
 *             ctx.setReturnValue(transformedReturnValue);
 *         }
 *
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p>For practical examples including argument modification, return value override, and caching
 * implementations, see the "Writing Callback Handlers" guide in the user documentation.
 *
 * @see InterceptContext
 * @see InterceptCallbackResponse
 * @see InterceptPhase
 * @see InterceptType
 */
@FunctionalInterface
public interface InterceptCallback {

  /**
   * Handles an intercept callback invocation.
   *
   * <p>This method is called when an intercept matches an operation. For {@link
   * InterceptType#AROUND} intercepts, this method will be called twice: once in the {@link
   * InterceptPhase#BEFORE} phase (before method execution) and once in the {@link
   * InterceptPhase#AFTER} phase (after method execution).
   *
   * <p>The callback can:
   *
   * <ul>
   *   <li><b>BEFORE phase:</b> Inspect/modify arguments, decide whether to proceed (AROUND only)
   *   <li><b>AFTER phase:</b> Access the return value or exception, override return value or
   *       exception
   *   <li><b>Both phases:</b> Throw exceptions to alter control flow
   * </ul>
   *
   * @param ctx the intercept context providing access to operation metadata, arguments, return
   *     values, and modification methods
   * @return the callback response indicating any modifications to execution flow, arguments, or
   *     return values
   * @throws Exception if the callback encounters an error or wants to throw an exception to the
   *     intercepted peer
   */
  InterceptCallbackResponse handle(InterceptContext ctx) throws Exception;
}
