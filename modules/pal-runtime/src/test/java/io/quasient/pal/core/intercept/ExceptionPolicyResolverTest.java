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

import static org.junit.Assert.assertEquals;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
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
  public void shouldUseInterceptPolicyOverGlobal() {
    // Given: Global policy is SWALLOW_ALL; intercept has PROPAGATE_ALL
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(ExceptionPropagationPolicy.SWALLOW_ALL)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept =
        new InterceptMessage()
            .withExceptionPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_ALL.toByte());

    // When: Resolving policy for the intercept
    ExceptionPropagationPolicy result =
        resolver.resolvePropagationPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns PROPAGATE_ALL (intercept-level overrides global)
    assertEquals(ExceptionPropagationPolicy.PROPAGATE_ALL, result);
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
  public void shouldUseGlobalWhenInterceptPolicyNull() {
    // Given: Global policy is PROPAGATE_CONTROLLED_ONLY; intercept has null policy
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept = null; // No intercept-level policy

    // When: Resolving policy for the intercept
    ExceptionPropagationPolicy result =
        resolver.resolvePropagationPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns PROPAGATE_CONTROLLED_ONLY (falls back to global)
    assertEquals(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY, result);
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
  public void shouldUsePerTypePolicyOverGlobal() {
    // Given: Global policy is SWALLOW_ALL; BEFORE type policy is PROPAGATE_ALL; intercept has
    // null policy
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(ExceptionPropagationPolicy.SWALLOW_ALL)
            .perTypePropagationPolicy(
                InterceptType.BEFORE, ExceptionPropagationPolicy.PROPAGATE_ALL)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept = null; // No intercept-level policy

    // When: Resolving policy for a BEFORE intercept
    ExceptionPropagationPolicy result =
        resolver.resolvePropagationPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns PROPAGATE_ALL (type-level overrides global)
    assertEquals(ExceptionPropagationPolicy.PROPAGATE_ALL, result);
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
  public void shouldUseInterceptPolicyOverPerType() {
    // Given: BEFORE type policy is PROPAGATE_ALL; intercept has SWALLOW_ALL
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY)
            .perTypePropagationPolicy(
                InterceptType.BEFORE, ExceptionPropagationPolicy.PROPAGATE_ALL)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept =
        new InterceptMessage()
            .withExceptionPropagationPolicy(ExceptionPropagationPolicy.SWALLOW_ALL.toByte());

    // When: Resolving policy for the intercept
    ExceptionPropagationPolicy result =
        resolver.resolvePropagationPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns SWALLOW_ALL (intercept-level overrides type-level)
    assertEquals(ExceptionPropagationPolicy.SWALLOW_ALL, result);
  }

  /**
   * Tests that sentinel value 255 in InterceptMessage is treated as null.
   *
   * <p><b>Given:</b> Global policy is PROPAGATE_ALL; intercept has sentinel value 255
   *
   * <p><b>When:</b> Resolving policy for the intercept
   *
   * <p><b>Then:</b> Returns PROPAGATE_ALL (falls back to global since 255 means null)
   */
  @Test
  public void shouldTreatSentinelValueAsNull() {
    // Given: Global policy is PROPAGATE_ALL; intercept has sentinel value 255
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_ALL)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept = new InterceptMessage().withExceptionPropagationPolicy((byte) 255);

    // When: Resolving policy for the intercept
    ExceptionPropagationPolicy result =
        resolver.resolvePropagationPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns PROPAGATE_ALL (falls back to global since 255 means null)
    assertEquals(ExceptionPropagationPolicy.PROPAGATE_ALL, result);
  }

  /**
   * Tests that CheckedExceptionPolicy is resolved correctly from InterceptMessage.
   *
   * <p><b>Given:</b> Global policy is ALLOW_ALL; intercept has WRAP
   *
   * <p><b>When:</b> Resolving checked exception policy for the intercept
   *
   * <p><b>Then:</b> Returns WRAP (intercept-level overrides global)
   */
  @Test
  public void shouldResolveCheckedExceptionPolicyFromIntercept() {
    // Given: Global policy is ALLOW_ALL; intercept has WRAP
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalCheckedExceptionPolicy(CheckedExceptionPolicy.ALLOW_ALL)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept =
        new InterceptMessage().withCheckedExceptionPolicy(CheckedExceptionPolicy.WRAP.toByte());

    // When: Resolving checked exception policy for the intercept
    CheckedExceptionPolicy result =
        resolver.resolveCheckedExceptionPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns WRAP (intercept-level overrides global)
    assertEquals(CheckedExceptionPolicy.WRAP, result);
  }

  /**
   * Tests that CheckedExceptionPolicy sentinel value 255 is treated as null.
   *
   * <p><b>Given:</b> Global policy is REJECT; intercept has sentinel value 255
   *
   * <p><b>When:</b> Resolving checked exception policy for the intercept
   *
   * <p><b>Then:</b> Returns REJECT (falls back to global since 255 means null)
   */
  @Test
  public void shouldTreatCheckedExceptionSentinelValueAsNull() {
    // Given: Global policy is REJECT; intercept has sentinel value 255
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalCheckedExceptionPolicy(CheckedExceptionPolicy.REJECT)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept = new InterceptMessage().withCheckedExceptionPolicy((byte) 255);

    // When: Resolving checked exception policy for the intercept
    CheckedExceptionPolicy result =
        resolver.resolveCheckedExceptionPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns REJECT (falls back to global since 255 means null)
    assertEquals(CheckedExceptionPolicy.REJECT, result);
  }

  /**
   * Tests that CheckedExceptionPolicy per-type configuration works correctly.
   *
   * <p><b>Given:</b> Global policy is WRAP; BEFORE type policy is REJECT; intercept has null policy
   *
   * <p><b>When:</b> Resolving checked exception policy for a BEFORE intercept
   *
   * <p><b>Then:</b> Returns REJECT (type-level overrides global)
   */
  @Test
  public void shouldUsePerTypeCheckedExceptionPolicyOverGlobal() {
    // Given: Global policy is WRAP; BEFORE type policy is REJECT; intercept has null policy
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalCheckedExceptionPolicy(CheckedExceptionPolicy.WRAP)
            .perTypeCheckedExceptionPolicy(InterceptType.BEFORE, CheckedExceptionPolicy.REJECT)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);

    InterceptMessage intercept = null; // No intercept-level policy

    // When: Resolving checked exception policy for a BEFORE intercept
    CheckedExceptionPolicy result =
        resolver.resolveCheckedExceptionPolicy(intercept, InterceptType.BEFORE);

    // Then: Returns REJECT (type-level overrides global)
    assertEquals(CheckedExceptionPolicy.REJECT, result);
  }
}
