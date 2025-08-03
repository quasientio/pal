/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

import com.quasient.pal.core.bench.io.*;
import org.openjdk.jmh.annotations.*;

/**
 * JMH {@code @State(Thread)} that delegates to the selected
 * {@link InvocationArgsSource}.
 */
@State(Scope.Thread)
public class InputThreadState {

  /** Instance of {@link InvocationArgsSource} that we'll fetch from the bench */
  private InvocationArgsSource source;

  /**
   * Sets up the specific source for the selected {@link InputMode}
   * @param bench the enclosing benchmark
   */
  @Setup(Level.Trial)
  public void trialSetup(DispatchBenchmark bench) {
    source = switch (bench.inputMode) {
      case ASYNC     -> new AsyncDispatchArgsSource(bench);
      case PRELOADED -> new PreloadedDispatchArgsSource(bench);
    };
    source.start();
  }

  /**
   * Iteration-level set up
   */
  @Setup(Level.Iteration)
  public void iterationSetup() { source.beforeIteration(); }

  /**
   * Trial-level tear down
   * @throws InterruptedException if thread is interrupted
   */
  @TearDown(Level.Trial)
  public void trialTearDown() throws InterruptedException {
    source.stop();
  }

  /** @return next {@link InvocationArgs} or {@code null} */
  public InvocationArgs take() {
    return source.next();
  }
}

