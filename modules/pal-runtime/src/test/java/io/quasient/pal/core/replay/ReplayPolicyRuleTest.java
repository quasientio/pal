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

import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.messages.types.MessageType;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayPolicyRule} — individual rule model with Ant-style pattern matching
 * for class and method names. Rules are used by {@link ReplayPolicy} to determine what replay
 * action to take for a given operation.
 */
public class ReplayPolicyRuleTest {

  /**
   * Verifies that an exact class and method pattern matches the corresponding fully-qualified
   * class.method path.
   */
  @Test
  public void exactClassAndMethodMatch() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "bar", ReplayAction.STUB_FROM_WAL);

    assertThat(rule.matches("com.example.Foo.bar", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /**
   * Verifies that a wildcard method pattern ("**") matches any method name within the specified
   * class.
   */
  @Test
  public void wildcardMethodMatch() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "**", ReplayAction.STUB_FROM_WAL);

    assertThat(
        rule.matches("com.example.Foo.anyMethod", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /**
   * Verifies that a wildcard class pattern ("java.io.**") matches any class within the package
   * hierarchy and any method.
   */
  @Test
  public void wildcardClassMatch() {
    ReplayPolicyRule rule = new ReplayPolicyRule("java.io.**", "**", ReplayAction.STUB_FROM_WAL);

    assertThat(
        rule.matches("java.io.InputStream.read", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /** Verifies that a rule returns false when the class and method do not match the patterns. */
  @Test
  public void noMatchReturnsFalse() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "bar", ReplayAction.STUB_FROM_WAL);

    assertThat(rule.matches("com.other.Baz.qux", MessageType.EXEC_INSTANCE_METHOD), is(false));
  }
}
