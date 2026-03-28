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
package io.quasient.pal.core.intercept;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Configuration object containing default exception policies for intercept callbacks.
 *
 * <p>This class provides three levels of policy configuration:
 *
 * <ol>
 *   <li><b>Global defaults:</b> Applied when no more specific policy is configured
 *   <li><b>Per-type defaults:</b> Applied for specific {@link InterceptType} values
 *   <li><b>Per-intercept overrides:</b> Configured directly in intercept requests (handled
 *       elsewhere)
 * </ol>
 *
 * <p>Use the {@link Builder} to construct instances with the desired policy configuration.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * ExceptionPolicyConfig config = new ExceptionPolicyConfig.Builder()
 *     .globalPropagationPolicy(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY)
 *     .globalCheckedExceptionPolicy(CheckedExceptionPolicy.WRAP)
 *     .perTypePropagationPolicy(InterceptType.BEFORE, ExceptionPropagationPolicy.PROPAGATE_ALL)
 *     .build();
 * }</pre>
 *
 * @see ExceptionPolicyResolver
 * @see ExceptionPropagationPolicy
 * @see CheckedExceptionPolicy
 */
public class ExceptionPolicyConfig {

  /** Global default for exception propagation policy. */
  @Nonnull private final ExceptionPropagationPolicy globalPropagationPolicy;

  /** Global default for checked exception policy. */
  @Nonnull private final CheckedExceptionPolicy globalCheckedExceptionPolicy;

  /**
   * Per-type exception propagation policies. Maps {@link InterceptType} to its specific propagation
   * policy.
   */
  @Nonnull private final Map<InterceptType, ExceptionPropagationPolicy> perTypePropagationPolicies;

  /**
   * Per-type checked exception policies. Maps {@link InterceptType} to its specific checked
   * exception policy.
   */
  @Nonnull private final Map<InterceptType, CheckedExceptionPolicy> perTypeCheckedExceptionPolicies;

  /**
   * Private constructor. Use {@link Builder} to create instances.
   *
   * @param builder the builder containing configuration values
   */
  private ExceptionPolicyConfig(@Nonnull Builder builder) {
    this.globalPropagationPolicy = builder.globalPropagationPolicy;
    this.globalCheckedExceptionPolicy = builder.globalCheckedExceptionPolicy;
    this.perTypePropagationPolicies = new EnumMap<>(builder.perTypePropagationPolicies);
    this.perTypeCheckedExceptionPolicies = new EnumMap<>(builder.perTypeCheckedExceptionPolicies);
  }

  /**
   * Gets the global default exception propagation policy.
   *
   * @return the global propagation policy; never null
   */
  @Nonnull
  public ExceptionPropagationPolicy getGlobalPropagationPolicy() {
    return globalPropagationPolicy;
  }

  /**
   * Gets the global default checked exception policy.
   *
   * @return the global checked exception policy; never null
   */
  @Nonnull
  public CheckedExceptionPolicy getGlobalCheckedExceptionPolicy() {
    return globalCheckedExceptionPolicy;
  }

  /**
   * Gets the exception propagation policy configured for a specific intercept type.
   *
   * @param type the intercept type; must not be null
   * @return the propagation policy for the given type, or null if no type-specific policy is
   *     configured
   * @throws NullPointerException if type is null
   */
  @Nullable
  public ExceptionPropagationPolicy getPropagationPolicyForType(@Nonnull InterceptType type) {
    if (type == null) {
      throw new NullPointerException("type must not be null");
    }
    return perTypePropagationPolicies.get(type);
  }

  /**
   * Gets the checked exception policy configured for a specific intercept type.
   *
   * @param type the intercept type; must not be null
   * @return the checked exception policy for the given type, or null if no type-specific policy is
   *     configured
   * @throws NullPointerException if type is null
   */
  @Nullable
  public CheckedExceptionPolicy getCheckedExceptionPolicyForType(@Nonnull InterceptType type) {
    if (type == null) {
      throw new NullPointerException("type must not be null");
    }
    return perTypeCheckedExceptionPolicies.get(type);
  }

  /**
   * Builder for creating {@link ExceptionPolicyConfig} instances.
   *
   * <p>This builder provides a fluent API for configuring exception policies at both global and
   * per-type levels.
   */
  public static class Builder {

    /** Global propagation policy. Defaults to PROPAGATE_CONTROLLED_ONLY. */
    @Nonnull
    private ExceptionPropagationPolicy globalPropagationPolicy =
        ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY;

    /** Global checked exception policy. Defaults to WRAP. */
    @Nonnull
    private CheckedExceptionPolicy globalCheckedExceptionPolicy = CheckedExceptionPolicy.WRAP;

    /** Per-type propagation policies. */
    @Nonnull
    private final Map<InterceptType, ExceptionPropagationPolicy> perTypePropagationPolicies =
        new EnumMap<>(InterceptType.class);

    /** Per-type checked exception policies. */
    @Nonnull
    private final Map<InterceptType, CheckedExceptionPolicy> perTypeCheckedExceptionPolicies =
        new EnumMap<>(InterceptType.class);

    /**
     * Sets the global default exception propagation policy.
     *
     * @param policy the global propagation policy; must not be null
     * @return this builder for method chaining
     * @throws NullPointerException if policy is null
     */
    @Nonnull
    public Builder globalPropagationPolicy(@Nonnull ExceptionPropagationPolicy policy) {
      if (policy == null) {
        throw new NullPointerException("policy must not be null");
      }
      this.globalPropagationPolicy = policy;
      return this;
    }

    /**
     * Sets the global default checked exception policy.
     *
     * @param policy the global checked exception policy; must not be null
     * @return this builder for method chaining
     * @throws NullPointerException if policy is null
     */
    @Nonnull
    public Builder globalCheckedExceptionPolicy(@Nonnull CheckedExceptionPolicy policy) {
      if (policy == null) {
        throw new NullPointerException("policy must not be null");
      }
      this.globalCheckedExceptionPolicy = policy;
      return this;
    }

    /**
     * Sets the exception propagation policy for a specific intercept type.
     *
     * @param type the intercept type; must not be null
     * @param policy the propagation policy for this type; must not be null
     * @return this builder for method chaining
     * @throws NullPointerException if type or policy is null
     */
    @Nonnull
    public Builder perTypePropagationPolicy(
        @Nonnull InterceptType type, @Nonnull ExceptionPropagationPolicy policy) {
      if (type == null) {
        throw new NullPointerException("type must not be null");
      }
      if (policy == null) {
        throw new NullPointerException("policy must not be null");
      }
      this.perTypePropagationPolicies.put(type, policy);
      return this;
    }

    /**
     * Sets the checked exception policy for a specific intercept type.
     *
     * @param type the intercept type; must not be null
     * @param policy the checked exception policy for this type; must not be null
     * @return this builder for method chaining
     * @throws NullPointerException if type or policy is null
     */
    @Nonnull
    public Builder perTypeCheckedExceptionPolicy(
        @Nonnull InterceptType type, @Nonnull CheckedExceptionPolicy policy) {
      if (type == null) {
        throw new NullPointerException("type must not be null");
      }
      if (policy == null) {
        throw new NullPointerException("policy must not be null");
      }
      this.perTypeCheckedExceptionPolicies.put(type, policy);
      return this;
    }

    /**
     * Builds a new {@link ExceptionPolicyConfig} instance with the configured values.
     *
     * @return a new configuration instance; never null
     */
    @Nonnull
    public ExceptionPolicyConfig build() {
      return new ExceptionPolicyConfig(this);
    }
  }
}
