/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench.io;

import com.quasient.pal.core.bench.InvocationArgs;

/**
 * Supplies {@link InvocationArgs} to producer threads in a benchmark run.
 *
 * <p>A source is:
 *
 * <ul>
 *   <li>created once per JMH <b>trial</b> (<code>@Setup(Level.Trial)</code>)
 *   <li>optionally reshuffled once per <b>iteration</b>
 *   <li>closed once per <b>trial</b>
 * </ul>
 *
 * <p>All implementations **must be thread‑safe**: multiple JMH worker threads call {@link #next()}
 * concurrently.
 */
public interface InvocationArgsSource {

  /** Prepare the source for a new trial. */
  void start();

  /**
   * Optionally reset / reshuffle internal state at the beginning of an iteration. Default
   * implementation is a no‑op.
   */
  default void beforeIteration() {}

  /**
   * Gets the next args.
   *
   * @return the next {@link InvocationArgs} instance
   */
  InvocationArgs next();

  /** Release resources at the end of a trial. */
  void stop() throws InterruptedException;
}
