/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            MemberCategory.METHOD,
            null);

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
            "com.example.Foo",
            "bar",
            MessageChannelType.WEBSOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            MemberCategory.FIELD_SET,
            null);

    assertThat(result, is(RpcPolicyAction.DENY));
  }

  /** Verifies that a policy with defaultAction=ALLOW returns ALLOW when no rules match. */
  @Test
  public void shouldReturnAllowForDefaultAllowPolicy() {
    RpcPolicy policy = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            "java.lang.System",
            "exit",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            "java.lang.Class",
            "forName",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

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
            MemberCategory.FIELD_GET,
            null),
        is(RpcPolicyAction.DENY));

    assertThat(
        policy.evaluate(
            "java.lang.ProcessBuilder",
            "start",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.CONSTRUCTOR,
            null),
        is(RpcPolicyAction.DENY));

    assertThat(
        policy.evaluate(
            "java.lang.ProcessBuilder",
            "start",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
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
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }

  /**
   * Verifies that visibility-scoped rules only match operations with the specified visibility, and
   * operations with other visibilities fall through to the default action.
   */
  @Test
  public void shouldFilterByVisibilityInRules() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "com.example.**",
                null,
                RpcPolicyAction.ALLOW,
                null,
                null,
                EnumSet.of(MemberVisibility.PUBLIC)));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    assertThat(
        policy.evaluate(
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PUBLIC),
        is(RpcPolicyAction.ALLOW));

    assertThat(
        policy.evaluate(
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PRIVATE),
        is(RpcPolicyAction.DENY));
  }

  /**
   * Verifies that evaluation falls through rules whose visibility constraint does not match, and
   * continues to subsequent rules that match all visibilities (null constraint).
   */
  @Test
  public void shouldFallThroughWhenVisibilityDoesNotMatch() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "com.example.**",
                null,
                RpcPolicyAction.ALLOW,
                null,
                null,
                EnumSet.of(MemberVisibility.PUBLIC)),
            new RpcPolicyRule(
                "com.example.**", null, RpcPolicyAction.LOG_AND_ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PRIVATE);

    assertThat(result, is(RpcPolicyAction.LOG_AND_ALLOW));
  }

  /**
   * Verifies that {@code hasVisibilityRules()} returns true when at least one rule has a non-null
   * visibilities constraint.
   */
  @Test
  public void shouldReportHasVisibilityRulesTrue() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null),
            new RpcPolicyRule(
                "com.internal.**",
                null,
                RpcPolicyAction.DENY,
                null,
                null,
                EnumSet.of(MemberVisibility.PRIVATE)));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    assertThat(policy.hasVisibilityRules(), is(true));
  }

  /**
   * Verifies that {@code hasVisibilityRules()} returns false when all rules have null visibilities
   * constraints.
   */
  @Test
  public void shouldReportHasVisibilityRulesFalse() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null),
            new RpcPolicyRule("com.**", null, RpcPolicyAction.DENY, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    assertThat(policy.hasVisibilityRules(), is(false));
  }

  /**
   * Verifies that visibility filtering applies to metadata evaluation, so that non-public members
   * are excluded from metadata when the policy restricts visibility.
   */
  @Test
  public void shouldFilterByVisibilityInMetadata() {
    List<RpcPolicyRule> rules =
        List.of(
            new RpcPolicyRule(
                "com.example.**",
                null,
                RpcPolicyAction.DENY,
                null,
                null,
                EnumSet.of(
                    MemberVisibility.PROTECTED,
                    MemberVisibility.PACKAGE_PRIVATE,
                    MemberVisibility.PRIVATE)),
            new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    assertThat(
        policy.isAccessibleForMetadata(
            "com.example.Foo", "bar", MemberCategory.METHOD, MemberVisibility.PROTECTED),
        is(false));

    assertThat(
        policy.isAccessibleForMetadata(
            "com.example.Foo", "bar", MemberCategory.METHOD, MemberVisibility.PUBLIC),
        is(true));
  }

  /**
   * Verifies that when no rules have visibility constraints, passing null visibility to {@code
   * evaluate()} allows rules to match normally (the visibility filter is skipped).
   */
  @Test
  public void shouldPassNullVisibilityWhenNoVisibilityRules() {
    List<RpcPolicyRule> rules =
        List.of(new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null));
    RpcPolicy policy = new RpcPolicy(rules, RpcPolicyAction.DENY);

    assertThat(policy.hasVisibilityRules(), is(false));

    RpcPolicyAction result =
        policy.evaluate(
            "com.example.Foo",
            "bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null);

    assertThat(result, is(RpcPolicyAction.ALLOW));
  }
}
