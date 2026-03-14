/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.callbacks.local;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.InterceptApiMisuseException;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
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

  // ==================== Arg Mutation Callbacks ====================

  /**
   * BEFORE callback that mutates the first argument by doubling integer values.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeMutateArg(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      int original = (Integer) args[0];
      int mutated = original * 2;
      ctx.setArg(0, mutated);
      logger.info("LOCAL_BEFORE_MUTATE_ARG: {} -> {}", original, mutated);
    } else {
      logger.info("LOCAL_BEFORE_MUTATE_ARG: no mutation (args not suitable)");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that mutates argument before proceeding.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundMutateArgBeforeProceed(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    Object[] args = ctx.getArgs();
    if (args.length > 0 && args[0] instanceof Integer) {
      int original = (Integer) args[0];
      int mutated = original * 2;
      ctx.setArg(0, mutated);
      logger.info("LOCAL_AROUND_MUTATE_ARG: {} -> {}", original, mutated);
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  // ==================== Return Override Callbacks ====================

  /**
   * AFTER callback that overrides the return value by doubling integer values.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterOverrideReturn(InterceptContext ctx) {
    afterCallCount.incrementAndGet();
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int overridden = original * 2;
        ctx.setReturnValue(overridden);
        logger.info("LOCAL_AFTER_OVERRIDE_RETURN: {} -> {}", original, overridden);
      } else {
        logger.info("LOCAL_AFTER_OVERRIDE_RETURN: no override (return not Integer)");
      }
    } else {
      logger.info("LOCAL_AFTER_OVERRIDE_RETURN: no override (void method)");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that overrides return value after proceeding.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundOverrideReturnAfterProceed(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    ctx.proceed();
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int overridden = original * 2;
        ctx.setReturnValue(overridden);
        logger.info("LOCAL_AROUND_OVERRIDE_RETURN: {} -> {}", original, overridden);
      }
    }
    return new InterceptCallbackResponse();
  }

  // ==================== AROUND Skip Callbacks ====================

  /**
   * AROUND callback that skips proceed and returns a fixed value (42).
   *
   * @param ctx the intercept context
   * @return the intercept response with skip
   */
  public static InterceptCallbackResponse onAroundSkipWithReturn(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    ctx.setReturnValue(42);
    logger.info("LOCAL_AROUND_SKIP_WITH_RETURN: returning 42");
    return InterceptCallbackResponse.skipProceed();
  }

  /**
   * AROUND callback that skips proceed and returns null explicitly.
   *
   * @param ctx the intercept context
   * @return the intercept response with skip
   */
  public static InterceptCallbackResponse onAroundSkipWithNullReturn(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    ctx.setReturnValue(null);
    logger.info("LOCAL_AROUND_SKIP_WITH_NULL_RETURN: returning null");
    return InterceptCallbackResponse.skipProceed();
  }

  /**
   * AROUND callback that skips proceed and throws an exception.
   *
   * @param ctx the intercept context
   * @return the intercept response with skip
   */
  public static InterceptCallbackResponse onAroundSkipWithException(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    ctx.setExceptionToThrow(new SecurityException("Access denied by AROUND skip"));
    logger.info("LOCAL_AROUND_SKIP_WITH_EXCEPTION: throwing SecurityException");
    return InterceptCallbackResponse.skipProceed();
  }

  // ==================== Exception Throwing Callbacks ====================

  /**
   * BEFORE callback that sets a SecurityException via ctx.setExceptionToThrow().
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeThrowException(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    logger.info("LOCAL_BEFORE_THROW_EXCEPTION: setting SecurityException via ctx");
    ctx.setExceptionToThrow(new SecurityException("Access denied by BEFORE callback"));
    return new InterceptCallbackResponse();
  }

  /**
   * AFTER callback that sets a SecurityException via ctx.setExceptionToThrow().
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterThrowException(InterceptContext ctx) {
    afterCallCount.incrementAndGet();
    logger.info("LOCAL_AFTER_THROW_EXCEPTION: setting SecurityException via ctx");
    ctx.setExceptionToThrow(new SecurityException("Access denied by AFTER callback"));
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that sets a SecurityException and skips proceed.
   *
   * @param ctx the intercept context
   * @return the intercept response with skipProceed
   */
  public static InterceptCallbackResponse onAroundThrowException(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    logger.info("LOCAL_AROUND_THROW_EXCEPTION: setting SecurityException via ctx");
    ctx.setExceptionToThrow(new SecurityException("Access denied by AROUND callback"));
    return InterceptCallbackResponse.skipProceed();
  }

  // ==================== Illegal Operation Callbacks (BEFORE) ====================

  /**
   * BEFORE callback that attempts to call getReturnValue() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAttemptGetReturnValue(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    try {
      ctx.getReturnValue();
      logger.info("LOCAL_BEFORE_ILLEGAL_GET_RETURN: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_BEFORE_ILLEGAL_GET_RETURN: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * BEFORE callback that attempts to call setReturnValue() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAttemptSetReturnValue(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    try {
      ctx.setReturnValue(999);
      logger.info("LOCAL_BEFORE_ILLEGAL_SET_RETURN: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_BEFORE_ILLEGAL_SET_RETURN: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * BEFORE callback that attempts to call proceed() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAttemptProceed(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    try {
      ctx.proceed();
      logger.info("LOCAL_BEFORE_ILLEGAL_PROCEED: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_BEFORE_ILLEGAL_PROCEED: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * BEFORE callback that attempts to call getThrownException() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAttemptGetThrownException(InterceptContext ctx) {
    beforeCallCount.incrementAndGet();
    try {
      ctx.getThrownException();
      logger.info("LOCAL_BEFORE_ILLEGAL_GET_THROWN: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_BEFORE_ILLEGAL_GET_THROWN: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  // ==================== Illegal Operation Callbacks (AFTER) ====================

  /**
   * AFTER callback that attempts to call setArg() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterAttemptSetArg(InterceptContext ctx) {
    afterCallCount.incrementAndGet();
    try {
      ctx.setArg(0, 999);
      logger.info("LOCAL_AFTER_ILLEGAL_SET_ARG: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_AFTER_ILLEGAL_SET_ARG: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * AFTER callback that attempts to call proceed() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterAttemptProceed(InterceptContext ctx) {
    afterCallCount.incrementAndGet();
    try {
      ctx.proceed();
      logger.info("LOCAL_AFTER_ILLEGAL_PROCEED: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info("LOCAL_AFTER_ILLEGAL_PROCEED: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  // ==================== Illegal Operation Callbacks (AROUND) ====================

  /**
   * AROUND callback that attempts getReturnValue() before proceed() (should throw IllegalState).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundAttemptGetReturnBeforeProceed(
      InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    try {
      ctx.getReturnValue();
      logger.info("LOCAL_AROUND_ILLEGAL_GET_RETURN_BEFORE_PROCEED: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_AROUND_ILLEGAL_GET_RETURN_BEFORE_PROCEED: correctly threw InterceptApiMisuseException");
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that attempts getThrownException() before proceed() (should throw
   * IllegalState).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundAttemptGetThrownBeforeProceed(
      InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    try {
      ctx.getThrownException();
      logger.info("LOCAL_AROUND_ILLEGAL_GET_THROWN_BEFORE_PROCEED: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_AROUND_ILLEGAL_GET_THROWN_BEFORE_PROCEED: correctly threw InterceptApiMisuseException");
    }
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that attempts setArg() after proceed() (should throw IllegalState).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundAttemptSetArgAfterProceed(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    ctx.proceed();
    try {
      ctx.setArg(0, 999);
      logger.info("LOCAL_AROUND_ILLEGAL_SET_ARG_AFTER_PROCEED: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_AROUND_ILLEGAL_SET_ARG_AFTER_PROCEED: correctly threw InterceptApiMisuseException");
    }
    return new InterceptCallbackResponse();
  }

  /**
   * AROUND callback that attempts skipProceed() without setReturnValue() (should throw
   * IllegalState).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAroundSkipWithoutReturnValue(InterceptContext ctx) {
    aroundCallCount.incrementAndGet();
    logger.info("LOCAL_AROUND_SKIP_WITHOUT_RETURN: attempting skipProceed without setReturnValue");
    // Don't set return value, just skip - this should cause IllegalStateException
    return InterceptCallbackResponse.skipProceed();
  }

  // ==================== Illegal Operation Callbacks (ASYNC) ====================

  /**
   * BEFORE_ASYNC callback that attempts to call setArg() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onBeforeAsyncAttemptSetArg(InterceptContext ctx) {
    beforeAsyncCallCount.incrementAndGet();
    try {
      ctx.setArg(0, 999);
      logger.info("LOCAL_BEFORE_ASYNC_ILLEGAL_SET_ARG: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_BEFORE_ASYNC_ILLEGAL_SET_ARG: correctly threw InterceptApiMisuseException");
    }
    asyncLatch.countDown();
    return new InterceptCallbackResponse();
  }

  /**
   * AFTER_ASYNC callback that attempts to call setReturnValue() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterAsyncAttemptSetReturnValue(InterceptContext ctx) {
    afterAsyncCallCount.incrementAndGet();
    try {
      ctx.setReturnValue(999);
      logger.info("LOCAL_AFTER_ASYNC_ILLEGAL_SET_RETURN: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_AFTER_ASYNC_ILLEGAL_SET_RETURN: correctly threw InterceptApiMisuseException");
    }
    asyncLatch.countDown();
    return new InterceptCallbackResponse();
  }

  /**
   * AFTER_ASYNC callback that attempts to call setExceptionToThrow() (should throw UnsupportedOp).
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterAsyncAttemptSetException(InterceptContext ctx) {
    afterAsyncCallCount.incrementAndGet();
    try {
      ctx.setExceptionToThrow(new RuntimeException("test"));
      logger.info("LOCAL_AFTER_ASYNC_ILLEGAL_SET_EXCEPTION: ERROR - did not throw");
    } catch (InterceptApiMisuseException e) {
      logger.info(
          "LOCAL_AFTER_ASYNC_ILLEGAL_SET_EXCEPTION: correctly threw InterceptApiMisuseException");
    }
    asyncLatch.countDown();
    return new InterceptCallbackResponse();
  }

  // ==================== Void Method Checks ====================

  /**
   * AFTER callback that checks isVoid() on void method and attempts setReturnValue.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse onAfterVoidMethodSetReturn(InterceptContext ctx) {
    afterCallCount.incrementAndGet();
    boolean isVoid = ctx.isVoid();
    logger.info("LOCAL_AFTER_VOID_CHECK: isVoid={}", isVoid);
    if (isVoid) {
      try {
        ctx.setReturnValue(999);
        logger.info("LOCAL_AFTER_VOID_SET_RETURN: ERROR - did not throw");
      } catch (InterceptApiMisuseException e) {
        logger.info("LOCAL_AFTER_VOID_SET_RETURN: correctly threw InterceptApiMisuseException");
      }
    }
    return new InterceptCallbackResponse();
  }
}
