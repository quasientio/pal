/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.local;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Callback class for local intercept integration tests.
 *
 * <p>This class provides static callback methods that can be registered for local intercepts. Each
 * callback method records its invocation in thread-safe counters and lists, allowing integration
 * tests to verify that callbacks were invoked correctly.
 *
 * <p>All state is static because local intercept callbacks are resolved via reflection on static
 * methods or instance methods on a static singleton. The state must be reset between tests using
 * {@link #reset()}.
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
@SuppressFBWarnings(
    value = {"EI_EXPOSE_STATIC_REP2", "MS_EXPOSE_REP"},
    justification = "Test helper class; mutable static state is intentional for test coordination")
public final class LocalInterceptCallbacks {

  /** Logger for callback invocations - tests verify callbacks via log output. */
  private static final Logger logger = LoggerFactory.getLogger(LocalInterceptCallbacks.class);

  /** Counter for BEFORE callback invocations. */
  private static final AtomicInteger beforeCallCount = new AtomicInteger(0);

  /** Counter for AFTER callback invocations. */
  private static final AtomicInteger afterCallCount = new AtomicInteger(0);

  /** Counter for AROUND callback invocations. */
  private static final AtomicInteger aroundCallCount = new AtomicInteger(0);

  /** Counter for BEFORE_ASYNC callback invocations. */
  private static final AtomicInteger beforeAsyncCallCount = new AtomicInteger(0);

  /** Counter for AFTER_ASYNC callback invocations. */
  private static final AtomicInteger afterAsyncCallCount = new AtomicInteger(0);

  /** Records class names from BEFORE callbacks. */
  private static final CopyOnWriteArrayList<String> beforeClassNames = new CopyOnWriteArrayList<>();

  /** Records method names from BEFORE callbacks. */
  private static final CopyOnWriteArrayList<String> beforeMethodNames =
      new CopyOnWriteArrayList<>();

  /** Records class names from AFTER callbacks. */
  private static final CopyOnWriteArrayList<String> afterClassNames = new CopyOnWriteArrayList<>();

  /** Records method names from AFTER callbacks. */
  private static final CopyOnWriteArrayList<String> afterMethodNames = new CopyOnWriteArrayList<>();

  /** Records return values from AFTER callbacks. */
  private static final CopyOnWriteArrayList<Object> afterReturnValues =
      new CopyOnWriteArrayList<>();

  /** Stores the last InterceptContext received. */
  private static final AtomicReference<InterceptContext> lastContext = new AtomicReference<>();

  /** Latch for async callback synchronization. */
  private static volatile CountDownLatch asyncLatch = new CountDownLatch(0);

  /** Exception to throw from BEFORE callback (for testing exception handling). */
  private static volatile RuntimeException beforeExceptionToThrow = null;

  /** Exception to throw from AFTER callback (for testing exception handling). */
  private static volatile RuntimeException afterExceptionToThrow = null;

  /** Override return value for AFTER callback (for testing return value modification). */
  private static volatile Object afterReturnOverride = null;

  /** Flag to indicate if AFTER callback should override return value. */
  private static volatile boolean shouldOverrideReturn = false;

  /** Argument mutation for BEFORE callback (index -> new value). */
  private static volatile Integer argMutationIndex = null;

  /** New argument value for BEFORE callback mutation. */
  private static volatile Object argMutationValue = null;

  private LocalInterceptCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    beforeCallCount.set(0);
    afterCallCount.set(0);
    aroundCallCount.set(0);
    beforeAsyncCallCount.set(0);
    afterAsyncCallCount.set(0);
    beforeClassNames.clear();
    beforeMethodNames.clear();
    afterClassNames.clear();
    afterMethodNames.clear();
    afterReturnValues.clear();
    lastContext.set(null);
    asyncLatch = new CountDownLatch(0);
    beforeExceptionToThrow = null;
    afterExceptionToThrow = null;
    afterReturnOverride = null;
    shouldOverrideReturn = false;
    argMutationIndex = null;
    argMutationValue = null;
  }

  // ==================== Configuration ====================

  /**
   * Configures an exception to be thrown from the BEFORE callback.
   *
   * @param ex the exception to throw
   */
  public static void setBeforeExceptionToThrow(RuntimeException ex) {
    beforeExceptionToThrow = ex;
  }

  /**
   * Configures an exception to be thrown from the AFTER callback.
   *
   * @param ex the exception to throw
   */
  public static void setAfterExceptionToThrow(RuntimeException ex) {
    afterExceptionToThrow = ex;
  }

  /**
   * Configures a return value override for AFTER callbacks.
   *
   * @param value the value to return instead of the original
   */
  public static void setAfterReturnOverride(Object value) {
    shouldOverrideReturn = true;
    afterReturnOverride = value;
  }

  /**
   * Configures an argument mutation for BEFORE callbacks.
   *
   * @param index the argument index to mutate
   * @param value the new argument value
   */
  public static void setArgMutation(int index, Object value) {
    argMutationIndex = index;
    argMutationValue = value;
  }

  /**
   * Sets up a latch for async callback synchronization.
   *
   * @param count the number of async callbacks to wait for
   */
  public static void setAsyncLatch(int count) {
    asyncLatch = new CountDownLatch(count);
  }

  /**
   * Waits for async callbacks to complete.
   *
   * @param timeoutMs the maximum time to wait in milliseconds
   * @return true if all async callbacks completed, false if timeout expired
   * @throws InterruptedException if interrupted while waiting
   */
  public static boolean awaitAsyncCallbacks(long timeoutMs) throws InterruptedException {
    return asyncLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  // ==================== Getters ====================

  /**
   * Returns the number of BEFORE callback invocations.
   *
   * @return the call count
   */
  public static int getBeforeCallCount() {
    return beforeCallCount.get();
  }

  /**
   * Returns the number of AFTER callback invocations.
   *
   * @return the call count
   */
  public static int getAfterCallCount() {
    return afterCallCount.get();
  }

  /**
   * Returns the number of AROUND callback invocations.
   *
   * @return the call count
   */
  public static int getAroundCallCount() {
    return aroundCallCount.get();
  }

  /**
   * Returns the number of BEFORE_ASYNC callback invocations.
   *
   * @return the call count
   */
  public static int getBeforeAsyncCallCount() {
    return beforeAsyncCallCount.get();
  }

  /**
   * Returns the number of AFTER_ASYNC callback invocations.
   *
   * @return the call count
   */
  public static int getAfterAsyncCallCount() {
    return afterAsyncCallCount.get();
  }

  /**
   * Returns the class names recorded from BEFORE callbacks.
   *
   * @return list of class names
   */
  public static CopyOnWriteArrayList<String> getBeforeClassNames() {
    return beforeClassNames;
  }

  /**
   * Returns the method names recorded from BEFORE callbacks.
   *
   * @return list of method names
   */
  public static CopyOnWriteArrayList<String> getBeforeMethodNames() {
    return beforeMethodNames;
  }

  /**
   * Returns the class names recorded from AFTER callbacks.
   *
   * @return list of class names
   */
  public static CopyOnWriteArrayList<String> getAfterClassNames() {
    return afterClassNames;
  }

  /**
   * Returns the method names recorded from AFTER callbacks.
   *
   * @return list of method names
   */
  public static CopyOnWriteArrayList<String> getAfterMethodNames() {
    return afterMethodNames;
  }

  /**
   * Returns the return values recorded from AFTER callbacks.
   *
   * @return list of return values
   */
  public static CopyOnWriteArrayList<Object> getAfterReturnValues() {
    return afterReturnValues;
  }

  /**
   * Returns the last InterceptContext received.
   *
   * @return the last context, or null if none
   */
  public static InterceptContext getLastContext() {
    return lastContext.get();
  }

  // ==================== Callback Methods ====================

  /**
   * Extracts class name from the intercept context (supports both local and remote).
   *
   * @param ctx the intercept context
   * @return the class name
   */
  private static String extractClassName(InterceptContext ctx) {
    if (ctx.isLocalIntercept()) {
      return ctx.getLocalMetadata().className();
    }
    // Remote intercept - extract from ExecMessage (would need more logic)
    return "unknown";
  }

  /**
   * Extracts method name from the intercept context (supports both local and remote).
   *
   * @param ctx the intercept context
   * @return the method name
   */
  private static String extractMethodName(InterceptContext ctx) {
    if (ctx.isLocalIntercept()) {
      return ctx.getLocalMetadata().methodName();
    }
    // Remote intercept - extract from ExecMessage (would need more logic)
    return "unknown";
  }

  /**
   * BEFORE callback that records invocation details.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBefore(InterceptContext ctx) {
    int count = beforeCallCount.incrementAndGet();
    beforeClassNames.add(extractClassName(ctx));
    beforeMethodNames.add(extractMethodName(ctx));
    lastContext.set(ctx);

    logger.info(
        "LOCAL_BEFORE: class={}, method={}, count={}",
        extractClassName(ctx),
        extractMethodName(ctx),
        count);

    // Check for configured exception
    if (beforeExceptionToThrow != null) {
      throw beforeExceptionToThrow;
    }

    // Check for configured argument mutation
    if (argMutationIndex != null) {
      ctx.setArg(argMutationIndex, argMutationValue);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * AFTER callback that records invocation details.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfter(InterceptContext ctx) {
    int count = afterCallCount.incrementAndGet();
    afterClassNames.add(extractClassName(ctx));
    afterMethodNames.add(extractMethodName(ctx));
    if (!ctx.isVoid()) {
      afterReturnValues.add(ctx.getReturnValue());
    }
    lastContext.set(ctx);

    logger.info(
        "LOCAL_AFTER: class={}, method={}, count={}",
        extractClassName(ctx),
        extractMethodName(ctx),
        count);

    // Check for configured exception
    if (afterExceptionToThrow != null) {
      throw afterExceptionToThrow;
    }

    // Check for configured return override
    if (shouldOverrideReturn && !ctx.isVoid()) {
      ctx.setReturnValue(afterReturnOverride);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that records invocation and calls proceed().
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAround(InterceptContext ctx) {
    int count = aroundCallCount.incrementAndGet();
    lastContext.set(ctx);

    logger.info("LOCAL_AROUND: proceeding, count={}", count);

    // Proceed with the original invocation
    ctx.proceed();

    // Record return value if not void
    if (!ctx.isVoid()) {
      afterReturnValues.add(ctx.getReturnValue());
    }

    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that skips the original invocation.
   *
   * @param ctx the intercept context
   * @return the intercept response with override value
   */
  public static InterceptCallbackResponse onAroundSkip(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    lastContext.set(ctx);

    // Skip the original invocation, return a fixed value
    if (!ctx.isVoid()) {
      ctx.setReturnValue(afterReturnOverride);
    }

    InterceptCallbackResponse response = new InterceptCallbackResponse();
    response.setShouldProceed(false);
    return response;
  }

  /**
   * BEFORE_ASYNC callback that records invocation details.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAsync(InterceptContext ctx) {
    int count = beforeAsyncCallCount.incrementAndGet();
    beforeClassNames.add(extractClassName(ctx));
    beforeMethodNames.add(extractMethodName(ctx));
    lastContext.set(ctx);
    asyncLatch.countDown();

    logger.info(
        "LOCAL_BEFORE_ASYNC: class={}, method={}, count={}",
        extractClassName(ctx),
        extractMethodName(ctx),
        count);

    return new InterceptCallbackResponse();
  }

  /**
   * AFTER_ASYNC callback that records invocation details.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterAsync(InterceptContext ctx) {
    int count = afterAsyncCallCount.incrementAndGet();
    afterClassNames.add(extractClassName(ctx));
    afterMethodNames.add(extractMethodName(ctx));
    if (!ctx.isVoid()) {
      afterReturnValues.add(ctx.getReturnValue());
    }
    lastContext.set(ctx);
    asyncLatch.countDown();

    logger.info(
        "LOCAL_AFTER_ASYNC: class={}, method={}, count={}",
        extractClassName(ctx),
        extractMethodName(ctx),
        count);

    return new InterceptCallbackResponse();
  }
}
