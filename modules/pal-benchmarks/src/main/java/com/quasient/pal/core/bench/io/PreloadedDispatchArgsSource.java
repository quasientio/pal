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

import com.quasient.pal.core.bench.DispatchBenchmark;
import com.quasient.pal.core.bench.InvocationArgs;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a fixed batch of {@linkplain InvocationArgs messages} once per <b>JMH trial</b> and
 * lets all worker threads pick randomly from that batch.
 *
 * <h4>Behaviour</h4>
 *
 * <ul>
 *   <li>Batch size is {@value #BATCH_SIZE}.
 *   <li>The payload size mix follows {@link DispatchBenchmark#sizeDistribution()}.
 *   <li>The list is {@linkplain Collections#shuffle(List) reshuffled} at the beginning of every
 *       <b>iteration</b> to avoid run‑to‑run correlation.
 *   <li>Each {@link #next()} call chooses an element with {@link ThreadLocalRandom#nextInt(int)} –
 *       hence multiple consumers read concurrently without coordination.
 * </ul>
 *
 * <p>This implementation is selected with {@code -p inputMode=PRELOADED}.
 */
public final class PreloadedDispatchArgsSource implements InvocationArgsSource {

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger("benchmark");

  /** Number of messages created for the batch. */
  private static final int BATCH_SIZE = 10_000;

  /** Back‑reference to the benchmark for helper factories. */
  private final DispatchBenchmark bench;

  /** Immutable list of pre‑generated messages. */
  private final List<InvocationArgs> batch = new ArrayList<>(BATCH_SIZE);

  /**
   * Constructs a new preloaded source.
   *
   * @param bench enclosing benchmark (used to build {@link InvocationArgs})
   */
  public PreloadedDispatchArgsSource(final DispatchBenchmark bench) {
    this.bench = bench;
  }

  /** {@inheritDoc} – builds the batch exactly once. */
  @Override
  public void start() {
    final Random rnd = new Random(bench.hashCode() ^ System.nanoTime());

    while (batch.size() < BATCH_SIZE) {
      try {
        batch.add(bench.randomArgsAccordingToDist(rnd));
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }

    logger.debug("Done preloading buffer");
  }

  /** {@inheritDoc} – reshuffle in place to vary ordering each iteration. */
  @Override
  public void beforeIteration() {
    Collections.shuffle(batch);
  }

  /** {@inheritDoc} */
  @Override
  public InvocationArgs next() {
    int idx = ThreadLocalRandom.current().nextInt(BATCH_SIZE);
    return batch.get(idx);
  }

  /** {@inheritDoc} – clears strong references for GC friendliness. */
  @Override
  public void stop() {
    batch.clear();
  }
}
