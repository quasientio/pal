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

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable rule that matches an operation against an Ant-style class/member pattern, a {@link
 * RecordingScopeAction}, and an optional {@link MemberCategory} filter set.
 *
 * <p>Rules are evaluated by {@link RecordingScope} in order; the first matching rule wins. Patterns
 * use dot-separated segments with {@code *} (single segment) and {@code **} (multi-segment)
 * wildcards, following the same conventions as {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicyRule}.
 *
 * <p>The {@code memberPattern} defaults to {@code "**"} if not provided.
 *
 * <p>Matching is multi-dimensional: the category filter is checked first (short-circuit), then the
 * class pattern is matched against the class name and the member pattern is matched against the
 * member name independently. This ensures that single-segment wildcards ({@code *}) in the class
 * pattern do not accidentally cross the class-member boundary. All dimensions must pass for the
 * rule to match.
 *
 * @see RecordingScope
 * @see RecordingScopeAction
 */
public class RecordingScopeRule {

  /**
   * Matcher for comparing dot-separated paths, configured to trim tokens and ignore case.
   *
   * <p><b>Thread safety:</b> This shared static instance is safe for concurrent use. {@code
   * AntPathMatcherArrays} (v1.0.0) is an immutable, effectively thread-safe class: all instance
   * fields are {@code final}, there is no mutable state, and {@code isMatch()} is a pure function.
   */
  static final AntPathMatcherArrays MATCHER =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  /** The Ant-style pattern for matching the fully-qualified class name. */
  private final String classPattern;

  /** The Ant-style pattern for matching the method, constructor, or field name. */
  private final String memberPattern;

  /** The action to apply when this rule matches. */
  private final RecordingScopeAction action;

  /**
   * The set of member categories this rule applies to, or {@code null} to match all categories.
   *
   * <p>When non-null, only operations targeting one of the specified member categories will match.
   * This enables rules like "skip all field reads on JDK types" or "record only constructors."
   */
  private final Set<MemberCategory> categories;

  /**
   * Creates a new recording scope rule.
   *
   * @param classPattern Ant-style pattern for matching class names (e.g. {@code "com.example.**"})
   * @param memberPattern Ant-style pattern for matching member names, or {@code null} to default to
   *     {@code "**"}
   * @param action the action to apply when this rule matches
   * @param categories the set of member categories this rule applies to, or {@code null} to match
   *     all categories
   */
  public RecordingScopeRule(
      String classPattern,
      String memberPattern,
      RecordingScopeAction action,
      Set<MemberCategory> categories) {
    this.classPattern = classPattern;
    this.memberPattern = memberPattern != null ? memberPattern : "**";
    this.action = action;
    this.categories = categories != null ? EnumSet.copyOf(categories) : null;
  }

  /**
   * Tests whether this rule matches the given class name, member name, and member category.
   *
   * <p>Matching is multi-dimensional: the category filter is checked first (short-circuit), then
   * the class pattern is matched against the class name and the member pattern against the member
   * name independently. Matching class and member separately ensures that single-segment wildcards
   * ({@code *}) in the class pattern cannot absorb member segments, and vice versa.
   *
   * @param className the fully-qualified class name (e.g. {@code "com.example.Foo"})
   * @param memberName the method name, {@code "new"} for constructors, or the field name
   * @param category the category of the member being accessed
   * @return {@code true} if the operation matches this rule
   */
  public boolean matches(String className, String memberName, MemberCategory category) {
    if (categories != null && !categories.contains(category)) {
      return false;
    }
    return MATCHER.isMatch(classPattern, className) && MATCHER.isMatch(memberPattern, memberName);
  }

  /**
   * Returns the action associated with this rule.
   *
   * @return the recording scope action
   */
  public RecordingScopeAction getAction() {
    return action;
  }

  /**
   * Returns the class pattern for this rule.
   *
   * @return the class pattern
   */
  public String getClassPattern() {
    return classPattern;
  }

  /**
   * Returns the member pattern for this rule.
   *
   * @return the member pattern
   */
  public String getMemberPattern() {
    return memberPattern;
  }

  /**
   * Returns the set of member categories this rule applies to, or {@code null} for all categories.
   *
   * @return the category set, or {@code null}
   */
  public Set<MemberCategory> getCategories() {
    return categories != null ? EnumSet.copyOf(categories) : null;
  }
}
