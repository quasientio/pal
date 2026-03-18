/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core evaluator that determines whether an operation is in recording scope, controlling whether it
 * is written to the WAL and published via PUB.
 *
 * <p>Holds an ordered list of {@link RecordingScopeRule} instances and a configurable default
 * {@link RecordingScopeAction}. Evaluation uses first-match-wins semantics modeled after {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicy}:
 *
 * <ol>
 *   <li>Build the match path as {@code className + "." + memberName}.
 *   <li>Iterate through rules in order; the first matching rule's action wins.
 *   <li>If no rule matches, return the {@link #defaultAction}.
 * </ol>
 *
 * <p>Results are cached in a {@link ConcurrentHashMap} keyed on {@code className + "." + memberName
 * + "#" + category.name()}, so each distinct operation is evaluated at most once. The cache key
 * includes the {@link MemberCategory} to differentiate field operations from method calls on the
 * same class and member name.
 *
 * <p><b>Thread safety:</b> This class is safe for concurrent use from multiple threads. The {@code
 * ConcurrentHashMap.computeIfAbsent()} call is atomic and lock-free for existing entries, matching
 * the performance characteristics of other PAL caches.
 *
 * <p><b>Backward compatibility:</b> When no recording scope is configured, the dispatcher holds a
 * {@code null} reference. The contract {@code recordingScope == null ||
 * recordingScope.isInScope(...)} ensures all operations are recorded by default.
 *
 * @see RecordingScopeRule
 * @see RecordingScopeAction
 */
public class RecordingScope {

  /** The ordered list of rules evaluated in first-match-wins order. */
  private final List<RecordingScopeRule> rules;

  /** The action to return when no rule matches. */
  private final RecordingScopeAction defaultAction;

  /** Cache of evaluation results, keyed on {@code className.memberName#category}. */
  private final ConcurrentHashMap<String, RecordingScopeAction> cache = new ConcurrentHashMap<>();

  /**
   * Creates a new recording scope with the given rules and default action.
   *
   * @param rules the ordered list of rules (defensively copied to an unmodifiable list)
   * @param defaultAction the action to return when no rule matches
   */
  public RecordingScope(List<RecordingScopeRule> rules, RecordingScopeAction defaultAction) {
    this.rules = Collections.unmodifiableList(List.copyOf(rules));
    this.defaultAction = defaultAction;
  }

  /**
   * Evaluates whether an operation is in recording scope.
   *
   * <p>The result is cached per unique combination of class name, member name, and member category.
   * The first call for a given combination evaluates all rules; subsequent calls return the cached
   * result in O(1).
   *
   * @param className the fully-qualified class name (e.g. {@code "com.example.Foo"})
   * @param memberName the method name, {@code "new"} for constructors, or the field name
   * @param category the operation type (METHOD, FIELD_GET, etc.)
   * @return {@code true} if the operation should be recorded to WAL/PUB
   */
  public boolean isInScope(String className, String memberName, MemberCategory category) {
    String cacheKey = className + "." + memberName + "#" + category.name();
    return cache.computeIfAbsent(cacheKey, k -> evaluate(className, memberName, category))
        == RecordingScopeAction.RECORD;
  }

  /**
   * Evaluates the rules for the given operation. Called at most once per distinct cache key.
   *
   * @param className the fully-qualified class name
   * @param memberName the method, constructor, or field name
   * @param category the operation category
   * @return the action from the first matching rule, or the default action
   */
  private RecordingScopeAction evaluate(
      String className, String memberName, MemberCategory category) {
    for (RecordingScopeRule rule : rules) {
      if (rule.matches(className, memberName, category)) {
        return rule.getAction();
      }
    }
    return defaultAction;
  }

  /**
   * Returns the ordered, unmodifiable list of rules in this scope.
   *
   * @return the rules list
   */
  public List<RecordingScopeRule> getRules() {
    return rules;
  }

  /**
   * Returns the default action applied when no rule matches.
   *
   * @return the default action
   */
  public RecordingScopeAction getDefaultAction() {
    return defaultAction;
  }
}
