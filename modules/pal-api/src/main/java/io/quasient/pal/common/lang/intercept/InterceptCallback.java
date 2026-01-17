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

/**
 * Functional interface for handling intercept callbacks within the PAL runtime.
 *
 * <p>Implementations of this interface are invoked when an intercept matches an operation. The
 * callback can inspect operation metadata, access and modify arguments, override return values, and
 * control execution flow.
 *
 * <p><b>Thread Safety:</b> Callback implementations must be thread-safe if they maintain shared
 * mutable state (e.g., static fields). The intercept mechanism itself does not introduce special
 * threading complexity - callbacks are invoked directly on the thread executing the intercepted
 * operation. If multiple threads execute intercepted operations concurrently, standard Java
 * concurrency concerns apply. For {@link InterceptType#AROUND} intercepts, the callback is invoked
 * once and calls {@link InterceptContext#proceed()} to transition from BEFORE to AFTER phase.
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
 * <p><b>Thread Safety Considerations:</b>
 *
 * <p>These are standard Java concurrency concerns that apply to any method invoked by multiple
 * threads. When multiple threads execute intercepted operations concurrently, they will invoke the
 * same static callback method concurrently. If your callback maintains shared mutable state (e.g.,
 * static fields for counters, caches, or accumulated data), you must use proper synchronization.
 *
 * <p>Here are common patterns for handling shared state in callbacks (these are standard Java
 * concurrency patterns, not intercept-specific):
 *
 * <p><b>1. PREFERRED: Stateless Callbacks</b>
 *
 * <p>The safest approach is stateless callbacks - no shared mutable state at all:
 *
 * <pre>{@code
 * public class ValidationCallback implements InterceptCallback {
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // No shared state - completely thread-safe
 *         Object[] args = ctx.getArgs();
 *         if (args.length > 0 && args[0] instanceof String) {
 *             String input = (String) args[0];
 *             if (input.length() > 100) {
 *                 ctx.setExceptionToThrow(
 *                     new IllegalArgumentException("Input too long"));
 *             }
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>2. Thread-Safe Collections</b>
 *
 * <p>For shared state, use concurrent collections from {@code java.util.concurrent}:
 *
 * <pre>{@code
 * public class CachingCallback implements InterceptCallback {
 *     // Thread-safe: ConcurrentHashMap handles concurrent access
 *     private final ConcurrentHashMap<String, Object> cache =
 *         new ConcurrentHashMap<>();
 *     private final AtomicInteger cacheHits = new AtomicInteger(0);
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         Object[] args = ctx.getArgs();
 *         String key = String.valueOf(args[0]);
 *
 *         // Check cache (thread-safe)
 *         Object cached = cache.get(key);
 *         if (cached != null) {
 *             cacheHits.incrementAndGet();  // Atomic increment
 *             ctx.setReturnValue(cached);
 *             return InterceptCallbackResponse.skipProceed();
 *         }
 *
 *         // Proceed and cache result
 *         ctx.proceed();
 *         if (!ctx.isVoid()) {
 *             cache.put(key, ctx.getReturnValue());
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>3. Manual Locking</b>
 *
 * <p>For non-concurrent collections, use explicit locks (standard practice - be careful with
 * deadlocks and critical section duration):
 *
 * <pre>{@code
 * public class ManualLockCallback implements InterceptCallback {
 *     // Non-concurrent collection requires manual locking
 *     private final HashMap<String, Object> cache = new HashMap<>();
 *     private final ReadWriteLock lock = new ReentrantReadWriteLock();
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         Object[] args = ctx.getArgs();
 *         String key = String.valueOf(args[0]);
 *
 *         // Read lock for cache lookup
 *         lock.readLock().lock();
 *         Object cached;
 *         try {
 *             cached = cache.get(key);
 *         } finally {
 *             lock.readLock().unlock();
 *         }
 *
 *         if (cached != null) {
 *             ctx.setReturnValue(cached);
 *             return InterceptCallbackResponse.skipProceed();
 *         }
 *
 *         ctx.proceed();
 *
 *         // Write lock for cache update
 *         if (!ctx.isVoid()) {
 *             lock.writeLock().lock();
 *             try {
 *                 cache.put(key, ctx.getReturnValue());
 *             } finally {
 *                 lock.writeLock().unlock();
 *             }
 *         }
 *         return new InterceptCallbackResponse();
 *     }
 * }
 * }</pre>
 *
 * <p><b>4. Common Mistake: Unsynchronized Shared State</b>
 *
 * <p><b>WARNING:</b> This example shows a common concurrency bug - unsynchronized access to shared
 * mutable state:
 *
 * <pre>{@code
 * public class UnsafeCallback implements InterceptCallback {
 *     // WRONG: Non-thread-safe collection without synchronization
 *     private final HashMap<String, Object> cache = new HashMap<>();
 *     private int count = 0;  // WRONG: Non-atomic counter
 *
 *     @Override
 *     public InterceptCallbackResponse handle(InterceptContext ctx) {
 *         // RACE CONDITION: Multiple threads can corrupt the HashMap
 *         String key = String.valueOf(ctx.getArgs()[0]);
 *         cache.put(key, "value");  // Concurrent puts can corrupt HashMap
 *
 *         // RACE CONDITION: Lost updates to counter
 *         count++;  // Not atomic - threads can overwrite each other's updates
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
   * <p>This method is called once when an intercept matches an operation. For {@link
   * InterceptType#AROUND} intercepts, the callback calls {@link InterceptContext#proceed()} to
   * execute the intercepted method, which transitions the context from BEFORE to AFTER phase within
   * the same invocation.
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
