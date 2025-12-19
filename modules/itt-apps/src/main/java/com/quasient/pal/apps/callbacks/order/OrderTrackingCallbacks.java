/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.apps.callbacks.order;

import com.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import com.quasient.pal.common.lang.intercept.InterceptContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local callback handlers for testing intercept execution order.
 *
 * <p>This class provides static callback methods that log unique identifiers and sequence numbers,
 * allowing integration tests to verify that callbacks execute in the documented order.
 *
 * <p>Each callback method:
 *
 * <ul>
 *   <li>Increments a sequence counter
 *   <li>Adds its identifier to the invocation order list
 *   <li>Logs in format: {@code ORDER_CALLBACK: id=XXX, seq=N, phase=YYY}
 * </ul>
 *
 * <p>State must be reset between tests using {@link #reset()}.
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
@SuppressFBWarnings(
    value = {"EI_EXPOSE_STATIC_REP2", "MS_EXPOSE_REP"},
    justification = "Test helper class; mutable static state is intentional for test coordination")
public final class OrderTrackingCallbacks {

  /** Logger for callback invocations - tests verify callbacks via log output. */
  private static final Logger logger = LoggerFactory.getLogger(OrderTrackingCallbacks.class);

  /** Thread-safe list recording callback invocation order. */
  private static final CopyOnWriteArrayList<String> invocationOrder = new CopyOnWriteArrayList<>();

  /** Atomic counter for sequence numbering. */
  private static final AtomicInteger sequenceCounter = new AtomicInteger(0);

  private OrderTrackingCallbacks() {
    // Prevent instantiation
  }

  // ==================== Reset ====================

  /** Resets all state. Call this at the start of each test. */
  public static void reset() {
    invocationOrder.clear();
    sequenceCounter.set(0);
    logger.info("ORDER_TRACKING: state reset");
  }

  // ==================== Getters ====================

  /**
   * Returns a copy of the recorded invocation order.
   *
   * @return list of callback identifiers in invocation order
   */
  public static List<String> getInvocationOrder() {
    return new ArrayList<>(invocationOrder);
  }

  /**
   * Returns the current sequence counter value.
   *
   * @return the sequence counter
   */
  public static int getSequenceCounter() {
    return sequenceCounter.get();
  }

  // ==================== LOCAL BEFORE Callbacks ====================

  /**
   * Local BEFORE callback with identifier A.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localBeforeA(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_BEFORE_A";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=BEFORE", id, seq);
    return new InterceptCallbackResponse();
  }

  /**
   * Local BEFORE callback with identifier B.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localBeforeB(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_BEFORE_B";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=BEFORE", id, seq);
    return new InterceptCallbackResponse();
  }

  /**
   * Local BEFORE callback with identifier C.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localBeforeC(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_BEFORE_C";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=BEFORE", id, seq);
    return new InterceptCallbackResponse();
  }

  // ==================== LOCAL AFTER Callbacks ====================

  /**
   * Local AFTER callback with identifier A.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAfterA(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_AFTER_A";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=AFTER", id, seq);
    return new InterceptCallbackResponse();
  }

  /**
   * Local AFTER callback with identifier B.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAfterB(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_AFTER_B";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=AFTER", id, seq);
    return new InterceptCallbackResponse();
  }

  // ==================== LOCAL AROUND Callbacks ====================

  /**
   * Local AROUND callback with identifier A.
   *
   * <p>This callback calls {@code ctx.proceed()} to allow method execution.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAroundA(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_AROUND_A";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=AROUND", id, seq);
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Local AROUND callback with identifier B.
   *
   * <p>This callback calls {@code ctx.proceed()} to allow method execution.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse localAroundB(InterceptContext ctx) {
    int seq = sequenceCounter.incrementAndGet();
    String id = "LOCAL_AROUND_B";
    invocationOrder.add(id);
    logger.info("ORDER_CALLBACK: id={}, seq={}, phase=AROUND", id, seq);
    ctx.proceed();
    return new InterceptCallbackResponse();
  }
}
