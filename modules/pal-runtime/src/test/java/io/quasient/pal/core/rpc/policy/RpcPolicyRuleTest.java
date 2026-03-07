/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.quasient.pal.core.transport.MessageChannelType;
import java.util.EnumSet;
import org.junit.Test;

/**
 * Unit tests for {@link RpcPolicyRule}, the core pattern-matching class for RPC policy evaluation.
 *
 * <p>Verifies Ant-style pattern matching with dot separators, channel filtering, member category
 * filtering, wildcard behavior, case insensitivity, and default patterns.
 */
public class RpcPolicyRuleTest {

  @Test
  public void shouldMatchExactClassAndMethod() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "add", RpcPolicyAction.ALLOW, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.add", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
  }

  @Test
  public void shouldNotMatchDifferentMethod() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "add", RpcPolicyAction.ALLOW, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.subtract",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD),
        is(false));
  }

  @Test
  public void shouldMatchWildcardMethod() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "**", RpcPolicyAction.ALLOW, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.add", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Calculator.subtract",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD),
        is(true));
  }

  @Test
  public void shouldMatchWildcardPackage() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null);

    assertThat(
        rule.matches(
            "com.example.sub.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
  }

  @Test
  public void shouldMatchSingleSegmentWildcard() {
    // Single-segment wildcard (*) at the end matches exactly one segment.
    // "com.example.*" as classPattern with null memberPattern -> fullPattern = "com.example.*.**"
    // But to test * specifically: use it in a terminal position via the memberPattern.
    // Here classPattern="com.example.Calculator" and memberPattern="*" (single-segment).
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "*", RpcPolicyAction.ALLOW, null, null);

    // Matches exactly one method segment
    assertThat(
        rule.matches(
            "com.example.Calculator.add", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
    // Does not match when there are extra segments after Calculator
    assertThat(
        rule.matches(
            "com.example.Calculator.add.extra",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD),
        is(false));
  }

  @Test
  public void shouldFilterByChannel() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            EnumSet.of(MessageChannelType.ZMQ_SOCKET_RPC),
            null);

    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.WEBSOCKET_RPC, MemberCategory.METHOD),
        is(false));
  }

  @Test
  public void shouldFilterByMemberCategory() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            EnumSet.of(MemberCategory.METHOD, MemberCategory.STATIC_METHOD));

    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.CONSTRUCTOR),
        is(false));
  }

  @Test
  public void shouldMatchAllChannelsWhenNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null);

    for (MessageChannelType channel : MessageChannelType.values()) {
      assertThat(rule.matches("com.example.Foo.bar", channel, MemberCategory.METHOD), is(true));
    }
  }

  @Test
  public void shouldMatchAllMembersWhenNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null);

    for (MemberCategory category : MemberCategory.values()) {
      assertThat(
          rule.matches("com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, category),
          is(true));
    }
  }

  @Test
  public void shouldBeCaseInsensitive() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "Add", RpcPolicyAction.ALLOW, null, null);

    assertThat(
        rule.matches(
            "com.example.calculator.add", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD),
        is(true));
  }

  @Test
  public void shouldDefaultMethodPatternToDoubleWildcard() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", null, RpcPolicyAction.ALLOW, null, null);

    assertThat(rule.getMemberPattern(), is("**"));
    assertThat(rule.getFullPattern(), is("com.example.Calculator.**"));
    assertThat(
        rule.matches(
            "com.example.Calculator.anyMethod",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD),
        is(true));
  }
}
