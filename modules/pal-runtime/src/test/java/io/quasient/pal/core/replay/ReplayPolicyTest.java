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
import static org.junit.Assert.fail;

import io.quasient.pal.messages.types.MessageType;
import org.junit.Ignore;
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

  /**
   * Verifies that when multiple rules match, the first matching rule wins.
   *
   * @see <a href="https://github.io/quasientinc/pal/issues/946">#946</a>
   */
  @Test
  @Ignore("Awaiting implementation in #946")
  public void firstMatchingRuleWins() {
    // Given: Policy with rules [com.example.Foo.bar->STUB_FROM_WAL,
    //        com.example.Foo.**->RE_EXECUTE]
    // When: getAction("com.example.Foo", "bar", EXEC_INSTANCE_METHOD)
    // Then: Returns STUB_FROM_WAL (first match wins)

    // TODO(#946): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the default action is returned when no rule matches the given operation.
   *
   * @see <a href="https://github.io/quasientinc/pal/issues/946">#946</a>
   */
  @Test
  @Ignore("Awaiting implementation in #946")
  public void defaultActionWhenNoRuleMatches() {
    // Given: Policy with rules for com.example.** and defaultAction=RE_EXECUTE
    // When: getAction("com.other.Bar", "baz", EXEC_INSTANCE_METHOD)
    // Then: Returns RE_EXECUTE (default fallback)

    // TODO(#946): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that a configured default action is used when no rules match.
   *
   * @see <a href="https://github.io/quasientinc/pal/issues/946">#946</a>
   */
  @Test
  @Ignore("Awaiting implementation in #946")
  public void configuredDefaultActionUsed() {
    // Given: Policy with defaultAction=STUB_FROM_WAL, no rules
    // When: getAction("com.any.Class", "method", EXEC_INSTANCE_METHOD)
    // Then: Returns STUB_FROM_WAL

    // TODO(#946): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the no-arg constructor preserves backward-compatible behavior (RE_EXECUTE).
   *
   * @see <a href="https://github.io/quasientinc/pal/issues/946">#946</a>
   */
  @Test
  @Ignore("Awaiting implementation in #946")
  public void defaultConstructorPreservesBackwardCompatibility() {
    // Given: new ReplayPolicy() (no-arg constructor)
    // When: getAction(...) called with any class/method/type
    // Then: Returns RE_EXECUTE (backward compatible)

    // TODO(#946): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that a rule with STUB_WITH_SIDE_EFFECTS action is correctly resolved.
   *
   * @see <a href="https://github.io/quasientinc/pal/issues/946">#946</a>
   */
  @Test
  @Ignore("Awaiting implementation in #946")
  public void stubWithSideEffectsAction() {
    // Given: Policy with rule com.example.Enricher.**->STUB_WITH_SIDE_EFFECTS
    // When: getAction("com.example.Enricher", "enrich", EXEC_INSTANCE_METHOD)
    // Then: Returns STUB_WITH_SIDE_EFFECTS

    // TODO(#946): Implement test logic
    fail("Not yet implemented");
  }
}
