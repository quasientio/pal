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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.core.transport.MessageChannelType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link RpcPolicy}, verifying first-match-wins rule evaluation, default action
 * fallback, channel and member-category filtering, preset integration, and class-method path
 * construction.
 */
public class RpcPolicyTest {

  /**
   * Verifies that the default action is returned when no rules are defined and no rule matches the
   * evaluated operation.
   */
  @Test
  public void shouldReturnDefaultActionWhenNoRulesMatch() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.DENY);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies first-match-wins semantics: when multiple rules match, the first matching rule's
   * action is returned.
   */
  @Test
  public void shouldReturnFirstMatchingRuleAction() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null),
            new RpcPolicyRule("com.**", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }

  /**
   * Verifies that evaluation falls through non-matching rules to find the first match in the
   * ordered rule list.
   */
  @Test
  public void shouldFallThroughToSecondRule() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule("com.example.api.**", null, RpcPolicyAction.ALLOW, null, null, null),
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.internal.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies that channel-scoped rules only match operations arriving on the specified channel, and
   * operations on other channels fall through to subsequent rules.
   */
  @Test
  public void shouldFilterByChannelInRules() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "com.example.**",
                null,
                RpcPolicyAction.ALLOW,
                EnumSet.of(MessageChannelType.ZMQ_SOCKET_RPC),
                null,
                null),
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo", "bar", MessageChannelType.WEBSOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies that member-category-scoped rules only match operations targeting the specified member
   * categories, and operations with other categories fall through.
   */
  @Test
  public void shouldFilterByMemberCategoryInRules() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "com.example.Config.**",
                null,
                RpcPolicyAction.ALLOW,
                null,
                EnumSet.of(MemberCategory.FIELD_GET),
                null),
            new RpcPolicyRule(
                "com.example.Config.**", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Config",
            "debugMode",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.FIELD_SET);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /** Verifies that a policy with defaultAction=ALLOW returns ALLOW when no rules match. */
  @Test
  public void shouldReturnAllowForDefaultAllowPolicy() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }

  /**
   * Verifies that preset deny rules are evaluated before the default action, so preset-blocked
   * operations are denied even when the default action is ALLOW.
   */
  @Test
  public void shouldEvaluatePresetsBeforeDefaultAction() {
    List<RpcPolicyRule> presetRules = RpcPolicyPresets.getDenyUnsafeRules();
    RpcPolicy policy = new RpcPolicy(presetRules, RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "java.lang.System", "exit", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies that user-defined rules placed before preset rules take priority, allowing users to
   * override preset denials with explicit ALLOW rules.
   */
  @Test
  public void shouldAllowUserRulesToOverridePresets() {
    List<RpcPolicyRule> rules = new ArrayList<>();
    rules.add(
        new RpcPolicyRule("java.lang.Class", "forName", RpcPolicyAction.ALLOW, null, null, null));
    rules.addAll(RpcPolicyPresets.getDenyClassloadingRules());
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    RpcPolicyAction result =
        policy.evaluate(
            "java.lang.Class", "forName", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }

  /**
   * Verifies that the deny-unsafe preset blocks all member types (methods, constructors, fields) on
   * dangerous classes like {@code ProcessBuilder}, preventing bypass via field access.
   */
  @Test
  public void shouldCheckAccessForAllDangerousClassMembers() {
    List<RpcPolicyRule> presetRules = RpcPolicyPresets.getDenyUnsafeRules();
    RpcPolicy policy = new RpcPolicy(presetRules, RpcPolicyAction.ALLOW);

    assertThat(
        policy.evaluate(
            "java.lang.ProcessBuilder",
            "command",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.FIELD_GET),
        is(RpcPolicyAction.DENY));

    assertThat(
        policy.evaluate(
            "java.lang.ProcessBuilder",
            "start",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.CONSTRUCTOR),
        is(RpcPolicyAction.DENY));

    assertThat(
        policy.evaluate(
            "java.lang.ProcessBuilder",
            "start",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD),
        is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies that {@code evaluate()} correctly concatenates {@code className + "." + memberName} to
   * form the path used for pattern matching.
   */
  @Test
  public void shouldBuildClassMethodPath() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule("com.example.Foo", "bar", RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo", "bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }
}
