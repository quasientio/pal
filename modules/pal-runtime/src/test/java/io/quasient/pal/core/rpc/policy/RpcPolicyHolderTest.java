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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Unit tests for {@code RpcPolicyHolder}, the volatile indirection layer that enables swappable
 * {@link RpcPolicy} references at runtime.
 *
 * <p>Tests verify construction, policy swap semantics, delegation of {@code hasVisibilityRules()},
 * and cross-thread visibility of the volatile policy field.
 */
public class RpcPolicyHolderTest {

  /**
   * Verifies that the holder returns the exact same {@link RpcPolicy} instance passed to the
   * constructor.
   */
  @Test
  public void shouldReturnInitialPolicyFromConstructor() {
    // Given: An RpcPolicy with ALLOW default and one rule
    RpcPolicy policy =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule("com.example.**", null, RpcPolicyAction.ALLOW, null, null, null)),
            RpcPolicyAction.ALLOW);

    // When: RpcPolicyHolder is constructed with that policy
    RpcPolicyHolder holder = new RpcPolicyHolder(policy);

    // Then: getPolicy() returns the exact same policy instance
    assertThat(holder.getPolicy(), is(sameInstance(policy)));
  }

  /**
   * Verifies that after calling {@code updatePolicy()}, the holder returns the new policy and no
   * longer returns the old one.
   */
  @Test
  public void shouldReturnUpdatedPolicyAfterSwap() {
    // Given: A holder initialized with policy A (DENY default)
    RpcPolicy policyA = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyHolder holder = new RpcPolicyHolder(policyA);

    // When: updatePolicy() is called with policy B (ALLOW default)
    RpcPolicy policyB = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    holder.updatePolicy(policyB);

    // Then: getPolicy() returns policy B; policy A is no longer returned
    assertThat(holder.getPolicy(), is(sameInstance(policyB)));
  }

  /**
   * Verifies that {@code hasVisibilityRules()} delegates to the current policy, returning {@code
   * true} when the policy has visibility rules and {@code false} when it does not.
   */
  @Test
  public void shouldDelegateHasVisibilityRules() {
    // Given: A holder with a policy that has visibility rules
    RpcPolicy withVisibility =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    null,
                    EnumSet.of(MemberVisibility.PUBLIC))),
            RpcPolicyAction.DENY);
    RpcPolicyHolder holder = new RpcPolicyHolder(withVisibility);

    // When/Then: hasVisibilityRules() returns true
    assertThat(holder.hasVisibilityRules(), is(true));

    // Given: A holder with a policy without visibility rules
    RpcPolicy withoutVisibility = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyHolder holder2 = new RpcPolicyHolder(withoutVisibility);

    // When/Then: hasVisibilityRules() returns false
    assertThat(holder2.hasVisibilityRules(), is(false));
  }

  /**
   * Verifies that {@code hasVisibilityRules()} reflects the swapped policy after {@code
   * updatePolicy()} replaces a policy without visibility rules with one that has them.
   */
  @Test
  public void shouldReflectVisibilityRulesChangeAfterSwap() {
    // Given: A holder initialized with a policy without visibility rules
    RpcPolicy noVisibility = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyHolder holder = new RpcPolicyHolder(noVisibility);
    assertThat(holder.hasVisibilityRules(), is(false));

    // When: updatePolicy() swaps in a policy with visibility rules
    RpcPolicy withVisibility =
        new RpcPolicy(
            List.of(
                new RpcPolicyRule(
                    "com.example.**",
                    null,
                    RpcPolicyAction.ALLOW,
                    null,
                    null,
                    EnumSet.of(MemberVisibility.PUBLIC))),
            RpcPolicyAction.DENY);
    holder.updatePolicy(withVisibility);

    // Then: hasVisibilityRules() returns true
    assertThat(holder.hasVisibilityRules(), is(true));
  }

  /**
   * Verifies that a policy update performed by a writer thread is visible to a reader thread,
   * confirming the volatile semantics of the policy field.
   */
  @Test
  public void shouldBeVisibleAcrossThreadsAfterUpdate() throws InterruptedException {
    // Given: A holder initialized with policy A
    RpcPolicy policyA = new RpcPolicy(List.of(), RpcPolicyAction.DENY);
    RpcPolicyHolder holder = new RpcPolicyHolder(policyA);

    RpcPolicy policyB = new RpcPolicy(List.of(), RpcPolicyAction.ALLOW);
    CountDownLatch writerDone = new CountDownLatch(1);
    CountDownLatch readerDone = new CountDownLatch(1);
    AtomicReference<RpcPolicy> readerSaw = new AtomicReference<>();

    // When: A writer thread calls updatePolicy(policyB)
    Thread writer =
        new Thread(
            () -> {
              holder.updatePolicy(policyB);
              writerDone.countDown();
            });

    // Then: A reader thread sees policy B after the writer completes
    Thread reader =
        new Thread(
            () -> {
              try {
                writerDone.await();
                readerSaw.set(holder.getPolicy());
                readerDone.countDown();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });

    writer.start();
    reader.start();
    readerDone.await();

    assertThat(readerSaw.get(), is(sameInstance(policyB)));
  }
}
