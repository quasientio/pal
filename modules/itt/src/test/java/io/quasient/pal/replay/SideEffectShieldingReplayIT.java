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
 * Integration tests for {@code STUB_WITH_SIDE_EFFECTS} replay action.
 *
 * <p>Validates that when a method is replayed with the {@code STUB_WITH_SIDE_EFFECTS} action, its
 * return value is stubbed from the WAL while field mutations (PUT_FIELD / PUT_STATIC) recorded
 * within the method's span are replayed via reflection. This ensures that dependent code sees
 * correct field values even though the method body was not re-executed.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class SideEffectShieldingReplayIT extends AbstractCliIT {

  /** The number of threads for this parameterized test run. */
  @SuppressWarnings("UnusedVariable")
  private final int threadCount;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public SideEffectShieldingReplayIT(int threadCount) {
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
   * Verifies that {@code STUB_WITH_SIDE_EFFECTS} stubs the return value while replaying field
   * mutations from the WAL span.
   */
  @Test
  @Ignore("Awaiting implementation in #959")
  public void stubWithSideEffectsAppliesFieldMutations() {
    // Given: A recorded WAL from an app with a mutating method that sets a field on a
    //        passed-in object (e.g., enricher.enrich(order) sets order.enriched = true)
    // When: Replay with STUB_WITH_SIDE_EFFECTS action for the mutating method
    //        (via replay policy or CLI flags)
    // Then: The method's return value is stubbed from WAL (method body not re-executed);
    //        field mutations (PUT_FIELD) within the span are replayed via reflection;
    //        dependent code that reads the mutated field sees the correct value;
    //        zero divergences reported

    // TODO(#959): Implement test logic
    fail("Not yet implemented");
  }
}
