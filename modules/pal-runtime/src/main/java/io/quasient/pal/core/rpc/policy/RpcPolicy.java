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

import io.quasient.pal.core.transport.MessageChannelType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable container holding an ordered list of {@link RpcPolicyRule} instances and a default
 * action. Evaluates incoming RPC operations against the rules using first-match-wins semantics.
 *
 * <p>The evaluation algorithm:
 *
 * <ol>
 *   <li>Build the match path as {@code className + "." + memberName}.
 *   <li>Iterate through rules in order; return the action of the first matching rule.
 *   <li>If no rule matches, return the {@link #defaultAction}.
 * </ol>
 *
 * <p>The default no-arg constructor creates a policy with empty rules and {@link
 * RpcPolicyAction#DENY} as the default action, matching the project requirement that the default
 * posture is deny-all.
 *
 * <p><b>Mandatory rules:</b> The {@code deny-pal-internals} rules (blocking all members in {@code
 * io.quasient.pal.**}) are always prepended before any user-supplied rules. This ensures that PAL
 * runtime internals can never be invoked via RPC, regardless of user policy configuration, CLI
 * flags, or hot-reload. Because rule evaluation is first-match-wins, user ALLOW rules for PAL
 * internal classes are unreachable.
 */
public class RpcPolicy {

  /**
   * Mandatory deny rules that are always prepended before user rules. These block all RPC access to
   * PAL internal packages ({@code io.quasient.pal.**}) and cannot be overridden.
   */
  private static final List<RpcPolicyRule> MANDATORY_RULES =
      RpcPolicyPresets.getDenyPalInternalRules();

  /** The ordered list of rules evaluated in first-match-wins order. */
  private final List<RpcPolicyRule> rules;

  /** The action to return when no rule matches. */
  private final RpcPolicyAction defaultAction;

  /**
   * Whether any rule in this policy has a non-null visibility constraint. When {@code false},
   * callers can skip modifiers extraction and pass {@code null} for the visibility parameter,
   * avoiding unnecessary work on the hot path.
   */
  private final boolean hasVisibilityRules;

  /**
   * Creates a policy with the given rules and default action.
   *
   * <p>The {@link RpcPolicyPresets#getDenyPalInternalRules() deny-pal-internals} rules are always
   * prepended before the given rules. This ensures PAL runtime internals are never accessible via
   * RPC, regardless of user configuration.
   *
   * @param rules the ordered list of rules (defensively copied)
   * @param defaultAction the action to return when no rule matches
   */
  public RpcPolicy(List<RpcPolicyRule> rules, RpcPolicyAction defaultAction) {
    List<RpcPolicyRule> allRules = new ArrayList<>(MANDATORY_RULES.size() + rules.size());
    allRules.addAll(MANDATORY_RULES);
    allRules.addAll(rules);
    this.rules = Collections.unmodifiableList(allRules);
    this.defaultAction = defaultAction;
    this.hasVisibilityRules = this.rules.stream().anyMatch(r -> r.getVisibilities() != null);
  }

  /**
   * Creates a deny-all policy with no rules. All operations will be denied unless rules are added.
   */
  public RpcPolicy() {
    this(List.of(), RpcPolicyAction.DENY);
  }

  /**
   * Evaluates an incoming RPC operation against this policy's rules.
   *
   * <p>Builds the match path as {@code className + "." + memberName}, then iterates the rules in
   * order. The first rule whose pattern, channel filter, member-category filter, and visibility
   * filter all match determines the action. If no rule matches, the {@link #defaultAction} is
   * returned.
   *
   * @param className the fully-qualified class name of the target (e.g. {@code "com.example.Foo"})
   * @param memberName the method or field name being accessed (e.g. {@code "bar"})
   * @param channel the message channel the operation arrived on
   * @param memberCategory the category of the member being accessed
   * @param visibility the visibility of the member being accessed, or {@code null} to skip
   *     visibility checks (appropriate when {@link #hasVisibilityRules()} is {@code false})
   * @return the action determined by the first matching rule, or the default action
   */
  public RpcPolicyAction evaluate(
      String className,
      String memberName,
      MessageChannelType channel,
      MemberCategory memberCategory,
      MemberVisibility visibility) {
    String path = className + "." + memberName;
    for (RpcPolicyRule rule : rules) {
      if (rule.matches(path, channel, memberCategory, visibility)) {
        return rule.getAction();
      }
    }
    return defaultAction;
  }

  /**
   * Evaluates an RPC operation against this policy's rules without considering the message channel.
   *
   * <p>This variant is intended for metadata serialization, where the goal is to determine if a
   * member is accessible on <em>any</em> channel. Channel-restricted rules are still matched
   * (ignoring their channel filter) so that members accessible on at least one channel appear in
   * metadata output.
   *
   * @param className the fully-qualified class name of the target
   * @param memberName the method or field name being accessed
   * @param memberCategory the category of the member being accessed
   * @param visibility the visibility of the member being accessed, or {@code null} to skip
   *     visibility checks
   * @return the action determined by the first matching rule, or the default action
   */
  public RpcPolicyAction evaluateForMetadata(
      String className,
      String memberName,
      MemberCategory memberCategory,
      MemberVisibility visibility) {
    String path = className + "." + memberName;
    for (RpcPolicyRule rule : rules) {
      if (rule.matchesForMetadata(path, memberCategory, visibility)) {
        return rule.getAction();
      }
    }
    return defaultAction;
  }

  /**
   * Returns whether the given member is accessible for metadata purposes (allowed on any channel).
   *
   * @param className the fully-qualified class name
   * @param memberName the method, field, or constructor name
   * @param memberCategory the category of the member
   * @param visibility the visibility of the member being accessed, or {@code null} to skip
   *     visibility checks
   * @return {@code true} if the policy allows access (ignoring channel)
   */
  public boolean isAccessibleForMetadata(
      String className,
      String memberName,
      MemberCategory memberCategory,
      MemberVisibility visibility) {
    RpcPolicyAction action = evaluateForMetadata(className, memberName, memberCategory, visibility);
    return action == RpcPolicyAction.ALLOW || action == RpcPolicyAction.LOG_AND_ALLOW;
  }

  /**
   * Returns the ordered, unmodifiable list of rules in this policy.
   *
   * @return the rules list
   */
  public List<RpcPolicyRule> getRules() {
    return rules;
  }

  /**
   * Returns the default action applied when no rule matches.
   *
   * @return the default action
   */
  public RpcPolicyAction getDefaultAction() {
    return defaultAction;
  }

  /**
   * Returns whether any rule in this policy has a non-null visibility constraint.
   *
   * <p>When this returns {@code false}, callers can safely pass {@code null} for the visibility
   * parameter in {@link #evaluate} and {@link #evaluateForMetadata}, avoiding unnecessary modifiers
   * extraction on the hot path.
   *
   * @return {@code true} if at least one rule filters by visibility
   */
  public boolean hasVisibilityRules() {
    return hasVisibilityRules;
  }
}
