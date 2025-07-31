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
import com.quasient.pal.core.bench.DispatchArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * <p>“On‑the‑fly” generator that fills a queue with {@link DispatchArgs}:</p>
 *
 * <ul>
 *   <li>A <b>single daemon producer thread</b> keeps a ring‑buffer topped‑up with
 *       freshly generated {@link DispatchArgs}.</li>
 *   <li>All JMH worker threads call {@link #next()} concurrently; the underlying
 *       queue is a lock‑free {@link ConcurrentLinkedQueue}.</li>
 *   <li>Payload size distribution is driven by {@link DispatchBenchmark#sizeDistribution()}.</li>
 * </ul>
 *
 * <p>This implementation is chosen with {@code -p inputMode=ASYNC}.</p>
 */
public final class AsyncDispatchArgsSource implements DispatchArgsSource {

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger("benchmark");

  /** Maximum number of pre‑generated messages kept in the buffer. */
  private static final int BUFSIZE = 2_048;

  /** Low‑water mark (<i>1/4 × BUFSIZE</i>)— triggers a refill. */
  private static final int REFILL_THRESHOLD = BUFSIZE / 4;

  /** Thread‑safe queue shared by producer and all consumers. */
  private final ConcurrentLinkedQueue<DispatchArgs> buf = new ConcurrentLinkedQueue<>();

  /** Stops the producer loop when {@code true}. */
  private volatile boolean stop;

  /** Producer thread instance. */
  private Thread producer;

  /** Reference to the benchmark for helper factories. */
  private final DispatchBenchmark bench;

  /**
   * Creates a new async source bound to the given benchmark instance.
   *
   * @param bench enclosing benchmark (for helper methods &amp; distribution)
   */
  public AsyncDispatchArgsSource(final DispatchBenchmark bench) {
    this.bench = bench;
  }

  /** {@inheritDoc} */
  @Override
  public void start() {
    // Synchronously pre‑fill before measurements start
    refillSynchronously();
    logger.debug("Initial buffer fill complete");

    producer = new Thread(this::runProducer, "args‑producer");
    producer.setDaemon(true); // benchmark can exit if something goes wrong
    producer.start();
  }

  /** {@inheritDoc} (no iteration‑level work required here) */
  @Override
  public void beforeIteration() { /* no‑op */ }

  /** {@inheritDoc} */
  @Override
  public DispatchArgs next() {
    return buf.poll();       // may return null for the consumer to spin on
  }

  /** {@inheritDoc} */
  @Override
  public void stop() throws InterruptedException {
    stop = true;
    producer.join();
    buf.clear();
  }

  /* ---------------------------------------------------------------------- */
  /* Internal helpers                                                       */
  /* ---------------------------------------------------------------------- */

  /** Body of the producer thread – keeps the queue above the threshold. */
  private void runProducer() {
    final Random rnd = new Random(System.nanoTime() ^ producer.getId());

    while (!stop) {
      if (buf.size() < REFILL_THRESHOLD) {
        refill(rnd);
      } else {
        Thread.onSpinWait();
      }
    }
  }

  /** Fills the queue up to {@value #BUFSIZE} elements. */
  private void refill(final Random rnd) {
    while (buf.size() < BUFSIZE) {
      try {
        buf.offer(bench.randomArgsAccordingToDist(rnd));
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /** Initial synchronous fill so the first benchmark iteration hits no nulls. */
  private void refillSynchronously() {
    final Random rnd = new Random(System.nanoTime());
    refill(rnd);
  }
}