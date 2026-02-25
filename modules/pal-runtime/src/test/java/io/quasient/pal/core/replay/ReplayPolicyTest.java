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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.messages.types.MessageType;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayPolicy} — the action resolution component that determines what replay
 * action to take for each operation. In Phase 1, the policy is hardcoded to always return {@code
 * RE_EXECUTE}.
 *
 * <p>Tests verify that RE_EXECUTE is returned for all operation types including instance methods,
 * constructors, and static methods.
 */
public class ReplayPolicyTest {

  /** Verifies that the default policy always returns RE_EXECUTE regardless of input. */
  @Test
  public void alwaysReturnsReExecute() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("com.example.Foo", "bar", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayPolicy.ReplayAction.RE_EXECUTE));
  }

  /** Verifies that RE_EXECUTE is returned for constructor operations. */
  @Test
  public void reExecuteForConstructor() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("Foo", "new", MessageType.EXEC_CONSTRUCTOR),
        is(ReplayPolicy.ReplayAction.RE_EXECUTE));
  }

  /** Verifies that RE_EXECUTE is returned for static method operations. */
  @Test
  public void reExecuteForStaticMethod() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("Foo", "bar", MessageType.EXEC_CLASS_METHOD),
        is(ReplayPolicy.ReplayAction.RE_EXECUTE));
  }
}
