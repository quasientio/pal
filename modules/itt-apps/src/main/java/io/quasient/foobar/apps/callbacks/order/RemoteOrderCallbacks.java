/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.foobar.apps.callbacks.order;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Remote callback handlers for testing intercept execution order.
 *
 * <p>These callbacks run on the interceptor peer (remote from the interceptable peer). They log
 * unique identifiers that can be parsed from the log to verify execution order.
 *
 * <p>Each callback logs in format: {@code ORDER_CALLBACK: id=XXX, phase=YYY}
 *
 * <p>Note: These callbacks cannot share static state with local callbacks since they run on
 * different peers. Order verification is done via log parsing.
 */
@SuppressWarnings("unused") // Callbacks invoked via reflection
public final class RemoteOrderCallbacks {

  /** Logger for callback invocations - tests verify callbacks via log output. */
  private static final Logger logger = LoggerFactory.getLogger(RemoteOrderCallbacks.class);

  private RemoteOrderCallbacks() {
    // Prevent instantiation
  }

  // ==================== REMOTE BEFORE Callbacks ====================

  /**
   * Remote BEFORE callback with identifier A.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteBeforeA(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_BEFORE_A, phase=BEFORE");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote BEFORE callback with identifier B.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteBeforeB(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_BEFORE_B, phase=BEFORE");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote BEFORE callback with identifier C.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteBeforeC(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_BEFORE_C, phase=BEFORE");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote BEFORE callback with identifier D.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteBeforeD(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_BEFORE_D, phase=BEFORE");
    return new InterceptCallbackResponse();
  }

  // ==================== REMOTE AFTER Callbacks ====================

  /**
   * Remote AFTER callback with identifier A.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAfterA(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_AFTER_A, phase=AFTER");
    return new InterceptCallbackResponse();
  }

  /**
   * Remote AFTER callback with identifier B.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAfterB(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_AFTER_B, phase=AFTER");
    return new InterceptCallbackResponse();
  }

  // ==================== REMOTE AROUND Callbacks ====================

  /**
   * Remote AROUND callback with identifier A.
   *
   * <p>This callback calls {@code ctx.proceed()} to allow method execution.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAroundA(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_AROUND_A, phase=AROUND");
    ctx.proceed();
    return new InterceptCallbackResponse();
  }

  /**
   * Remote AROUND callback with identifier B.
   *
   * <p>This callback calls {@code ctx.proceed()} to allow method execution.
   *
   * @param ctx the intercept context
   * @return the intercept response
   */
  public static InterceptCallbackResponse remoteAroundB(InterceptContext ctx) {
    logger.info("ORDER_CALLBACK: id=REMOTE_AROUND_B, phase=AROUND");
    ctx.proceed();
    return new InterceptCallbackResponse();
  }
}
