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

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Schedules “open-loop” arrivals:  ↙ baseline  ↔ burst ↘ baseline …
 */
@State(Scope.Benchmark)
public class BurstPlan {

  // ---------- tunables exposed on the CLI (-p) ----------

  /**
   * baseline QPS - λ₁
   */
  @Param({"50"})
  public int baseQps;

  /**
   * burst QPS - λ₂
   */
  @Param({"500"})
  public int burstQps;

  /**
   * seconds in baseline
   */
  @Param({"45"})
  public long baseSec;

  /**
   * seconds in burst
   */
  @Param({"15"})
  public long burstSec;

  /**
   * reproducible runs
   */
  @Param({"42"})
  public long rndSeed;
  // ------------------------------------------------------

  /* --- per-benchmark state --- */

  /**
   * flag to flip between baseline and burst phase
   */
  boolean inBurst;

  /**
   * nano time when we flip λ
   */
  long phaseEndsAt;

  /**
   * nano time for the next call
   */
  long nextArrivalAt;

  /**
   * allows random inter-arrival times
   */
  Random rnd;

  /**
   * Sets up the baseline <--> burst inter-arrival plan.
   *
   * @param bench instance of {@link DispatchBenchmark} to acquire
   *              handles of existing objects
   */
  @SuppressWarnings("unused")
  @Setup(Level.Iteration)
  public void init(DispatchBenchmark bench) {
    rnd = new Random(rndSeed);
    inBurst = false;
    long now = System.nanoTime();
    phaseEndsAt = now + TimeUnit.SECONDS.toNanos(baseSec);
    nextArrivalAt = now;                  // hit immediately
  }

  /**
   * Exponential inter-arrival time in nanoseconds.
   */
  long pickIntervalNs() {
    double lambda = inBurst ? burstQps : baseQps;
    double u = 1.0d - rnd.nextDouble();   // avoid ln(0)
    return (long) (-Math.log(u) * 1_000_000_000d / lambda);
  }

  /**
   * Flip baseline ↔ burst when the current phase expires.
   */
  void maybeFlipPhase(long now) {
    if (now >= phaseEndsAt) {
      inBurst = !inBurst;
      long durNs = TimeUnit.SECONDS.toNanos(inBurst ? burstSec : baseSec);
      phaseEndsAt = now + durNs;
    }
  }
}
