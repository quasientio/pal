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
package io.quasient.pal.core.bench.io;

import io.quasient.pal.core.bench.InvocationArgs;

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
