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
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton service that performs access checks on incoming RPC messages against an {@link
 * RpcPolicy}.
 *
 * <p>This checker bridges the dispatch system and the policy engine. For each incoming execution
 * message, it extracts the class name, member name, and member category, then delegates to the
 * policy for an access decision. Operations arriving via the {@link
 * MessageChannelType#REPLAY_INJECTION} channel are exempt from policy checks because they represent
 * previously-executed operations being replayed.
 *
 * <p>The {@link #isAccessible(String, String, MessageChannelType, MemberCategory)} method provides
 * a boolean-returning variant for use by metadata serializers that need to filter which members are
 * advertised to RPC clients.
 */
@Singleton
public class RpcPolicyChecker {

  /** Logger for access-decision auditing. */
  private static final Logger logger = LoggerFactory.getLogger(RpcPolicyChecker.class);

  /** The policy to evaluate incoming operations against. */
  private final RpcPolicy policy;

  /**
   * Creates a new checker backed by the given policy.
   *
   * @param policy the RPC policy to evaluate operations against
   */
  @Inject
  public RpcPolicyChecker(RpcPolicy policy) {
    this.policy = policy;
  }

  /**
   * Checks whether an incoming execution message is allowed by the policy.
   *
   * <p>Operations on the {@link MessageChannelType#REPLAY_INJECTION} channel are always permitted
   * without consulting the policy, since they represent replayed operations that already executed.
   * Similarly, {@link MessageChannelType#CLI_RPC} operations are exempt because they represent the
   * peer invoking its own main class — a local operation, not a remote call.
   *
   * <p>For all other channels, the checker extracts the class name and member name from the message
   * via {@link ExecMessageUtils}, maps the {@link MessageType} to a {@link MemberCategory}, and
   * evaluates the policy. Based on the resulting action:
   *
   * <ul>
   *   <li>{@link RpcPolicyAction#ALLOW} — returns silently
   *   <li>{@link RpcPolicyAction#DENY} — throws {@link RpcAccessDeniedException}
   *   <li>{@link RpcPolicyAction#LOG_AND_ALLOW} — logs at INFO level, then returns
   *   <li>{@link RpcPolicyAction#LOG_AND_DENY} — logs at WARN level, then throws
   * </ul>
   *
   * @param msg the incoming execution message to check
   * @param type the message type (determines the member category)
   * @param channel the channel the message arrived on
   * @throws RpcAccessDeniedException if the policy denies access
   */
  public void checkAccess(ExecMessage msg, MessageType type, MessageChannelType channel) {
    if (channel == MessageChannelType.REPLAY_INJECTION || channel == MessageChannelType.CLI_RPC) {
      return;
    }

    String className = ExecMessageUtils.getClassname(msg);
    String memberName = ExecMessageUtils.getExecutableName(msg);
    MemberCategory category = MemberCategory.fromMessageType(type);

    MemberVisibility visibility =
        policy.hasVisibilityRules()
            ? MemberVisibility.fromModifiers(ExecMessageUtils.getModifiers(msg))
            : null;

    RpcPolicyAction action = policy.evaluate(className, memberName, channel, category, visibility);

    switch (action) {
      case ALLOW -> {}
      case DENY -> throw new RpcAccessDeniedException(className, memberName, channel);
      case LOG_AND_ALLOW ->
          logger.info("RPC access allowed: {}.{} via {}", className, memberName, channel);
      case LOG_AND_DENY -> {
        logger.warn("RPC access denied: {}.{} via {} [logged]", className, memberName, channel);
        throw new RpcAccessDeniedException(className, memberName, channel);
      }
    }
  }

  /**
   * Returns whether the given class member is accessible under the current policy for the specified
   * channel and category.
   *
   * <p>This method is intended for use by metadata serializers (e.g. {@code
   * ClassMetadataSerializer}) that need to filter which members are advertised to RPC clients, so
   * that clients never discover methods they cannot call.
   *
   * @param className the fully-qualified class name (e.g. {@code "com.example.Foo"})
   * @param memberName the method or field name (e.g. {@code "bar"})
   * @param channel the message channel to evaluate against
   * @param category the member category to evaluate against
   * @return {@code true} if the policy allows access, {@code false} if it denies
   */
  public boolean isAccessible(
      String className, String memberName, MessageChannelType channel, MemberCategory category) {
    return isAccessible(className, memberName, channel, category, null);
  }

  /**
   * Returns whether the given class member is accessible under the current policy for the specified
   * channel, category, and visibility.
   *
   * <p>This variant accepts a {@link MemberVisibility} parameter for callers that have modifiers
   * available (e.g. from ClassGraph or reflection). When {@code visibility} is {@code null}, the
   * visibility dimension is not checked.
   *
   * @param className the fully-qualified class name (e.g. {@code "com.example.Foo"})
   * @param memberName the method or field name (e.g. {@code "bar"})
   * @param channel the message channel to evaluate against
   * @param category the member category to evaluate against
   * @param visibility the visibility of the member, or {@code null} to skip visibility checks
   * @return {@code true} if the policy allows access, {@code false} if it denies
   */
  public boolean isAccessible(
      String className,
      String memberName,
      MessageChannelType channel,
      MemberCategory category,
      MemberVisibility visibility) {
    RpcPolicyAction action = policy.evaluate(className, memberName, channel, category, visibility);
    return action == RpcPolicyAction.ALLOW || action == RpcPolicyAction.LOG_AND_ALLOW;
  }
}
