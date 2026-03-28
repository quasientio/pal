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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.messages.types.MessageType;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayPolicy} — the action resolution component that determines what replay
 * action to take for each operation. Tests cover both the backward-compatible no-arg constructor
 * and the configurable rules-based constructor.
 */
public class ReplayPolicyTest {

  /** Verifies that the default policy always returns RE_EXECUTE regardless of input. */
  @Test
  public void alwaysReturnsReExecute() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("com.example.Foo", "bar", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
  }

  /** Verifies that RE_EXECUTE is returned for constructor operations. */
  @Test
  public void reExecuteForConstructor() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("Foo", "new", MessageType.EXEC_CONSTRUCTOR), is(ReplayAction.RE_EXECUTE));
  }

  /** Verifies that RE_EXECUTE is returned for static method operations. */
  @Test
  public void reExecuteForStaticMethod() {
    ReplayPolicy policy = new ReplayPolicy();
    assertThat(
        policy.getAction("Foo", "bar", MessageType.EXEC_CLASS_METHOD), is(ReplayAction.RE_EXECUTE));
  }

  /** Verifies that when multiple rules match, the first matching rule wins. */
  @Test
  public void firstMatchingRuleWins() {
    ReplayPolicy policy =
        new ReplayPolicy(
            List.of(
                new ReplayPolicyRule("com.example.Foo", "bar", ReplayAction.STUB_FROM_WAL),
                new ReplayPolicyRule("com.example.Foo", "**", ReplayAction.RE_EXECUTE)),
            ReplayAction.RE_EXECUTE);

    assertThat(
        policy.getAction("com.example.Foo", "bar", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
  }

  /** Verifies that the default action is returned when no rule matches the given operation. */
  @Test
  public void defaultActionWhenNoRuleMatches() {
    ReplayPolicy policy =
        new ReplayPolicy(
            List.of(new ReplayPolicyRule("com.example.**", "**", ReplayAction.STUB_FROM_WAL)),
            ReplayAction.RE_EXECUTE);

    assertThat(
        policy.getAction("com.other.Bar", "baz", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
  }

  /** Verifies that a configured default action is used when no rules match. */
  @Test
  public void configuredDefaultActionUsed() {
    ReplayPolicy policy = new ReplayPolicy(List.of(), ReplayAction.STUB_FROM_WAL);

    assertThat(
        policy.getAction("com.any.Class", "method", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_FROM_WAL));
  }

  /** Verifies that the no-arg constructor preserves backward-compatible behavior (RE_EXECUTE). */
  @Test
  public void defaultConstructorPreservesBackwardCompatibility() {
    ReplayPolicy policy = new ReplayPolicy();

    assertThat(
        policy.getAction("com.example.Foo", "bar", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.RE_EXECUTE));
    assertThat(
        policy.getAction("java.lang.System", "currentTimeMillis", MessageType.EXEC_CLASS_METHOD),
        is(ReplayAction.RE_EXECUTE));
  }

  /** Verifies that a rule with STUB_WITH_SIDE_EFFECTS action is correctly resolved. */
  @Test
  public void stubWithSideEffectsAction() {
    ReplayPolicy policy =
        new ReplayPolicy(
            List.of(
                new ReplayPolicyRule(
                    "com.example.Enricher", "**", ReplayAction.STUB_WITH_SIDE_EFFECTS)),
            ReplayAction.RE_EXECUTE);

    assertThat(
        policy.getAction("com.example.Enricher", "enrich", MessageType.EXEC_INSTANCE_METHOD),
        is(ReplayAction.STUB_WITH_SIDE_EFFECTS));
  }
}
