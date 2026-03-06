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
 * Integration tests for unsafe stub detection and the {@code --force-stub} override.
 *
 * <p>Validates that the {@link io.quasient.pal.core.replay.SideEffectAnalyzer} detects unsafe stubs
 * (operations stubbed with {@code STUB_FROM_WAL} whose spans contain field mutations on
 * externally-referenced objects) and that the system fails fast with a warning unless {@code
 * --force-stub} is specified.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class UnsafeStubWarningReplayIT extends AbstractCliIT {

  /** The number of threads for this parameterized test run. */
  @SuppressWarnings("UnusedVariable")
  private final int threadCount;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public UnsafeStubWarningReplayIT(int threadCount) {
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
   * Verifies that replaying with a plain {@code STUB_FROM_WAL} for a mutating method exits with a
   * non-zero code and emits a warning about unsafe stub usage.
   */
  @Test
  @Ignore("Awaiting implementation in #959")
  public void unsafeStubWithoutForceExitsWithError() {
    // Given: A recorded WAL from an app with a mutating method (sets field on passed-in object)
    // When: Replay with plain STUB_FROM_WAL for the mutating method (without --force-stub)
    // Then: Non-zero exit code; warning message in output about unsafe stub indicating the
    //        span contains PUT_FIELD on an externally-referenced object; suggests using
    //        RE_EXECUTE or STUB_WITH_SIDE_EFFECTS instead

    // TODO(#959): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that {@code --force-stub} allows replay to proceed despite unsafe stub warnings. */
  @Test
  @Ignore("Awaiting implementation in #959")
  public void unsafeStubWithForceStubProceedsWithWarning() {
    // Given: Same setup as unsafeStubWithoutForceExitsWithError (mutating method stubbed)
    //        but with --force-stub flag specified
    // When: Replay proceeds past the unsafe stub check
    // Then: Warning about unsafe stub is still emitted in output; replay proceeds;
    //        exit code depends on whether divergences occur (may be non-zero if field
    //        mutations are missed)

    // TODO(#959): Implement test logic
    fail("Not yet implemented");
  }
}
