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
import org.junit.Test;

/**
 * Unit tests for {@code ReplayPolicyRule} — individual rule model with Ant-style pattern matching
 * for class and method names. Rules are used by {@link ReplayPolicy} to determine what replay
 * action to take for a given operation.
 */
public class ReplayPolicyRuleTest {

  /**
   * Verifies that an exact class and method pattern matches the corresponding fully-qualified
   * class.method path.
   */
  @Test
  public void exactClassAndMethodMatch() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "bar", ReplayAction.STUB_FROM_WAL);

    assertThat(rule.matches("com.example.Foo.bar", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /**
   * Verifies that a wildcard method pattern ("**") matches any method name within the specified
   * class.
   */
  @Test
  public void wildcardMethodMatch() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "**", ReplayAction.STUB_FROM_WAL);

    assertThat(
        rule.matches("com.example.Foo.anyMethod", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /**
   * Verifies that a wildcard class pattern ("java.io.**") matches any class within the package
   * hierarchy and any method.
   */
  @Test
  public void wildcardClassMatch() {
    ReplayPolicyRule rule = new ReplayPolicyRule("java.io.**", "**", ReplayAction.STUB_FROM_WAL);

    assertThat(
        rule.matches("java.io.InputStream.read", MessageType.EXEC_INSTANCE_METHOD), is(true));
  }

  /** Verifies that a rule returns false when the class and method do not match the patterns. */
  @Test
  public void noMatchReturnsFalse() {
    ReplayPolicyRule rule =
        new ReplayPolicyRule("com.example.Foo", "bar", ReplayAction.STUB_FROM_WAL);

    assertThat(rule.matches("com.other.Baz.qux", MessageType.EXEC_INSTANCE_METHOD), is(false));
  }
}
