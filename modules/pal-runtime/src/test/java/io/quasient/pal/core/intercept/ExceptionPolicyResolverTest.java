/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link ExceptionPolicyResolver}.
 *
 * <p>Tests verify the policy resolution hierarchy for exception propagation policies:
 *
 * <ol>
 *   <li>Intercept-level policy (highest priority)
 *   <li>Per-type policy (medium priority)
 *   <li>Global policy (lowest priority)
 * </ol>
 *
 * <p>These test specifications are awaiting implementation in issue #286.
 */
public class ExceptionPolicyResolverTest {

  /**
   * Tests that an intercept-level policy takes priority over a global policy.
   *
   * <p><b>Given:</b> Global policy is SWALLOW_ALL; intercept has PROPAGATE_ALL
   *
   * <p><b>When:</b> Resolving policy for the intercept
   *
   * <p><b>Then:</b> Returns PROPAGATE_ALL (intercept-level overrides global)
   */
  @Test
  @Ignore("Awaiting implementation in #286")
  public void shouldUseInterceptPolicyOverGlobal() {
    // Given: Global policy is SWALLOW_ALL; intercept has PROPAGATE_ALL
    // When: Resolving policy for the intercept
    // Then: Returns PROPAGATE_ALL (intercept-level overrides global)

    // TODO: Implement after #286 provides ExceptionPolicyResolver implementation
    fail("Not yet implemented");
  }

  /**
   * Tests that the global policy is used when the intercept policy is null.
   *
   * <p><b>Given:</b> Global policy is PROPAGATE_CONTROLLED_ONLY; intercept has null policy
   *
   * <p><b>When:</b> Resolving policy for the intercept
   *
   * <p><b>Then:</b> Returns PROPAGATE_CONTROLLED_ONLY (falls back to global)
   */
  @Test
  @Ignore("Awaiting implementation in #286")
  public void shouldUseGlobalWhenInterceptPolicyNull() {
    // Given: Global policy is PROPAGATE_CONTROLLED_ONLY; intercept has null policy
    // When: Resolving policy for the intercept
    // Then: Returns PROPAGATE_CONTROLLED_ONLY (falls back to global)

    // TODO: Implement after #286 provides ExceptionPolicyResolver implementation
    fail("Not yet implemented");
  }

  /**
   * Tests that a per-type policy takes priority over a global policy.
   *
   * <p><b>Given:</b> Global policy is SWALLOW_ALL; BEFORE type policy is PROPAGATE_ALL; intercept
   * has null policy
   *
   * <p><b>When:</b> Resolving policy for a BEFORE intercept
   *
   * <p><b>Then:</b> Returns PROPAGATE_ALL (type-level overrides global)
   */
  @Test
  @Ignore("Awaiting implementation in #286")
  public void shouldUsePerTypePolicyOverGlobal() {
    // Given: Global policy is SWALLOW_ALL; BEFORE type policy is PROPAGATE_ALL; intercept has
    // null policy
    // When: Resolving policy for a BEFORE intercept
    // Then: Returns PROPAGATE_ALL (type-level overrides global)

    // TODO: Implement after #286 provides ExceptionPolicyResolver implementation
    fail("Not yet implemented");
  }

  /**
   * Tests that an intercept-level policy takes priority over a per-type policy.
   *
   * <p><b>Given:</b> BEFORE type policy is PROPAGATE_ALL; intercept has SWALLOW_ALL
   *
   * <p><b>When:</b> Resolving policy for the intercept
   *
   * <p><b>Then:</b> Returns SWALLOW_ALL (intercept-level overrides type-level)
   */
  @Test
  @Ignore("Awaiting implementation in #286")
  public void shouldUseInterceptPolicyOverPerType() {
    // Given: BEFORE type policy is PROPAGATE_ALL; intercept has SWALLOW_ALL
    // When: Resolving policy for the intercept
    // Then: Returns SWALLOW_ALL (intercept-level overrides type-level)

    // TODO: Implement after #286 provides ExceptionPolicyResolver implementation
    fail("Not yet implemented");
  }
}
