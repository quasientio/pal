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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.ExecMessageUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton service that performs access checks on incoming RPC messages against an {@link
 * RpcPolicy} obtained from an {@link RpcPolicyHolder}.
 *
 * <p>This checker bridges the dispatch system and the policy engine. For each incoming execution
 * message, it extracts the class name, member name, and member category, then delegates to the
 * policy for an access decision. Operations arriving via the {@link
 * MessageChannelType#REPLAY_INJECTION} channel are exempt from policy checks because they represent
 * previously-executed operations being replayed.
 *
 * <p>The policy is read indirectly through an {@link RpcPolicyHolder}, which supports volatile swap
 * for hot-reload. Each access check reads the current policy snapshot via {@link
 * RpcPolicyHolder#getPolicy()}, ensuring that policy updates are picked up without restart.
 *
 * <p>The {@link #isAccessible(String, String, MessageChannelType, MemberCategory)} method provides
 * a boolean-returning variant for use by metadata serializers that need to filter which members are
 * advertised to RPC clients.
 */
@Singleton
public class RpcPolicyChecker {

  /** Logger for access-decision auditing. */
  private static final Logger logger = LoggerFactory.getLogger(RpcPolicyChecker.class);

  /** The holder providing the current policy to evaluate incoming operations against. */
  private final RpcPolicyHolder policyHolder;

  /**
   * The classloader for resolving application classes when modifiers fallback is needed. In
   * production this is the {@link CustomClassloader} injected by Guice; in tests it may be {@code
   * null}, in which case the fallback uses the current thread's context classloader.
   */
  private final ClassLoader appClassLoader;

  /**
   * Creates a new checker backed by the given policy holder with the application classloader for
   * visibility resolution.
   *
   * @param policyHolder the holder providing the current RPC policy
   * @param appClassLoader the classloader for loading application classes during modifiers fallback
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Classloader is intentionally shared, not a mutable data object")
  @Inject
  public RpcPolicyChecker(RpcPolicyHolder policyHolder, CustomClassloader appClassLoader) {
    this.policyHolder = policyHolder;
    this.appClassLoader = appClassLoader;
  }

  /**
   * Creates a new checker backed by the given policy without an explicit application classloader.
   * Used in unit tests where the modifiers fallback resolution is not needed. The policy is wrapped
   * in a new {@link RpcPolicyHolder} for backward compatibility.
   *
   * @param policy the RPC policy to evaluate operations against
   */
  public RpcPolicyChecker(RpcPolicy policy) {
    this.policyHolder = new RpcPolicyHolder(policy);
    this.appClassLoader = null;
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

    MemberVisibility visibility = null;
    if (policyHolder.hasVisibilityRules()) {
      int modifiers = ExecMessageUtils.getModifiers(msg);
      if (modifiers == 0) {
        modifiers = resolveModifiersViaReflection(className, memberName, category);
      }
      if (modifiers < 0) {
        // Class not on classpath — skip visibility-based policy evaluation.
        // Non-visibility rules (pattern-based deny-unsafe, deny-jdk-internals, etc.) still
        // apply below. The dispatch layer will produce the real ClassNotFoundException.
        logger.warn(
            "Class not found on classpath during policy evaluation: {}.{} — "
                + "skipping visibility check (deny-nonpublic will not apply)",
            className,
            memberName);
      } else {
        visibility = MemberVisibility.fromModifiers(modifiers);
      }
    }

    RpcPolicyAction action =
        policyHolder.getPolicy().evaluate(className, memberName, channel, category, visibility);

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
    RpcPolicyAction action =
        policyHolder.getPolicy().evaluate(className, memberName, channel, category, visibility);
    return action == RpcPolicyAction.ALLOW || action == RpcPolicyAction.LOG_AND_ALLOW;
  }

  /**
   * Resolves the Java modifiers of a class member via reflection, used as a fallback when the
   * incoming message does not carry modifiers (e.g. from non-PAL clients like ThinPeer or JSON-RPC
   * WebSocket).
   *
   * <p>When the modifiers field in an {@link ExecMessage} is zero, it is ambiguous: it may indicate
   * a genuinely package-private member with no other modifier bits, or it may indicate that the
   * sender simply did not populate the field. This method resolves the ambiguity by loading the
   * class and querying the actual member modifiers via the Java reflection API.
   *
   * <p>For overloaded methods, this returns the modifiers of the first declared method with the
   * matching name. This is acceptable because all overloads of a method typically share the same
   * visibility.
   *
   * @param className the fully-qualified class name
   * @param memberName the method, constructor ({@code "<init>"}), or field name
   * @param category the member category determining whether to search methods, constructors, or
   *     fields
   * @return the resolved modifiers, {@code 0} if the member cannot be found on an existing class,
   *     or {@code -1} if the class itself cannot be loaded (signals the caller to skip
   *     visibility-based policy evaluation)
   */
  private int resolveModifiersViaReflection(
      String className, String memberName, MemberCategory category) {
    ClassLoader cl = appClassLoader;
    if (cl == null) {
      cl = Thread.currentThread().getContextClassLoader();
    }
    if (cl == null) {
      cl = RpcPolicyChecker.class.getClassLoader();
    }
    try {
      Class<?> clazz = Class.forName(className, false, cl);
      switch (category) {
        case CONSTRUCTOR -> {
          for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            return c.getModifiers();
          }
        }
        case METHOD, STATIC_METHOD -> {
          for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(memberName)) {
              return m.getModifiers();
            }
          }
        }
        case FIELD_GET, FIELD_SET -> {
          for (Field f : clazz.getDeclaredFields()) {
            if (f.getName().equals(memberName)) {
              return f.getModifiers();
            }
          }
        }
      }
    } catch (ClassNotFoundException e) {
      logger.debug("Cannot resolve modifiers for {}.{}: class not found", className, memberName);
      return -1;
    }
    return 0;
  }
}
