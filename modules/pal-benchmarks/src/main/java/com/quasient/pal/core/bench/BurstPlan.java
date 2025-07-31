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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * Schedules “open-loop” arrivals:  ↙ baseline  ↔ burst ↘ baseline …
 */
@State(Scope.Benchmark)
public class BurstPlan {

  /** Enumerates the distinct phases */
  enum Phase { BASE, RAMP, BURST }

  /** Logger instance for this class. */
  private static final Logger logger = LoggerFactory.getLogger("benchmark");

  /** save last phase for logging */
  private Phase lastPhase;

  // ---------- tunables exposed on the CLI (-p) ----------

  /**
   * baseline QPS - λ₁
   */
  @Param({"1000"})
  public int baseQps;

  /**
   * burst QPS - λ₂
   */
  @Param({"10000"})  public int    burstQps;

  /**
   * seconds in baseline
   */
  @Param({"30"})     public double baseSec;

  /**
   * seconds in steady-state burst
   */
  @Param({"10"})     public double burstSec;

  /**
   * seconds of linear ramp-up
   */
  @Param({"3"})      public double rampSec;

  /**
   * for reproducible runs
   */
  @Param({"42"})     public long   rndSeed;

  /* ---------- global timing anchor (shared by all iterations) ---------- */

  /** Timestamp when we start */
  private static final long RUN_START_NS = System.nanoTime();

  /** base duration cached in ns for speed */
  private long baseNs;

  /** ramp duration cached in ns for speed */
  private long rampNs;

  /** full duration cycle cached in ns for speed */
  private long cycleNs;

  /* ---------- per-iteration objects ---------- */

  /**
   * allows random inter-arrival times
   */
  private Random rnd;


  /**
   * nano time for the next call
   */
  long           nextArrivalAt;

  /* ---------- JMH callbacks ---------- */

  /**
   * Sets up the baseline <--> burst inter-arrival plan.
   */
  @Setup(Level.Iteration)          // keep Iteration scope
  public void init() {
    // convert only once per fork
    if (cycleNs == 0) {
      baseNs  = secsToNs(baseSec);
      rampNs  = secsToNs(rampSec);
      long burstNs = secsToNs(burstSec);
      cycleNs = baseNs + rampNs + burstNs;
    }
    rnd = new Random(rndSeed);
    nextArrivalAt = System.nanoTime();
  }

  /* ---------- helpers ---------- */

  /** Convert seconds (double) to nanoseconds (long) */
  private static long secsToNs(double sec) {
    return (long) (sec * 1_000_000_000L + 0.5);
  }

  /** Return the arrival interval (ns) drawn from an exponential distribution. */
  long pickIntervalNs() {
    double nowOffset = (System.nanoTime() - RUN_START_NS) % cycleNs;
    double lambda = currentLambda(nowOffset);
    double u = 1.0 - rnd.nextDouble();          // avoid ln(0)
    return (long) (-Math.log(u) * 1_000_000_000d / lambda);
  }

  /** Piece-wise λ(t): baseline → linear ramp-up → flat burst. */
  private double currentLambda(double offset) {

    Phase newPhase;
    if (offset < baseNs) {
      newPhase = Phase.BASE;
    } else if (offset < baseNs + rampNs) {
      newPhase = Phase.RAMP;
    } else {
      newPhase = Phase.BURST;
    }

    // log the switch the first time we enter a new phase
    if (newPhase != lastPhase) {
      logger.debug("Switching phase: {} → {}", lastPhase, newPhase);
      lastPhase = newPhase;
    }

    // λ(t) for the current phase
    return switch (newPhase) {
      case BASE  -> baseQps;
      case RAMP  -> {
        double frac = (offset - baseNs) / (double) rampNs;   // 0 … 1
        yield baseQps + frac * (burstQps - baseQps);
      }
      case BURST -> burstQps;
    };
  }
}

