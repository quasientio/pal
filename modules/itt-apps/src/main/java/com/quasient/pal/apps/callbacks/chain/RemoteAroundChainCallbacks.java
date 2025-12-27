/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.chain;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
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

  private RemoteAroundChainCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    innerAdderCount.set(0);
    innerLoggerCount.set(0);
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
}
