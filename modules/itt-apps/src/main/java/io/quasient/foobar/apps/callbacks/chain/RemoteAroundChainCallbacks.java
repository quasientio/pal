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
package io.quasient.foobar.apps.callbacks.chain;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote callback handlers for testing AROUND intercept chain behavior.
 *
 * <p>These callbacks run on the interceptor peer (remote from the interceptable peer). They modify
 * return values to verify that the AROUND chain properly propagates return value changes outward.
 *
 * <p>In the onion model, remote AROUND intercepts are inner layers relative to local AROUND
 * intercepts:
 *
 * <pre>
 * Local AROUND (outer) → Remote AROUND (inner) → Method
 * </pre>
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
public final class RemoteAroundChainCallbacks {

  /** Logger for callback invocations. */
  private static final Logger logger = LoggerFactory.getLogger(RemoteAroundChainCallbacks.class);

  /** Invocation counter for innerAdder callback. */
  private static final AtomicInteger innerAdderCount = new AtomicInteger(0);

  /** Invocation counter for innerLogger callback. */
  private static final AtomicInteger innerLoggerCount = new AtomicInteger(0);

  /** Invocation counter for innerExceptionThrower callback. */
  private static final AtomicInteger innerExceptionThrowerCount = new AtomicInteger(0);

  private RemoteAroundChainCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    innerAdderCount.set(0);
    innerLoggerCount.set(0);
    innerExceptionThrowerCount.set(0);
    logger.info("REMOTE_AROUND_CHAIN_CALLBACKS: state reset");
  }

  // ==================== Getters ====================

  /**
   * Returns the invocation count for innerAdder.
   *
   * @return the count
   */
  public static int getInnerAdderCount() {
    return innerAdderCount.get();
  }

  /**
   * Returns the invocation count for innerLogger.
   *
   * @return the count
   */
  public static int getInnerLoggerCount() {
    return innerLoggerCount.get();
  }

  /**
   * Returns the invocation count for innerExceptionThrower.
   *
   * @return the count
   */
  public static int getInnerExceptionThrowerCount() {
    return innerExceptionThrowerCount.get();
  }

  // ==================== AROUND Callbacks ====================

  /**
   * Remote AROUND callback that adds 10 to the return value.
   *
   * <p>This callback:
   *
   * <ol>
   *   <li>Logs entry with sequence number
   *   <li>Calls proceed() to invoke the method (or next remote layer)
   *   <li>Adds 10 to the return value (if integer)
   *   <li>Logs exit with original and modified values
   * </ol>
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse innerAdder(InterceptContext ctx) {
    int seq = innerAdderCount.incrementAndGet();
    logger.info("AROUND_CHAIN: innerAdder BEFORE (remote), seq={}", seq);

    // Proceed to method execution
    ctx.proceed();

    // Add 10 to return value
    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int added = original + 10;
        ctx.setReturnValue(added);
        logger.info(
            "AROUND_CHAIN: innerAdder AFTER (remote), seq={}, {} + 10 = {}", seq, original, added);
      } else {
        logger.info(
            "AROUND_CHAIN: innerAdder AFTER (remote), seq={}, return not Integer: {}",
            seq,
            returnValue);
      }
    } else {
      logger.info("AROUND_CHAIN: innerAdder AFTER (remote), seq={}, void method", seq);
    }

    return new InterceptCallbackResponse();
  }

  /**
   * Remote AROUND callback that logs but doesn't modify.
   *
   * <p>Used to verify that remote AROUND is invoked in the chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse innerLogger(InterceptContext ctx) {
    int seq = innerLoggerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: innerLogger BEFORE (remote), seq={}", seq);

    ctx.proceed();

    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      logger.info("AROUND_CHAIN: innerLogger AFTER (remote), seq={}, return={}", seq, returnValue);
    } else {
      logger.info("AROUND_CHAIN: innerLogger AFTER (remote), seq={}, void method", seq);
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Exception Handling Callbacks ====================

  /**
   * Remote AROUND callback that throws an exception BEFORE proceeding.
   *
   * <p>Tests exception propagation from inner remote layer to outer local layers.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse innerExceptionThrowerBefore(InterceptContext ctx) {
    int seq = innerExceptionThrowerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: innerExceptionThrowerBefore THROWING (remote), seq={}", seq);

    ctx.setExceptionToThrow(
        new RuntimeException(
            "Exception from remote innerExceptionThrowerBefore (seq=" + seq + ")"));
    return new InterceptCallbackResponse();
  }

  /**
   * Remote AROUND callback that throws an exception AFTER proceeding.
   *
   * <p>Tests exception propagation from inner remote layer after method execution.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse innerExceptionThrowerAfter(InterceptContext ctx) {
    int seq = innerExceptionThrowerCount.incrementAndGet();
    logger.info("AROUND_CHAIN: innerExceptionThrowerAfter BEFORE (remote), seq={}", seq);

    ctx.proceed();

    Object returnValue = ctx.isVoid() ? "void" : ctx.getReturnValue();
    logger.info(
        "AROUND_CHAIN: innerExceptionThrowerAfter AFTER (remote), seq={}, got return={}, NOW THROWING",
        seq,
        returnValue);

    ctx.setExceptionToThrow(
        new RuntimeException("Exception from remote innerExceptionThrowerAfter (seq=" + seq + ")"));
    return new InterceptCallbackResponse();
  }

  // ==================== Chain Order Verification Callbacks ====================

  /**
   * Remote AROUND callback "B" for chain order verification.
   *
   * <p>Logs "REMOTE_AROUND_B_BEFORE" and "REMOTE_AROUND_B_AFTER" to verify execution order.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAroundB(InterceptContext ctx) {
    logger.info("AROUND_CHAIN_ORDER: REMOTE_AROUND_B_BEFORE");

    ctx.proceed();

    logger.info("AROUND_CHAIN_ORDER: REMOTE_AROUND_B_AFTER");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote AROUND callback "D" for chain order verification.
   *
   * <p>Logs "REMOTE_AROUND_D_BEFORE" and "REMOTE_AROUND_D_AFTER" to verify execution order.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAroundD(InterceptContext ctx) {
    logger.info("AROUND_CHAIN_ORDER: REMOTE_AROUND_D_BEFORE");

    ctx.proceed();

    logger.info("AROUND_CHAIN_ORDER: REMOTE_AROUND_D_AFTER");
    return new InterceptCallbackResponse();
  }

  // ==================== Arg Mutation Callbacks ====================

  /**
   * Remote AROUND callback that mutates the first argument to 20.
   *
   * <p>Used for testing argument mutation propagation through the chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse mutateFirstArgTo20(InterceptContext ctx) {
    Object[] args = ctx.getArgs();
    Object originalArg = args.length > 0 ? args[0] : null;
    logger.info(
        "AROUND_CHAIN: mutateFirstArgTo20 BEFORE (remote), original arg[0]={}", originalArg);

    // Mutate first arg to 20
    ctx.setArg(0, 20);

    ctx.proceed();

    logger.info(
        "AROUND_CHAIN: mutateFirstArgTo20 AFTER (remote), return={}",
        ctx.isVoid() ? "void" : ctx.getReturnValue());
    return new InterceptCallbackResponse();
  }

  // ==================== Return Value Modification Callbacks ====================

  /**
   * Remote AROUND callback that multiplies the return value by 2.
   *
   * <p>Used for testing return value modification propagation through the chain.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse multiplyReturnBy2(InterceptContext ctx) {
    logger.info("AROUND_CHAIN: multiplyReturnBy2 BEFORE (remote)");

    ctx.proceed();

    if (!ctx.isVoid()) {
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer) {
        int original = (Integer) returnValue;
        int modified = original * 2;
        ctx.setReturnValue(modified);
        logger.info(
            "AROUND_CHAIN: multiplyReturnBy2 AFTER (remote), {} * 2 = {}", original, modified);
      }
    }

    return new InterceptCallbackResponse();
  }

  // ==================== Skip Callbacks ====================

  /**
   * Remote AROUND callback that skips execution with a cached value.
   *
   * <p>Always skips proceed() and returns a hardcoded value (888). Used for testing that skipping
   * in the middle of the chain (at a remote layer) prevents inner layers from executing.
   *
   * @param ctx the intercept context
   * @return the intercept response with skip
   */
  public static InterceptCallbackResponse remoteAlwaysSkip(InterceptContext ctx) {
    logger.info("AROUND_CHAIN: remoteAlwaysSkip SKIPPING (remote)");

    // Skip execution and return cached value
    ctx.setReturnValue(888);
    return InterceptCallbackResponse.skipProceed();
  }

  // ==================== BEFORE/AFTER Callbacks for Combined Chain Testing ====================

  /**
   * Remote BEFORE callback that logs invocation.
   *
   * <p>Used for testing mixed local/remote BEFORE + AROUND + AFTER combinations.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteBeforeLogger(InterceptContext ctx) {
    logger.info("BEFORE_AFTER_CHAIN: remoteBeforeLogger executed");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote AFTER callback that logs the return value.
   *
   * <p>Used for testing mixed local/remote BEFORE + AROUND + AFTER combinations.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAfterLogger(InterceptContext ctx) {
    Object returnValue = ctx.isVoid() ? "void" : ctx.getReturnValue();
    logger.info("BEFORE_AFTER_CHAIN: remoteAfterLogger received return={}", returnValue);
    return new InterceptCallbackResponse();
  }
}
