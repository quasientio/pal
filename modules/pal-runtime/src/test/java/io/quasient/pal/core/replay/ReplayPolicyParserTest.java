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
 * Unit tests for {@code ReplayPolicyParser} — parses YAML replay policy files and CLI options into
 * {@link ReplayPolicy} instances. Covers YAML parsing, built-in shield-IO rules, CLI pattern
 * overrides, error handling, and stub-all-else behavior.
 */
public class ReplayPolicyParserTest {

  /**
   * Verifies that a well-formed YAML string with a default action and multiple rules is correctly
   * parsed into a {@link ReplayPolicy} with the expected rules and default action.
   */
  @Test
  public void parsesYamlWithRulesAndDefault() {
    String yaml =
        """
        defaultAction: STUB_FROM_WAL
        rules:
          - class: "java.lang.System"
            method: "currentTimeMillis"
            action: STUB_FROM_WAL
          - class: "com.example.Service"
            method: "**"
            action: RE_EXECUTE
        """;

    ReplayPolicy policy = ReplayPolicyParser.parseYaml(yaml);

    assertThat(policy.getDefaultAction(), is(ReplayAction.STUB_FROM_WAL));
    assertThat(policy.getRules().size(), is(2));
    assertThat(
        policy.getAction("java.lang.System", "currentTimeMillis", MessageType.EXEC_CLASS_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
    assertThat(
        policy.getAction("com.example.Service", "doWork", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
  }

  /**
   * Verifies that a YAML string containing only a default action and no rules produces a policy
   * with an empty rule list and the specified default.
   */
  @Test
  public void parsesEmptyYamlToDefaultPolicy() {
    String yaml = "defaultAction: RE_EXECUTE\n";

    ReplayPolicy policy = ReplayPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(0));
    assertThat(policy.getDefaultAction(), is(ReplayAction.RE_EXECUTE));
  }

  /**
   * Verifies that the {@code --shield-io} flag activates built-in I/O stubbing rules covering
   * System.currentTimeMillis, System.nanoTime, Math.random, and other I/O-dependent operations.
   */
  @Test
  public void shieldIoRulesApplied() {
    ReplayPolicy policy = ReplayPolicyParser.fromOptions(null, true, false, null, null, false);

    assertThat(
        policy.getAction("java.lang.System", "currentTimeMillis", MessageType.EXEC_CLASS_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
    assertThat(
        policy.getAction("java.lang.System", "nanoTime", MessageType.EXEC_CLASS_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
    assertThat(
        policy.getAction("java.lang.Math", "random", MessageType.EXEC_CLASS_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
    assertThat(
        policy.getAction("java.util.Random", "nextInt", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
  }

  /**
   * Verifies that CLI patterns (--re-execute, --stub) take precedence over YAML-defined rules when
   * both are provided, and that --stub-all-else adds a catch-all STUB_FROM_WAL default.
   */
  @Test
  public void cliPatternsOverrideYaml() {
    ReplayPolicy policy =
        ReplayPolicyParser.fromOptions(
            null, false, false, new String[] {"com.example.**"}, null, true);

    // CLI --re-execute pattern should match
    assertThat(
        policy.getAction("com.example.Service", "doWork", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
    // Everything else should be stubbed due to --stub-all-else
    assertThat(
        policy.getAction("com.other.Bar", "baz", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
  }

  /**
   * Verifies that malformed YAML content causes an {@link IllegalArgumentException} with a
   * descriptive error message.
   */
  @Test(expected = IllegalArgumentException.class)
  public void malformedYamlThrowsException() {
    ReplayPolicyParser.parseYaml("{{invalid yaml: [unclosed");
  }

  /**
   * Verifies that --re-execute patterns combined with --stub-all-else sets the default action to
   * STUB_FROM_WAL, so all operations not matching --re-execute patterns are stubbed.
   */
  @Test
  public void stubAllElseAddsDefaultStub() {
    ReplayPolicy policy =
        ReplayPolicyParser.fromOptions(
            null, false, false, new String[] {"com.myapp.**"}, null, true);

    assertThat(policy.getDefaultAction(), is(ReplayAction.STUB_FROM_WAL));
    // Matching --re-execute pattern
    assertThat(
        policy.getAction("com.myapp.Foo", "bar", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
    // Non-matching falls to default STUB_FROM_WAL
    assertThat(
        policy.getAction("com.other.Baz", "qux", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
  }
}
