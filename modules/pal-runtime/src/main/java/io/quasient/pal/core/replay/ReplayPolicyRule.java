/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.messages.types.MessageType;

/**
 * An immutable rule that maps an Ant-style class/method pattern to a {@link ReplayAction}.
 *
 * <p>Rules are evaluated by {@link ReplayPolicy} in order; the first matching rule wins. Patterns
 * use dot-separated segments with {@code *} (single segment) and {@code **} (multi-segment)
 * wildcards, matching the same syntax used in {@link
 * io.quasient.pal.core.intercept.InterceptRequestEntry}.
 *
 * <p>The full matching pattern is constructed as {@code classPattern + "." + methodPattern}, where
 * {@code methodPattern} defaults to {@code "**"} if not provided.
 */
public class ReplayPolicyRule {

  /**
   * Matcher for comparing dot-separated paths, configured to trim tokens and ignore case.
   *
   * <p><b>Thread safety:</b> This shared static instance is safe for concurrent use. {@code
   * AntPathMatcherArrays} (v1.0.0) is an immutable, effectively thread-safe class: all instance
   * fields are {@code final}, there is no mutable state, and {@code isMatch()} is a pure function.
   */
  private static final AntPathMatcherArrays MATCHER =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  /** The Ant-style pattern for matching the fully-qualified class name. */
  private final String classPattern;

  /** The Ant-style pattern for matching the method or field name. */
  private final String methodPattern;

  /** The replay action to apply when this rule matches. */
  private final ReplayAction action;

  /** The pre-computed full pattern combining class and method patterns. */
  private final String fullPattern;

  /**
   * Creates a new replay policy rule.
   *
   * @param classPattern Ant-style pattern for matching class names (e.g. {@code "java.io.**"})
   * @param methodPattern Ant-style pattern for matching method names, or {@code null} to default to
   *     {@code "**"}
   * @param action the replay action to apply when this rule matches
   */
  public ReplayPolicyRule(String classPattern, String methodPattern, ReplayAction action) {
    this.classPattern = classPattern;
    this.methodPattern = methodPattern != null ? methodPattern : "**";
    this.action = action;
    this.fullPattern = this.classPattern + "." + this.methodPattern;
  }

  /**
   * Tests whether this rule matches the given class-method path.
   *
   * @param classMethodPath the fully-qualified path in the form {@code "com.example.Foo.bar"}
   * @param messageType the EXEC message type (currently unused, reserved for future filtering)
   * @return {@code true} if the path matches this rule's pattern
   */
  public boolean matches(String classMethodPath, MessageType messageType) {
    return MATCHER.isMatch(fullPattern, classMethodPath);
  }

  /**
   * Returns the replay action associated with this rule.
   *
   * @return the replay action
   */
  public ReplayAction getAction() {
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
   * Returns the method pattern for this rule.
   *
   * @return the method pattern
   */
  public String getMethodPattern() {
    return methodPattern;
  }

  /**
   * Returns the pre-computed full pattern combining class and method patterns.
   *
   * @return the full pattern
   */
  public String getFullPattern() {
    return fullPattern;
  }
}
