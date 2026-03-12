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

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.core.transport.MessageChannelType;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable rule that matches an incoming RPC operation against an Ant-style class/method
 * pattern, optional channel filter, and optional member-category filter.
 *
 * <p>Rules are evaluated by the RPC policy in order; the first matching rule wins. Patterns use
 * dot-separated segments with {@code *} (single segment) and {@code **} (multi-segment) wildcards,
 * matching the same syntax used in {@link io.quasient.pal.core.replay.ReplayPolicyRule}.
 *
 * <p>The full matching pattern is constructed as {@code classPattern + "." + memberPattern}, where
 * {@code memberPattern} defaults to {@code "**"} if not provided.
 *
 * <p>Matching is multi-dimensional: the pattern must match the class-method path, the channel must
 * be in the allowed set (if specified), and the member category must be in the allowed set (if
 * specified). All three dimensions must pass for the rule to match.
 */
public class RpcPolicyRule {

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

  /** The Ant-style pattern for matching the method or field name. Defaults to {@code "**"}. */
  private final String memberPattern;

  /** The pre-computed full pattern combining class and member patterns. */
  private final String fullPattern;

  /** The action to apply when this rule matches. */
  private final RpcPolicyAction action;

  /**
   * The set of channels this rule applies to, or {@code null} to match all channels.
   *
   * <p>When non-null, only operations arriving on one of the specified channels will match.
   */
  private final Set<MessageChannelType> channels;

  /**
   * The set of member categories this rule applies to, or {@code null} to match all categories.
   *
   * <p>When non-null, only operations targeting one of the specified member categories will match.
   */
  private final Set<MemberCategory> members;

  /**
   * The set of member visibilities this rule applies to, or {@code null} to match all visibilities.
   *
   * <p>When non-null, only operations targeting a member with one of the specified visibility
   * levels will match. This enables rules like "only allow public methods" or "deny private
   * fields."
   */
  private final Set<MemberVisibility> visibilities;

  /**
   * Creates a new RPC policy rule.
   *
   * @param classPattern Ant-style pattern for matching class names (e.g. {@code "com.example.**"})
   * @param memberPattern Ant-style pattern for matching member names, or {@code null} to default to
   *     {@code "**"}
   * @param action the action to apply when this rule matches
   * @param channels the set of channels this rule applies to, or {@code null} to match all channels
   * @param members the set of member categories this rule applies to, or {@code null} to match all
   *     categories
   * @param visibilities the set of member visibilities this rule applies to, or {@code null} to
   *     match all visibilities
   */
  public RpcPolicyRule(
      String classPattern,
      String memberPattern,
      RpcPolicyAction action,
      Set<MessageChannelType> channels,
      Set<MemberCategory> members,
      Set<MemberVisibility> visibilities) {
    this.classPattern = classPattern;
    this.memberPattern = memberPattern != null ? memberPattern : "**";
    this.action = action;
    this.channels = channels != null ? EnumSet.copyOf(channels) : null;
    this.members = members != null ? EnumSet.copyOf(members) : null;
    this.visibilities = visibilities != null ? EnumSet.copyOf(visibilities) : null;
    this.fullPattern = this.classPattern + "." + this.memberPattern;
  }

  /**
   * Tests whether this rule matches the given class-method path, channel, member category, and
   * visibility.
   *
   * <p>Matching is multi-dimensional: the channel filter is checked first (short-circuit), then the
   * member-category filter, then the visibility filter, and finally the Ant-style pattern match on
   * the full path.
   *
   * @param classMethodPath the fully-qualified path in the form {@code "com.example.Foo.bar"}
   * @param channel the message channel the operation arrived on
   * @param memberCategory the category of the member being accessed
   * @param visibility the visibility of the member being accessed, or {@code null} to skip the
   *     visibility check
   * @return {@code true} if the operation matches this rule
   */
  public boolean matches(
      String classMethodPath,
      MessageChannelType channel,
      MemberCategory memberCategory,
      MemberVisibility visibility) {
    if (channels != null && !channels.contains(channel)) {
      return false;
    }
    if (members != null && !members.contains(memberCategory)) {
      return false;
    }
    if (visibility != null && visibilities != null && !visibilities.contains(visibility)) {
      return false;
    }
    return MATCHER.isMatch(fullPattern, classMethodPath);
  }

  /**
   * Returns the action associated with this rule.
   *
   * @return the RPC policy action
   */
  public RpcPolicyAction getAction() {
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
   * Returns the pre-computed full pattern combining class and member patterns.
   *
   * @return the full pattern
   */
  public String getFullPattern() {
    return fullPattern;
  }

  /**
   * Returns the set of channels this rule applies to, or {@code null} for all channels.
   *
   * @return the channel set, or {@code null}
   */
  public Set<MessageChannelType> getChannels() {
    return channels != null ? EnumSet.copyOf(channels) : null;
  }

  /**
   * Returns the set of member categories this rule applies to, or {@code null} for all categories.
   *
   * @return the member category set, or {@code null}
   */
  public Set<MemberCategory> getMembers() {
    return members != null ? EnumSet.copyOf(members) : null;
  }

  /**
   * Returns the set of member visibilities this rule applies to, or {@code null} for all
   * visibilities.
   *
   * @return the visibility set, or {@code null}
   */
  public Set<MemberVisibility> getVisibilities() {
    return visibilities != null ? EnumSet.copyOf(visibilities) : null;
  }

  /**
   * Tests whether this rule matches the given class-method path, member category, and visibility,
   * ignoring the channel dimension.
   *
   * <p>This variant is intended for metadata serialization, where the goal is to determine if a
   * member is accessible on <em>any</em> channel. Channel-restricted rules are still evaluated
   * (they match regardless of the actual channel).
   *
   * @param classMethodPath the fully-qualified path in the form {@code "com.example.Foo.bar"}
   * @param memberCategory the category of the member being accessed
   * @param visibility the visibility of the member being accessed, or {@code null} to skip the
   *     visibility check
   * @return {@code true} if the operation matches this rule (ignoring channel)
   */
  public boolean matchesForMetadata(
      String classMethodPath, MemberCategory memberCategory, MemberVisibility visibility) {
    if (members != null && !members.contains(memberCategory)) {
      return false;
    }
    if (visibility != null && visibilities != null && !visibilities.contains(visibility)) {
      return false;
    }
    return MATCHER.isMatch(fullPattern, classMethodPath);
  }
}
