/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.bench;

import io.quasient.pal.core.service.RunOptions;
import java.util.EnumSet;

/** Which combination of subsystems the gateway should activate. */
public enum FeatureSetVariant {
  /** Call to non-woven method. */
  NOWEAVE,

  /** No Intercepts, no WAL, no TCP-PUB. */
  NOOP,

  /** Intercepts only. */
  INTERCEPTS,

  /** PUB only. */
  PUB,

  /** WAL only. */
  WAL,

  /** PUB + WAL. */
  PUB_WAL,

  /** Intercepts + TCP-PUB. */
  INTERCEPTS_PUB,

  /** Intercepts + WAL. */
  INTERCEPTS_WAL,

  /** Intercepts + TCP-PUB + WAL (full hot path). */
  INTERCEPTS_PUB_WAL,

  /**
   * Intercepts with a local BEFORE callback registered.
   *
   * <p>Measures the overhead of synchronous BEFORE intercept dispatch: pattern matching, callback
   * resolution, {@code InterceptContext} creation, and reflective callback invocation. The callback
   * itself is a no-op so that only dispatch infrastructure cost is measured.
   */
  INTERCEPTS_BEFORE,

  /**
   * Intercepts with a local AFTER callback registered.
   *
   * <p>Measures the overhead of synchronous AFTER intercept dispatch: pattern matching, callback
   * resolution, {@code InterceptContext} creation with return value, and reflective callback
   * invocation. The callback itself is a no-op so that only dispatch infrastructure cost is
   * measured.
   */
  INTERCEPTS_AFTER,

  /**
   * Intercepts with a local AROUND callback registered.
   *
   * <p>Measures the overhead of AROUND intercept dispatch: pattern matching, {@link
   * io.quasient.pal.core.intercept.AroundInterceptChainBuilder} chain construction, and onion-model
   * execution via {@code AroundInterceptChain}. The callback calls {@code proceed()} immediately so
   * that only chain infrastructure cost is measured.
   */
  INTERCEPTS_AROUND,

  /**
   * Intercepts with both BEFORE and AFTER callbacks registered.
   *
   * <p>Measures the combined overhead of synchronous BEFORE + AFTER intercept dispatch on a single
   * invocation. Both callbacks are no-ops. This variant is useful for understanding the incremental
   * cost of adding a second intercept phase compared to {@link #INTERCEPTS_BEFORE} or {@link
   * #INTERCEPTS_AFTER} alone.
   */
  INTERCEPTS_BEFORE_AFTER,

  /**
   * Intercepts with BEFORE, AFTER, and AROUND callbacks all registered.
   *
   * <p>Measures the full synchronous intercept pipeline: BEFORE dispatch, AROUND chain construction
   * and execution, and AFTER dispatch. All callbacks are no-ops. This is the worst-case overhead
   * for a fully-intercepted method invocation.
   */
  INTERCEPTS_ALL,

  /**
   * Intercepts enabled with in-flight dispatch tracking, but no intercepts registered.
   *
   * <p>Measures the overhead of {@code InFlightDispatchTracker} enter/exit bookkeeping on every
   * dispatch, without any intercept matching or callback dispatch. Compare against {@link
   * #INTERCEPTS} (which also has no registered intercepts but no tracking) to isolate the cost of
   * the {@code LongAdder}/{@code ConcurrentHashMap.computeIfAbsent()} tracking mechanism.
   */
  INTERCEPTS_IN_FLIGHT,

  /**
   * Intercepts with in-flight tracking and a local BEFORE callback registered.
   *
   * <p>Measures the combined overhead of in-flight dispatch tracking plus synchronous BEFORE
   * intercept dispatch. Compare against {@link #INTERCEPTS_BEFORE} (no tracking) and {@link
   * #INTERCEPTS_IN_FLIGHT} (no registered intercepts) to isolate the interaction cost.
   */
  INTERCEPTS_BEFORE_IN_FLIGHT,

  /**
   * No intercepts, but with in-flight dispatch tracking enabled.
   *
   * <p>Isolates the pure overhead of {@code InFlightDispatchTracker} bookkeeping without any
   * intercept infrastructure. Compare against {@link #NOOP} to measure the raw cost of tracking
   * enter/exit on every dispatch.
   */
  NOOP_IN_FLIGHT,

  /**
   * Intercepts with BEFORE_ASYNC callback registered.
   *
   * <p>Exercises the async callback dispatch path for BEFORE_ASYNC intercepts. Uses the executor
   * selected by {@code VirtualThreadCallbackExecutor} (virtual threads on Java 21+, cached pool on
   * Java 17).
   */
  INTERCEPTS_BEFORE_ASYNC,

  /**
   * Intercepts with AFTER_ASYNC callback registered.
   *
   * <p>Exercises the async callback dispatch path for AFTER_ASYNC intercepts. Uses the executor
   * selected by {@code VirtualThreadCallbackExecutor} (virtual threads on Java 21+, cached pool on
   * Java 17).
   */
  INTERCEPTS_AFTER_ASYNC;

  /**
   * Return the {@link RunOptions} equivalent to this variant.
   *
   * @return enum set of run options.
   */
  public EnumSet<RunOptions> toRunOptions() {
    final EnumSet<RunOptions> runOpts = EnumSet.noneOf(RunOptions.class);
    switch (this) {
      case INTERCEPTS,
              INTERCEPTS_BEFORE,
              INTERCEPTS_AFTER,
              INTERCEPTS_AROUND,
              INTERCEPTS_BEFORE_AFTER,
              INTERCEPTS_ALL,
              INTERCEPTS_BEFORE_ASYNC,
              INTERCEPTS_AFTER_ASYNC ->
          runOpts.add(RunOptions.WITH_INTERCEPTS);
      case INTERCEPTS_IN_FLIGHT, INTERCEPTS_BEFORE_IN_FLIGHT -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_IN_FLIGHT_TRACKING);
      }
      case NOOP_IN_FLIGHT -> runOpts.add(RunOptions.WITH_IN_FLIGHT_TRACKING);
      case PUB -> runOpts.add(RunOptions.WITH_TCP_PUB);
      case WAL -> runOpts.add(RunOptions.WITH_WAL);
      case PUB_WAL -> {
        runOpts.add(RunOptions.WITH_TCP_PUB);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
      }
      case INTERCEPTS_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case INTERCEPTS_PUB_WAL -> {
        runOpts.add(RunOptions.WITH_INTERCEPTS);
        runOpts.add(RunOptions.WITH_TCP_PUB);
        runOpts.add(RunOptions.WITH_WAL);
      }
      case NOOP, NOWEAVE -> {}
    }
    return runOpts;
  }
}
