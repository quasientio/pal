/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.replay;

import static org.junit.Assert.fail;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration tests for {@code STUB_FROM_WAL} replay with shield-IO and stub patterns.
 *
 * <p>Validates that replay with {@code --shield-io} correctly stubs non-deterministic operations
 * (e.g., {@code System.currentTimeMillis()}, {@code Math.random()}) using WAL-recorded values, and
 * that {@code --stub} patterns selectively stub matching operations while re-executing non-matching
 * ones.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class StubFromWalReplayIT extends AbstractCliIT {

  /** The number of threads for this parameterized test run. */
  @SuppressWarnings("UnusedVariable")
  private final int threadCount;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public StubFromWalReplayIT(int threadCount) {
    this.threadCount = threadCount;
  }

  /**
   * Returns the parameterized thread counts.
   *
   * @return collection of parameters: 1-thread and 2-thread scenarios
   */
  @Parameters(name = "threads={0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {1}, new Object[] {2});
  }

  /**
   * Verifies that replaying with {@code --shield-io} produces zero divergences by stubbing
   * non-deterministic I/O operations from the WAL.
   */
  @Test
  @Ignore("Awaiting implementation in #959")
  public void replayWithShieldIoProducesZeroDivergences() {
    // Given: A recorded WAL from an app that calls System.currentTimeMillis() and Math.random()
    //        (non-deterministic operations that would normally cause divergences on replay)
    // When: Replay with --shield-io flag enabled
    // Then: Exit code 0; zero divergences reported; stubbed values from WAL used instead of
    //        live non-deterministic calls

    // TODO(#959): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code --stub} with an Ant-style pattern stubs matching operations while
   * re-executing non-matching ones.
   */
  @Test
  @Ignore("Awaiting implementation in #959")
  public void replayWithStubPatternStubsMatchingOperations() {
    // Given: A recorded WAL from a deterministic app with operations across multiple classes
    // When: Replay with --stub com.example.Foo.** (Ant-style pattern matching specific class)
    // Then: Operations matching the pattern are stubbed from WAL; non-matching operations are
    //        re-executed normally; zero divergences reported

    // TODO(#959): Implement test logic
    fail("Not yet implemented");
  }
}
