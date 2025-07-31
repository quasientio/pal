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

import com.quasient.pal.core.bench.DispatchArgs;

/**
 * Supplies {@link DispatchArgs} to producer threads in a benchmark run.
 *
 * <p>A source is:
 * <ul>
 *   <li>created once per JMH <b>trial</b> (<code>@Setup(Level.Trial)</code>)</li>
 *   <li>optionally reshuffled once per <b>iteration</b></li>
 *   <li>closed once per <b>trial</b></li>
 * </ul>
 *
 * <p>All implementations **must be thread‑safe**: multiple JMH worker threads call
 * {@link #next()} concurrently.
 */
public interface DispatchArgsSource {

  /** Prepare the source for a new trial. */
  void start();

  /**
   * Optionally reset / reshuffle internal state at the beginning of an iteration.
   * Default implementation is a no‑op.
   */
  default void beforeIteration() { }

  /**
   * @return the next {@link DispatchArgs} instance
   */
  DispatchArgs next();

  /** Release resources at the end of a trial. */
  void stop() throws InterruptedException;
}

