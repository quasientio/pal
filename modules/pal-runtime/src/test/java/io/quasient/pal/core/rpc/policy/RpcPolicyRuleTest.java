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
import static org.junit.Assert.fail;

import io.quasient.pal.core.transport.MessageChannelType;
import java.util.EnumSet;
import org.junit.Ignore;
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

  // ---------------------------------------------------------------------------
  // Visibility filter tests (awaiting RpcPolicyRule visibility extension #1092)
  // ---------------------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldMatchWhenVisibilitiesIsNull() {
    // Given: Rule with visibilities = null (match all)
    // When: matches() called with MemberVisibility.PRIVATE
    // Then: Returns true (null means no visibility restriction)

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldMatchWhenVisibilityInSet() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC, PROTECTED)
    // When: matches() called with MemberVisibility.PUBLIC
    // Then: Returns true

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldNotMatchWhenVisibilityNotInSet() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC)
    // When: matches() called with MemberVisibility.PRIVATE
    // Then: Returns false

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldShortCircuitOnVisibilityBeforePatternMatch() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC) and broad pattern **.**
    // When: matches() called with MemberVisibility.PACKAGE_PRIVATE
    // Then: Returns false (visibility check prevents pattern evaluation)

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldFilterByVisibilityInMetadata() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC)
    // When: matchesForMetadata() called with MemberVisibility.PROTECTED
    // Then: Returns false

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldMatchAllVisibilitiesInMetadataWhenNull() {
    // Given: Rule with visibilities = null
    // When: matchesForMetadata() called with MemberVisibility.PRIVATE
    // Then: Returns true

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldMatchWithNullVisibilityParameter() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC)
    // When: matches() called with visibility = null (skip check)
    // Then: Returns true (null visibility parameter means skip the check)

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldReturnVisibilitiesViaGetter() {
    // Given: Rule with visibilities = EnumSet.of(PUBLIC, PROTECTED)
    // When: getVisibilities() called
    // Then: Returns set containing PUBLIC and PROTECTED

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldReturnNullVisibilitiesViaGetter() {
    // Given: Rule with visibilities = null
    // When: getVisibilities() called
    // Then: Returns null

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1092")
  public void shouldCombineVisibilityWithChannelAndMemberFilters() {
    // Given: Rule with channels = EnumSet.of(ZMQ_SOCKET_RPC),
    //        members = EnumSet.of(METHOD), visibilities = EnumSet.of(PUBLIC)
    // When: matches() called with matching channel, member, and visibility
    // Then: Returns true
    // When: matches() called with matching channel/member but PRIVATE visibility
    // Then: Returns false

    // TODO(#1092): Implement test logic
    fail("Not yet implemented");
  }
}
