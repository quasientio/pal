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
 * Unit tests for the STUB_WITH_SIDE_EFFECTS dispatch path in {@code BaseExecMessageDispatcher}.
 * This action combines WAL-based stubbing (returning recorded return values without executing the
 * method) with field mutation replay (applying PUT_FIELD / PUT_STATIC operations from within the
 * stubbed span).
 */
public class ReplayDispatchStubWithSideEffectsTest {

  /**
   * Verifies that STUB_WITH_SIDE_EFFECTS returns the WAL-recorded value and applies field mutations
   * from within the span, without invoking the original method.
   */
  @Test
  @Ignore("Awaiting implementation in #956")
  public void stubWithSideEffectsReturnsValueAndAppliesMutations() {
    // Given: WAL with span (10, 40) containing PUT_FIELD at offset 25
    //        (sets obj.field = "mutated"); policy returns STUB_WITH_SIDE_EFFECTS;
    //        completion at 40 has return value "result"
    // When: dispatchReplay called
    // Then: Returns "result"; obj.field is "mutated"; method not invoked

    // TODO(#956): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that STUB_WITH_SIDE_EFFECTS advances the cursor past the span's completion offset and
   * advances the replay gate, matching STUB_FROM_WAL span-skipping behavior.
   */
  @Test
  @Ignore("Awaiting implementation in #956")
  public void stubWithSideEffectsSkipsSpanLikeStubFromWal() {
    // Given: WAL with span (10, 40); policy returns STUB_WITH_SIDE_EFFECTS
    // When: dispatchReplay called
    // Then: Cursor advanced past 40; gate advanced to 40

    // TODO(#956): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that STUB_WITH_SIDE_EFFECTS handles void methods correctly — returns null while still
   * applying field mutations from the span.
   */
  @Test
  @Ignore("Awaiting implementation in #956")
  public void stubWithSideEffectsHandlesVoidMethodWithMutations() {
    // Given: Void method span with PUT_FIELD mutations; policy returns STUB_WITH_SIDE_EFFECTS
    // When: dispatchReplay called
    // Then: Returns null; field mutations applied

    // TODO(#956): Implement test logic
    fail("Not yet implemented");
  }
}
