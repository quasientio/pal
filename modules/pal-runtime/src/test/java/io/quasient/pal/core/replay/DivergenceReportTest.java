/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@code DivergenceReport} — the immutable summary of divergences detected during
 * deterministic replay.
 *
 * <p>Tests cover thread-context formatting in {@code formatAsText()} for both multi-thread and
 * single-thread divergence scenarios.
 */
public class DivergenceReportTest {

  /**
   * Verifies that {@code formatAsText()} includes thread names when the report contains divergences
   * from multiple threads.
   */
  @Test
  @Ignore("Awaiting implementation in #903")
  public void formatAsText_includesThreadContext() {
    // Given: DivergenceReport with divergences from multiple threads
    //        ('self-caller' and 'rpc-worker-1')
    // When: formatAsText() called
    // Then: Output contains thread names for each divergence entry
    //       (e.g., "[VALUE_MISMATCH] thread=rpc-worker-1 offset=...")

    // TODO(#903): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code formatAsText()} includes thread name even when the report contains
   * divergences from only a single thread.
   */
  @Test
  @Ignore("Awaiting implementation in #903")
  public void formatAsText_singleThread_includesThreadContext() {
    // Given: DivergenceReport with divergences from only one thread
    // When: formatAsText() called
    // Then: Output still includes thread name for each entry

    // TODO(#903): Implement test logic
    fail("Not yet implemented");
  }
}
