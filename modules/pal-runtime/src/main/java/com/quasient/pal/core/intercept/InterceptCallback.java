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
 * <p><b>Example: BEFORE intercept to modify arguments</b>
 *
 * <pre>{@code
 * public class UpperCaseCurrencyCallback implements InterceptCallback {
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         if (ctx.getPhase() == InterceptPhase.BEFORE && ctx.getArgs().length > 0) {
 *             Object firstArg = ctx.getArgs()[0];
 *             if (firstArg instanceof String) {
 *                 ctx.setArg(0, ((String) firstArg).toUpperCase());
 *             }
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example: AFTER intercept to override return value</b>
 *
 * <pre>{@code
 * public class RedactSsnCallback implements InterceptCallback {
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         if (ctx.getPhase() == InterceptPhase.AFTER) {
 *             CustomerDto dto = (CustomerDto) ctx.getReturnValue();
 *             if (dto != null) {
 *                 dto.setSsn("***-**-****");
 *                 ctx.setReturnValue(dto);
 *             }
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example: AROUND intercept with proceed control</b>
 *
 * <pre>{@code
 * public class CachingCallback implements InterceptCallback {
 *     private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         String cacheKey = ctx.getExec().toString();
 *
 *         if (ctx.getPhase() == InterceptPhase.BEFORE) {
 *             Object cached = cache.get(cacheKey);
 *             if (cached != null) {
 *                 // Skip execution and return cached value
 *                 InterceptCallbackResponse response = new InterceptCallbackResponse();
 *                 response.setShouldProceed(false);
 *                 response.setNewReturnValue(cached);
 *                 return response;
 *             }
 *         } else if (ctx.getPhase() == InterceptPhase.AFTER) {
 *             // Cache the result
 *             cache.put(cacheKey, ctx.getReturnValue());
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
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
