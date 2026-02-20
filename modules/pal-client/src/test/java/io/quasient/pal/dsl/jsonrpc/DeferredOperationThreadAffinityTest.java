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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link DeferredOperation}.
 *
 * <p>Verifies that the thread affinity property defaults to {@code null} and can be set and
 * retrieved via getter/setter.
 */
public class DeferredOperationThreadAffinityTest {

  /**
   * Verifies that thread affinity defaults to {@code null} on newly created operations.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinityDefaultIsNull]
   */
  @Test
  public void threadAffinityDefaultIsNull() {
    DeferredOperation op = DeferredOperation.staticMethod("Foo", "bar", null, null);
    assertThat(op.getThreadAffinity(), is(nullValue()));
  }

  /**
   * Verifies that thread affinity can be set and retrieved.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinitySetAndGet]
   */
  @Test
  public void threadAffinitySetAndGet() {
    DeferredOperation op = DeferredOperation.newInstance("Foo", "var", null);
    op.setThreadAffinity("fx-thread");
    assertThat(op.getThreadAffinity(), is("fx-thread"));
  }
}
