/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.jsonrpc;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link DeferredOperation}.
 *
 * <p>Verifies that the thread affinity property defaults to {@code null} and can be set and
 * retrieved via getter/setter.
 *
 * <p>All tests are skipped until the {@code threadAffinity} field is added to {@link
 * DeferredOperation} in #747.
 */
public class DeferredOperationThreadAffinityTest {

  /**
   * Verifies that thread affinity defaults to {@code null} on newly created operations.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinityDefaultIsNull]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void threadAffinityDefaultIsNull() {
    // Given: DeferredOperation created via any static factory
    // When:  getThreadAffinity() called
    // Then:  Returns null
    //
    // DeferredOperation op = DeferredOperation.staticMethod("Foo", "bar", null, null);
    // assertThat(op.getThreadAffinity(), is(nullValue()));

    fail("Not yet implemented — awaiting #747");
  }

  /**
   * Verifies that thread affinity can be set and retrieved.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinitySetAndGet]
   */
  @Test
  @Ignore("Awaiting implementation in #747")
  public void threadAffinitySetAndGet() {
    // Given: DeferredOperation
    // When:  setThreadAffinity("fx-thread") then getThreadAffinity()
    // Then:  Returns "fx-thread"
    //
    // DeferredOperation op = DeferredOperation.newInstance("Foo", "var", null);
    // op.setThreadAffinity("fx-thread");
    // assertThat(op.getThreadAffinity(), is("fx-thread"));

    fail("Not yet implemented — awaiting #747");
  }
}
