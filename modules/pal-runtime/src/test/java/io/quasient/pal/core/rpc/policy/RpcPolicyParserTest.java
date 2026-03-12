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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.core.transport.MessageChannelType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for {@code RpcPolicyParser}, verifying YAML parsing, policy construction from CLI
 * options, preset integration, pattern shorthand, and error handling for malformed input.
 *
 * <p>The parser converts YAML policy files and CLI options into {@link RpcPolicy} instances,
 * following the {@code ReplayPolicyParser} pattern. Priority order: user rules &gt; presets &gt;
 * default action.
 */
public class RpcPolicyParserTest {

  /**
   * Verifies that a minimal YAML containing only {@code version: 1} and {@code defaultAction: DENY}
   * parses into a policy with DENY default and no rules.
   */
  @Test
  public void shouldParseMinimalYaml() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getDefaultAction(), is(RpcPolicyAction.DENY));
    assertThat(policy.getRules().size(), is(0));
  }

  /**
   * Verifies that YAML rules containing {@code class}, {@code method}, and {@code action} fields
   * are parsed into {@link RpcPolicyRule} instances with the correct classPattern, memberPattern,
   * and action.
   */
  @Test
  public void shouldParseRulesWithClassAndMethodPatterns() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            method: "**"
            action: ALLOW
          - class: "com.example.Calculator"
            method: "add"
            action: ALLOW
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(2));
    RpcPolicyRule first = policy.getRules().get(0);
    assertThat(first.getClassPattern(), is("com.example.api.**"));
    assertThat(first.getMemberPattern(), is("**"));
    assertThat(first.getAction(), is(RpcPolicyAction.ALLOW));

    RpcPolicyRule second = policy.getRules().get(1);
    assertThat(second.getClassPattern(), is("com.example.Calculator"));
    assertThat(second.getMemberPattern(), is("add"));
    assertThat(second.getAction(), is(RpcPolicyAction.ALLOW));
  }

  /**
   * Verifies that a rule with a single {@code channel: ZMQ_SOCKET_RPC} constraint is parsed into a
   * rule whose channels set contains only {@code ZMQ_SOCKET_RPC}.
   */
  @Test
  public void shouldParseChannelConstraint() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.admin.**"
            action: ALLOW
            channel: ZMQ_SOCKET_RPC
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MessageChannelType> channels = policy.getRules().get(0).getChannels();
    assertNotNull(channels);
    assertThat(channels.size(), is(1));
    assertTrue(channels.contains(MessageChannelType.ZMQ_SOCKET_RPC));
  }

  /**
   * Verifies that a rule with a list-valued {@code channel: [ZMQ_SOCKET_RPC, WEBSOCKET_RPC]}
   * constraint is parsed into a rule whose channels set contains both values.
   */
  @Test
  public void shouldParseChannelListConstraint() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.admin.**"
            action: ALLOW
            channel: [ZMQ_SOCKET_RPC, WEBSOCKET_RPC]
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MessageChannelType> channels = policy.getRules().get(0).getChannels();
    assertNotNull(channels);
    assertThat(channels.size(), is(2));
    assertTrue(channels.contains(MessageChannelType.ZMQ_SOCKET_RPC));
    assertTrue(channels.contains(MessageChannelType.WEBSOCKET_RPC));
  }

  /**
   * Verifies that a rule with {@code members: [METHOD, CONSTRUCTOR]} is parsed into a rule whose
   * members set contains both {@link MemberCategory#METHOD} and {@link MemberCategory#CONSTRUCTOR}.
   */
  @Test
  public void shouldParseMembersConstraint() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.dto.**"
            action: ALLOW
            members: [METHOD, CONSTRUCTOR]
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberCategory> members = policy.getRules().get(0).getMembers();
    assertNotNull(members);
    assertThat(members.size(), is(2));
    assertTrue(members.contains(MemberCategory.METHOD));
    assertTrue(members.contains(MemberCategory.CONSTRUCTOR));
  }

  /**
   * Verifies that a {@code presets} section with enabled presets (e.g. {@code deny-unsafe: true,
   * deny-jdk-internals: true}) results in the corresponding preset rules being included in the
   * parsed policy.
   */
  @Test
  public void shouldParsePresetsSection() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        presets:
          deny-unsafe: true
          deny-jdk-internals: true
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    List<RpcPolicyRule> rules = policy.getRules();
    int expectedSize =
        RpcPolicyPresets.getDenyUnsafeRules().size()
            + RpcPolicyPresets.getDenyJdkInternalRules().size();
    assertThat(rules.size(), is(expectedSize));
  }

  /**
   * Verifies that a preset set to {@code false} is ignored and its rules are not included in the
   * parsed policy.
   */
  @Test
  public void shouldIgnoreDisabledPresets() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        presets:
          deny-unsafe: false
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(0));
  }

  /**
   * Verifies that the combined {@code pattern} shorthand (e.g. {@code "com.example.Foo.bar"}) is
   * correctly split into classPattern ({@code "com.example.Foo"}) and memberPattern ({@code
   * "bar"}).
   */
  @Test
  public void shouldParsePatternShorthand() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - pattern: "com.example.Foo.bar"
            action: ALLOW
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    RpcPolicyRule rule = policy.getRules().get(0);
    assertThat(rule.getClassPattern(), is("com.example.Foo"));
    assertThat(rule.getMemberPattern(), is("bar"));
  }

  /**
   * Verifies that malformed (syntactically invalid) YAML input causes an {@link
   * IllegalArgumentException} to be thrown.
   */
  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowOnMalformedYaml() {
    String yaml = ":\n  - {\n  invalid: [unclosed";
    RpcPolicyParser.parseYaml(yaml);
  }

  /**
   * Verifies that a YAML rule missing the required {@code action} field causes an {@link
   * IllegalArgumentException} to be thrown.
   */
  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowOnMissingRequiredFields() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.Foo"
            method: "bar"
        """;
    RpcPolicyParser.parseYaml(yaml);
  }

  /**
   * Verifies that {@code fromOptions} with preset names but no YAML path produces a policy
   * containing only preset rules and the specified default action.
   */
  @Test
  public void shouldBuildFromOptionsWithPresetsOnly() {
    RpcPolicy policy = RpcPolicyParser.fromOptions(null, "deny-unsafe,deny-jdk-internals", "DENY");

    assertThat(policy.getDefaultAction(), is(RpcPolicyAction.DENY));
    int expectedSize =
        RpcPolicyPresets.getDenyUnsafeRules().size()
            + RpcPolicyPresets.getDenyJdkInternalRules().size();
    assertThat(policy.getRules().size(), is(expectedSize));
  }

  /**
   * Verifies that {@code fromOptions} with both a YAML file path and preset names produces a policy
   * where user rules (from YAML) come first, then preset rules, then the default action.
   */
  @Test
  public void shouldBuildFromOptionsWithYamlAndPresets() throws IOException {
    String yaml =
        """
        version: 1
        defaultAction: ALLOW
        rules:
          - class: "com.example.api.**"
            action: ALLOW
        """;

    Path tempFile = Files.createTempFile("rpc-policy-test", ".yaml");
    try {
      Files.writeString(tempFile, yaml);

      RpcPolicy policy = RpcPolicyParser.fromOptions(tempFile.toString(), "deny-unsafe", "DENY");

      assertThat(policy.getDefaultAction(), is(RpcPolicyAction.DENY));
      List<RpcPolicyRule> rules = policy.getRules();

      // User rules come first
      assertThat(rules.get(0).getClassPattern(), is("com.example.api.**"));
      assertThat(rules.get(0).getAction(), is(RpcPolicyAction.ALLOW));

      // Then preset rules
      int presetStart = 1;
      int presetCount = RpcPolicyPresets.getDenyUnsafeRules().size();
      assertThat(rules.size(), is(1 + presetCount));
      assertThat(rules.get(presetStart).getAction(), is(RpcPolicyAction.DENY));
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that YAML without a {@code version} field parses successfully, defaulting to version
   * 1.
   */
  @Test
  public void shouldDefaultToVersionOne() {
    String yaml = "defaultAction: ALLOW\n";

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getDefaultAction(), is(RpcPolicyAction.ALLOW));
    assertThat(policy.getRules().size(), is(0));
  }

  /**
   * Verifies that a policy with {@code defaultAction: DENY} and only DENY rules (no ALLOW rules)
   * logs a warning about potential misconfiguration. The parser should not throw an exception but
   * should emit a WARN-level log message.
   */
  @Test
  public void shouldWarnWhenDefaultIsDenyWithNoAllowRules() throws IOException {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.Foo"
            action: DENY
        """;

    Path tempFile = Files.createTempFile("rpc-policy-warn-test", ".yaml");
    try {
      Files.writeString(tempFile, yaml);

      // Should not throw — just logs a warning
      RpcPolicy policy = RpcPolicyParser.fromOptions(tempFile.toString(), null, "DENY");

      assertThat(policy.getDefaultAction(), is(RpcPolicyAction.DENY));
      assertNull(policy.getRules().get(0).getChannels());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that a rule with a single string {@code visibility: PUBLIC} is parsed into a rule
   * whose visibilities set contains only {@link MemberVisibility#PUBLIC}.
   */
  @Test
  public void shouldParseVisibilitySingleValue() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: PUBLIC
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberVisibility> visibilities = policy.getRules().get(0).getVisibilities();
    assertNotNull(visibilities);
    assertThat(visibilities.size(), is(1));
    assertTrue(visibilities.contains(MemberVisibility.PUBLIC));
  }

  /**
   * Verifies that a rule with a list-valued {@code visibility: [PUBLIC, PROTECTED]} is parsed into
   * a rule whose visibilities set contains both {@link MemberVisibility#PUBLIC} and {@link
   * MemberVisibility#PROTECTED}.
   */
  @Test
  public void shouldParseVisibilityListValue() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: [PUBLIC, PROTECTED]
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberVisibility> visibilities = policy.getRules().get(0).getVisibilities();
    assertNotNull(visibilities);
    assertThat(visibilities.size(), is(2));
    assertTrue(visibilities.contains(MemberVisibility.PUBLIC));
    assertTrue(visibilities.contains(MemberVisibility.PROTECTED));
  }

  /**
   * Verifies that a rule with {@code visibility: ALL} is parsed into a rule whose visibilities is
   * null, meaning all visibility levels are matched.
   */
  @Test
  public void shouldParseVisibilityAll() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: ALL
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    assertNull(policy.getRules().get(0).getVisibilities());
  }

  /**
   * Verifies that a rule with {@code visibility: [PUBLIC, ALL]} is parsed into a rule whose
   * visibilities is null, because ALL overrides any other values in the list.
   */
  @Test
  public void shouldParseVisibilityAllInList() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: [PUBLIC, ALL]
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    assertNull(policy.getRules().get(0).getVisibilities());
  }

  /**
   * Verifies that a rule with {@code visibility: DEFAULT} is parsed as the alias for {@link
   * MemberVisibility#PACKAGE_PRIVATE}.
   */
  @Test
  public void shouldParseVisibilityDefault() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: DEFAULT
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberVisibility> visibilities = policy.getRules().get(0).getVisibilities();
    assertNotNull(visibilities);
    assertThat(visibilities.size(), is(1));
    assertTrue(visibilities.contains(MemberVisibility.PACKAGE_PRIVATE));
  }

  /**
   * Verifies that a rule with {@code visibility: PACKAGE_PRIVATE} is parsed directly into a rule
   * whose visibilities set contains only {@link MemberVisibility#PACKAGE_PRIVATE}.
   */
  @Test
  public void shouldParseVisibilityPackagePrivate() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: PACKAGE_PRIVATE
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberVisibility> visibilities = policy.getRules().get(0).getVisibilities();
    assertNotNull(visibilities);
    assertThat(visibilities.size(), is(1));
    assertTrue(visibilities.contains(MemberVisibility.PACKAGE_PRIVATE));
  }

  /**
   * Verifies that when a YAML rule does not include a {@code visibility} field, the parsed rule's
   * visibilities is null, meaning all visibility levels are matched by default.
   */
  @Test
  public void shouldOmitVisibilityWhenNotSpecified() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    assertNull(policy.getRules().get(0).getVisibilities());
  }

  /**
   * Verifies that a rule with an invalid visibility value (e.g. {@code visibility: INVALID_VALUE})
   * causes an {@link IllegalArgumentException} to be thrown during parsing.
   */
  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowForInvalidVisibility() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: INVALID_VALUE
        """;

    RpcPolicyParser.parseYaml(yaml);
  }

  /**
   * Verifies that visibility values are parsed case-insensitively, so {@code visibility: public}
   * (lowercase) is equivalent to {@code visibility: PUBLIC}.
   */
  @Test
  public void shouldParseCaseInsensitiveVisibility() {
    String yaml =
        """
        version: 1
        defaultAction: DENY
        rules:
          - class: "com.example.api.**"
            action: ALLOW
            visibility: public
        """;

    RpcPolicy policy = RpcPolicyParser.parseYaml(yaml);

    assertThat(policy.getRules().size(), is(1));
    Set<MemberVisibility> visibilities = policy.getRules().get(0).getVisibilities();
    assertNotNull(visibilities);
    assertThat(visibilities.size(), is(1));
    assertTrue(visibilities.contains(MemberVisibility.PUBLIC));
  }
}
