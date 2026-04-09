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
        new RpcPolicyRule("com.example.Calculator", "add", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.add",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
  }

  @Test
  public void shouldNotMatchDifferentMethod() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "add", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.subtract",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(false));
  }

  @Test
  public void shouldMatchWildcardMethod() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "**", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.Calculator.add",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Calculator.subtract",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
  }

  @Test
  public void shouldMatchWildcardPackage() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.sub.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
  }

  @Test
  public void shouldMatchSingleSegmentWildcard() {
    // Single-segment wildcard (*) at the end matches exactly one segment.
    // "com.example.*" as classPattern with null memberPattern -> fullPattern = "com.example.*.**"
    // But to test * specifically: use it in a terminal position via the memberPattern.
    // Here classPattern="com.example.Calculator" and memberPattern="*" (single-segment).
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "*", RpcPolicyAction.ALLOW, null, null, null);

    // Matches exactly one method segment
    assertThat(
        rule.matches(
            "com.example.Calculator.add",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
    // Does not match when there are extra segments after Calculator
    assertThat(
        rule.matches(
            "com.example.Calculator.add.extra",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
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
            null,
            null);

    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD, null),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.WEBSOCKET_RPC, MemberCategory.METHOD, null),
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
            EnumSet.of(MemberCategory.METHOD, MemberCategory.STATIC_METHOD),
            null);

    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD, null),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.CONSTRUCTOR,
            null),
        is(false));
  }

  @Test
  public void shouldMatchAllChannelsWhenNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    for (MessageChannelType channel : MessageChannelType.values()) {
      assertThat(
          rule.matches("com.example.Foo.bar", channel, MemberCategory.METHOD, null), is(true));
    }
  }

  @Test
  public void shouldMatchAllMembersWhenNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    for (MemberCategory category : MemberCategory.values()) {
      assertThat(
          rule.matches("com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, category, null),
          is(true));
    }
  }

  @Test
  public void shouldBeCaseInsensitive() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", "Add", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.calculator.add",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
  }

  @Test
  public void shouldDefaultMethodPatternToDoubleWildcard() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.Calculator", null, RpcPolicyAction.ALLOW, null, null, null);

    assertThat(rule.getMemberPattern(), is("**"));
    assertThat(rule.getFullPattern(), is("com.example.Calculator.**"));
    assertThat(
        rule.matches(
            "com.example.Calculator.anyMethod",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            null),
        is(true));
  }

  // ---------------------------------------------------------------------------
  // Visibility filter tests
  // ---------------------------------------------------------------------------

  @Test
  public void shouldMatchWhenVisibilitiesIsNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PRIVATE),
        is(true));
  }

  @Test
  public void shouldMatchWhenVisibilityInSet() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            null,
            EnumSet.of(MemberVisibility.PUBLIC, MemberVisibility.PROTECTED));

    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PUBLIC),
        is(true));
  }

  @Test
  public void shouldNotMatchWhenVisibilityNotInSet() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            null,
            EnumSet.of(MemberVisibility.PUBLIC));

    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PRIVATE),
        is(false));
  }

  @Test
  public void shouldShortCircuitOnVisibilityBeforePatternMatch() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "**", "**", RpcPolicyAction.ALLOW, null, null, EnumSet.of(MemberVisibility.PUBLIC));

    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PACKAGE_PRIVATE),
        is(false));
  }

  @Test
  public void shouldFilterByVisibilityInMetadata() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            null,
            EnumSet.of(MemberVisibility.PUBLIC));

    assertThat(
        rule.matchesForMetadata(
            "com.example.Foo.bar", MemberCategory.METHOD, MemberVisibility.PROTECTED),
        is(false));
  }

  @Test
  public void shouldMatchAllVisibilitiesInMetadataWhenNull() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(
        rule.matchesForMetadata(
            "com.example.Foo.bar", MemberCategory.METHOD, MemberVisibility.PRIVATE),
        is(true));
  }

  /**
   * Verifies that a rule with visibility constraints does NOT match when visibility is {@code null}
   * (unknown). Visibility-restricted rules require known visibility to fire — this prevents false
   * denials when the class cannot be loaded for modifier resolution.
   */
  @Test
  public void shouldNotMatchWhenVisibilityNullAndRuleHasVisibilityConstraints() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            null,
            EnumSet.of(MemberVisibility.PUBLIC));

    assertThat(
        rule.matches(
            "com.example.Foo.bar", MessageChannelType.ZMQ_SOCKET_RPC, MemberCategory.METHOD, null),
        is(false));
  }

  @Test
  public void shouldReturnVisibilitiesViaGetter() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            null,
            null,
            EnumSet.of(MemberVisibility.PUBLIC, MemberVisibility.PROTECTED));

    assertThat(
        rule.getVisibilities(),
        is(EnumSet.of(MemberVisibility.PUBLIC, MemberVisibility.PROTECTED)));
  }

  @Test
  public void shouldReturnNullVisibilitiesViaGetter() {
    RpcPolicyRule rule =
        new RpcPolicyRule("com.example.**", "**", RpcPolicyAction.ALLOW, null, null, null);

    assertThat(rule.getVisibilities() == null, is(true));
  }

  @Test
  public void shouldCombineVisibilityWithChannelAndMemberFilters() {
    RpcPolicyRule rule =
        new RpcPolicyRule(
            "com.example.**",
            "**",
            RpcPolicyAction.ALLOW,
            EnumSet.of(MessageChannelType.ZMQ_SOCKET_RPC),
            EnumSet.of(MemberCategory.METHOD),
            EnumSet.of(MemberVisibility.PUBLIC));

    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PUBLIC),
        is(true));
    assertThat(
        rule.matches(
            "com.example.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD,
            MemberVisibility.PRIVATE),
        is(false));
  }
}
