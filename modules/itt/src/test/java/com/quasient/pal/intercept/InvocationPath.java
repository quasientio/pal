/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

/**
 * Defines the two paths through which method/constructor/field operations can be intercepted.
 *
 * <p>This enum is used to parameterize intercept tests so they run through both code paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Operations triggered via wrapper methods (e.g., callEcho() calls echo()).
 *       The intercept fires at the AspectJ call-site inside the wrapper method, handled by {@code
 *       BaseExecMessageDispatcher.dispatch()}.
 *   <li><b>INCOMING_RPC</b>: Operations invoked directly via RPC (e.g., calling echo() directly).
 *       The intercept fires in {@code BaseExecMessageDispatcher.dispatchIncoming()}.
 * </ul>
 *
 * <p><b>Why both paths matter:</b> The hot-path uses AspectJ's {@code ProceedingJoinPoint} for
 * interception, while the incoming RPC path extracts intercept information from {@code
 * ExecMessage}. Both paths must behave identically for intercept callbacks (BEFORE, AFTER, AROUND).
 * <p>
 * See {@code BaseExecMessageDispatcher#dispatch} and {@code BaseExecMessageDispatcher#dispatchIncoming}
 */
public enum InvocationPath {

  /**
   * Invocation through a wrapper method (call-site weaving).
   *
   * <p>Example: {@code callEcho("hello")} internally calls {@code echo("hello")}. The intercept on
   * {@code echo()} fires at the call-site inside {@code callEcho()}.
   */
  HOT_PATH("wrapper"),

  /**
   * Direct invocation via RPC (dispatchIncoming).
   *
   * <p>Example: Directly invoke {@code echo("hello")} via RPC. The intercept on {@code echo()}
   * fires within {@code dispatchIncoming()}.
   */
  INCOMING_RPC("direct");

  /** Short description for test naming. */
  private final String description;

  /**
   * Constructs an InvocationPath with the given description.
   *
   * @param description short description for test naming
   */
  InvocationPath(String description) {
    this.description = description;
  }

  /**
   * Returns a short description suitable for test naming.
   *
   * @return the description (e.g., "wrapper" or "direct")
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return name();
  }
}
