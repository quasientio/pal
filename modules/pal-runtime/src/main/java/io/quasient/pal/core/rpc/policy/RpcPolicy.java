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
 */
public class RpcPolicy {

  /** The ordered list of rules evaluated in first-match-wins order. */
  private final List<RpcPolicyRule> rules;

  /** The action to return when no rule matches. */
  private final RpcPolicyAction defaultAction;

  /**
   * Creates a policy with the given rules and default action.
   *
   * @param rules the ordered list of rules (defensively copied)
   * @param defaultAction the action to return when no rule matches
   */
  public RpcPolicy(List<RpcPolicyRule> rules, RpcPolicyAction defaultAction) {
    this.rules = Collections.unmodifiableList(List.copyOf(rules));
    this.defaultAction = defaultAction;
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
   * order. The first rule whose pattern, channel filter, and member-category filter all match
   * determines the action. If no rule matches, the {@link #defaultAction} is returned.
   *
   * @param className the fully-qualified class name of the target (e.g. {@code "com.example.Foo"})
   * @param memberName the method or field name being accessed (e.g. {@code "bar"})
   * @param channel the message channel the operation arrived on
   * @param memberCategory the category of the member being accessed
   * @return the action determined by the first matching rule, or the default action
   */
  public RpcPolicyAction evaluate(
      String className,
      String memberName,
      MessageChannelType channel,
      MemberCategory memberCategory) {
    String path = className + "." + memberName;
    for (RpcPolicyRule rule : rules) {
      if (rule.matches(path, channel, memberCategory)) {
        return rule.getAction();
      }
    }
    return defaultAction;
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
}
