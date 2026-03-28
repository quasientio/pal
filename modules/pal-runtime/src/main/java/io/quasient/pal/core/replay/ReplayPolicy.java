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
package io.quasient.pal.core.replay;

import io.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;

/**
 * Strategy object that determines what replay action to take for each operation during
 * deterministic WAL replay.
 *
 * <p>The policy evaluates an ordered list of {@link ReplayPolicyRule} instances using first-match
 * semantics. If no rule matches, the configured default action is returned.
 *
 * <p>The no-arg constructor preserves backward compatibility by defaulting to an empty rule list
 * with {@link ReplayAction#RE_EXECUTE} as the default action.
 */
public class ReplayPolicy {

  /** The action the replay system should take for a given operation. */
  public enum ReplayAction {
    /** Execute the operation live and verify the return value against the WAL. */
    RE_EXECUTE,

    /** Execute the operation live without verifying against the WAL. */
    RE_EXECUTE_UNCHECKED,

    /** Return the WAL-recorded value without executing the operation. */
    STUB_FROM_WAL,

    /** Return the WAL-recorded value and verify that the arguments match the WAL. */
    STUB_FROM_WAL_VERIFIED,

    /** Return the WAL-recorded value and replay field mutations from within the span. */
    STUB_WITH_SIDE_EFFECTS
  }

  /** The ordered list of rules evaluated in first-match-wins order. */
  private final List<ReplayPolicyRule> rules;

  /** The fallback action when no rule matches. */
  private final ReplayAction defaultAction;

  /** Creates a replay policy with default behavior (no rules, {@link ReplayAction#RE_EXECUTE}). */
  public ReplayPolicy() {
    this(Collections.emptyList(), ReplayAction.RE_EXECUTE);
  }

  /**
   * Creates a replay policy with the given rules and default action.
   *
   * @param rules the ordered list of rules (first match wins)
   * @param defaultAction the action to return when no rule matches
   */
  public ReplayPolicy(List<ReplayPolicyRule> rules, ReplayAction defaultAction) {
    this.rules = List.copyOf(rules);
    this.defaultAction = defaultAction;
  }

  /**
   * Determines the replay action for the given operation.
   *
   * <p>Rules are evaluated in order; the first matching rule's action is returned. If no rule
   * matches, the default action is returned.
   *
   * @param className the fully qualified class name of the operation target
   * @param methodName the method, constructor, or field name
   * @param messageType the EXEC message type classifying the operation
   * @return the resolved replay action
   */
  public ReplayAction getAction(String className, String methodName, MessageType messageType) {
    String path = className + "." + methodName;
    for (ReplayPolicyRule rule : rules) {
      if (rule.matches(path, messageType)) {
        return rule.getAction();
      }
    }
    return defaultAction;
  }

  /**
   * Returns the ordered list of rules in this policy.
   *
   * @return an unmodifiable list of rules
   */
  public List<ReplayPolicyRule> getRules() {
    return rules;
  }

  /**
   * Returns the default action used when no rule matches.
   *
   * @return the default replay action
   */
  public ReplayAction getDefaultAction() {
    return defaultAction;
  }
}
