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
package io.quasient.pal.dsl.jsonrpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

/**
 * Tests for the {@code threadAffinity} field on {@link DeferredOperation}.
 *
 * <p>Verifies that the thread affinity property defaults to {@code null} and can be set and
 * retrieved via getter/setter.
 */
public class DeferredOperationThreadAffinityTest {

  /**
   * Verifies that thread affinity defaults to {@code null} on newly created operations.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinityDefaultIsNull]
   */
  @Test
  public void threadAffinityDefaultIsNull() {
    DeferredOperation op = DeferredOperation.staticMethod("Foo", "bar", null, null);
    assertThat(op.getThreadAffinity(), is(nullValue()));
  }

  /**
   * Verifies that thread affinity can be set and retrieved.
   *
   * <p>Acceptance criterion: [TEST:DeferredOperationThreadAffinityTest.threadAffinitySetAndGet]
   */
  @Test
  public void threadAffinitySetAndGet() {
    DeferredOperation op = DeferredOperation.newInstance("Foo", "var", null);
    op.setThreadAffinity("fx-thread");
    assertThat(op.getThreadAffinity(), is("fx-thread"));
  }
}
