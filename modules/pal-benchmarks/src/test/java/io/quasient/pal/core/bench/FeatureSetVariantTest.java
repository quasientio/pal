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
package io.quasient.pal.core.bench;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.core.service.RunOptions;
import java.util.EnumSet;
import org.junit.Test;

/** Tests for {@link FeatureSetVariant} enum and its {@code toRunOptions()} mapping. */
public class FeatureSetVariantTest {

  // ---- baseline variants ----

  /** NOWEAVE produces an empty RunOptions set. */
  @Test
  public void testNoweaveReturnsEmptyRunOptions() {
    EnumSet<RunOptions> opts = FeatureSetVariant.NOWEAVE.toRunOptions();
    assertThat(opts.isEmpty(), is(true));
  }

  /** NOOP produces an empty RunOptions set. */
  @Test
  public void testNoopReturnsEmptyRunOptions() {
    EnumSet<RunOptions> opts = FeatureSetVariant.NOOP.toRunOptions();
    assertThat(opts.isEmpty(), is(true));
  }

  // ---- intercept-only variants (no registered intercepts) ----

  /** INTERCEPTS maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  // ---- intercept callback variants ----

  /** INTERCEPTS_BEFORE maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsBeforeReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_BEFORE.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  /** INTERCEPTS_AFTER maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsAfterReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_AFTER.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  /** INTERCEPTS_AROUND maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsAroundReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_AROUND.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  /** INTERCEPTS_BEFORE_AFTER maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsBeforeAfterReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_BEFORE_AFTER.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  /** INTERCEPTS_ALL maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsAllReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_ALL.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  // ---- async callback variants ----

  /** INTERCEPTS_BEFORE_ASYNC maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsBeforeAsyncReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_BEFORE_ASYNC.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  /** INTERCEPTS_AFTER_ASYNC maps to WITH_INTERCEPTS only. */
  @Test
  public void testInterceptsAfterAsyncReturnsWithIntercepts() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_AFTER_ASYNC.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS)));
  }

  // ---- in-flight tracking variants ----

  /** INTERCEPTS_IN_FLIGHT maps to WITH_INTERCEPTS + WITH_IN_FLIGHT_TRACKING. */
  @Test
  public void testInterceptsInFlightReturnsInterceptsAndTracking() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_IN_FLIGHT.toRunOptions();
    assertThat(
        opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS, RunOptions.WITH_IN_FLIGHT_TRACKING)));
  }

  /** INTERCEPTS_BEFORE_IN_FLIGHT maps to WITH_INTERCEPTS + WITH_IN_FLIGHT_TRACKING. */
  @Test
  public void testInterceptsBeforeInFlightReturnsInterceptsAndTracking() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_BEFORE_IN_FLIGHT.toRunOptions();
    assertThat(
        opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS, RunOptions.WITH_IN_FLIGHT_TRACKING)));
  }

  /** NOOP_IN_FLIGHT maps to WITH_IN_FLIGHT_TRACKING only (no WITH_INTERCEPTS). */
  @Test
  public void testNoopInFlightReturnsTrackingOnly() {
    EnumSet<RunOptions> opts = FeatureSetVariant.NOOP_IN_FLIGHT.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_IN_FLIGHT_TRACKING)));
    assertThat(opts, not(hasItem(RunOptions.WITH_INTERCEPTS)));
  }

  // ---- PUB/WAL infrastructure variants ----

  /** PUB maps to WITH_TCP_PUB only. */
  @Test
  public void testPubReturnsWithTcpPub() {
    EnumSet<RunOptions> opts = FeatureSetVariant.PUB.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_TCP_PUB)));
  }

  /** WAL maps to WITH_WAL only. */
  @Test
  public void testWalReturnsWithWal() {
    EnumSet<RunOptions> opts = FeatureSetVariant.WAL.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_WAL)));
  }

  /** PUB_WAL maps to WITH_TCP_PUB + WITH_WAL. */
  @Test
  public void testPubWalReturnsPubAndWal() {
    EnumSet<RunOptions> opts = FeatureSetVariant.PUB_WAL.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_TCP_PUB, RunOptions.WITH_WAL)));
  }

  /** INTERCEPTS_PUB maps to WITH_INTERCEPTS + WITH_TCP_PUB. */
  @Test
  public void testInterceptsPubReturnsInterceptsAndPub() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_PUB.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS, RunOptions.WITH_TCP_PUB)));
  }

  /** INTERCEPTS_WAL maps to WITH_INTERCEPTS + WITH_WAL. */
  @Test
  public void testInterceptsWalReturnsInterceptsAndWal() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_WAL.toRunOptions();
    assertThat(opts, is(EnumSet.of(RunOptions.WITH_INTERCEPTS, RunOptions.WITH_WAL)));
  }

  /** INTERCEPTS_PUB_WAL maps to WITH_INTERCEPTS + WITH_TCP_PUB + WITH_WAL. */
  @Test
  public void testInterceptsPubWalReturnsAll() {
    EnumSet<RunOptions> opts = FeatureSetVariant.INTERCEPTS_PUB_WAL.toRunOptions();
    assertThat(
        opts,
        is(EnumSet.of(RunOptions.WITH_INTERCEPTS, RunOptions.WITH_TCP_PUB, RunOptions.WITH_WAL)));
  }

  // ---- exhaustiveness check ----

  /** Every FeatureSetVariant value must be handled by toRunOptions() without throwing. */
  @Test
  public void testAllVariantsProduceNonNullRunOptions() {
    for (FeatureSetVariant v : FeatureSetVariant.values()) {
      EnumSet<RunOptions> opts = v.toRunOptions();
      assertThat("toRunOptions() returned null for " + v, opts != null, is(true));
    }
  }

  // ---- no variant leaks unexpected options ----

  /** No intercept callback variant should include WAL or PUB options. */
  @Test
  public void testInterceptCallbackVariantsDoNotIncludePubOrWal() {
    FeatureSetVariant[] callbackVariants = {
      FeatureSetVariant.INTERCEPTS_BEFORE,
      FeatureSetVariant.INTERCEPTS_AFTER,
      FeatureSetVariant.INTERCEPTS_AROUND,
      FeatureSetVariant.INTERCEPTS_BEFORE_AFTER,
      FeatureSetVariant.INTERCEPTS_ALL,
      FeatureSetVariant.INTERCEPTS_BEFORE_ASYNC,
      FeatureSetVariant.INTERCEPTS_AFTER_ASYNC,
    };
    for (FeatureSetVariant v : callbackVariants) {
      EnumSet<RunOptions> opts = v.toRunOptions();
      assertThat(
          v + " should not contain WITH_TCP_PUB", opts, not(hasItem(RunOptions.WITH_TCP_PUB)));
      assertThat(v + " should not contain WITH_WAL", opts, not(hasItem(RunOptions.WITH_WAL)));
    }
  }

  /** In-flight variants must always include WITH_IN_FLIGHT_TRACKING. */
  @Test
  public void testInFlightVariantsAlwaysIncludeTracking() {
    FeatureSetVariant[] inFlightVariants = {
      FeatureSetVariant.INTERCEPTS_IN_FLIGHT,
      FeatureSetVariant.INTERCEPTS_BEFORE_IN_FLIGHT,
      FeatureSetVariant.NOOP_IN_FLIGHT,
    };
    for (FeatureSetVariant v : inFlightVariants) {
      EnumSet<RunOptions> opts = v.toRunOptions();
      assertThat(
          v + " must include WITH_IN_FLIGHT_TRACKING",
          opts,
          hasItem(RunOptions.WITH_IN_FLIGHT_TRACKING));
    }
  }

  /** Non-in-flight variants must not include WITH_IN_FLIGHT_TRACKING. */
  @Test
  public void testNonInFlightVariantsExcludeTracking() {
    FeatureSetVariant[] nonInFlightVariants = {
      FeatureSetVariant.NOWEAVE,
      FeatureSetVariant.NOOP,
      FeatureSetVariant.INTERCEPTS,
      FeatureSetVariant.PUB,
      FeatureSetVariant.WAL,
      FeatureSetVariant.PUB_WAL,
      FeatureSetVariant.INTERCEPTS_PUB,
      FeatureSetVariant.INTERCEPTS_WAL,
      FeatureSetVariant.INTERCEPTS_PUB_WAL,
      FeatureSetVariant.INTERCEPTS_BEFORE,
      FeatureSetVariant.INTERCEPTS_AFTER,
      FeatureSetVariant.INTERCEPTS_AROUND,
      FeatureSetVariant.INTERCEPTS_BEFORE_AFTER,
      FeatureSetVariant.INTERCEPTS_ALL,
      FeatureSetVariant.INTERCEPTS_BEFORE_ASYNC,
      FeatureSetVariant.INTERCEPTS_AFTER_ASYNC,
    };
    for (FeatureSetVariant v : nonInFlightVariants) {
      EnumSet<RunOptions> opts = v.toRunOptions();
      assertThat(
          v + " must not include WITH_IN_FLIGHT_TRACKING",
          opts,
          not(hasItem(RunOptions.WITH_IN_FLIGHT_TRACKING)));
    }
  }
}
