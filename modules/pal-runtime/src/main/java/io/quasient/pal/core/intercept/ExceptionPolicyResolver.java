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

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for resolving effective exception policies for intercept callbacks.
 *
 * <p>This resolver implements a three-level hierarchy for determining which exception policy to
 * apply to an intercept callback:
 *
 * <ol>
 *   <li><b>Per-intercept override:</b> Policy specified directly in the {@link InterceptMessage}
 *   <li><b>Per-intercept-type default:</b> Policy configured for the specific {@link InterceptType}
 *   <li><b>Global default:</b> Fallback policy when no more specific policy is configured
 * </ol>
 *
 * <p>The resolver checks each level in order, returning the first non-null policy found. This
 * allows for flexible configuration where specific intercepts can override type-level defaults,
 * which in turn override global defaults.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Create resolver with configuration
 * ExceptionPolicyConfig config = new ExceptionPolicyConfig.Builder()
 *     .globalPropagationPolicy(ExceptionPropagationPolicy.SWALLOW_ALL)
 *     .perTypePropagationPolicy(InterceptType.BEFORE, ExceptionPropagationPolicy.PROPAGATE_ALL)
 *     .build();
 *
 * ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);
 *
 * // Resolve policy for an intercept message
 * ExceptionPropagationPolicy policy =
 *     resolver.resolvePropagationPolicy(interceptMessage, InterceptType.BEFORE);
 * }</pre>
 *
 * @see ExceptionPropagationPolicy
 * @see CheckedExceptionPolicy
 * @see InterceptMessage
 */
public class ExceptionPolicyResolver {

  /** Configuration containing default policies for resolution. */
  @Nonnull private final ExceptionPolicyConfig config;

  /**
   * Constructs a new {@code ExceptionPolicyResolver} with the specified configuration.
   *
   * @param config the configuration containing default policies; must not be null
   * @throws NullPointerException if config is null
   */
  public ExceptionPolicyResolver(@Nonnull ExceptionPolicyConfig config) {
    if (config == null) {
      throw new NullPointerException("config must not be null");
    }
    this.config = config;
  }

  /**
   * Resolves the effective {@link ExceptionPropagationPolicy} for an intercept.
   *
   * <p>The resolution follows this hierarchy:
   *
   * <ol>
   *   <li>Per-intercept override from {@code intercept} parameter (if present)
   *   <li>Per-intercept-type default from configuration for the given {@code type}
   *   <li>Global default from configuration
   * </ol>
   *
   * @param intercept the intercept message that may contain a per-intercept policy override; may be
   *     null
   * @param type the intercept type to use for per-type policy lookup; must not be null
   * @return the resolved exception propagation policy; never null
   * @throws NullPointerException if type is null
   */
  @Nonnull
  public ExceptionPropagationPolicy resolvePropagationPolicy(
      @Nullable InterceptMessage intercept, @Nonnull InterceptType type) {
    if (type == null) {
      throw new NullPointerException("type must not be null");
    }

    // Level 1: Per-intercept override (highest priority)
    if (intercept != null) {
      ExceptionPropagationPolicy interceptPolicy = getInterceptPropagationPolicy(intercept);
      if (interceptPolicy != null) {
        return interceptPolicy;
      }
    }

    // Level 2: Per-intercept-type default
    ExceptionPropagationPolicy typePolicy = config.getPropagationPolicyForType(type);
    if (typePolicy != null) {
      return typePolicy;
    }

    // Level 3: Global default (lowest priority)
    return config.getGlobalPropagationPolicy();
  }

  /**
   * Resolves the effective {@link CheckedExceptionPolicy} for an intercept.
   *
   * <p>The resolution follows this hierarchy:
   *
   * <ol>
   *   <li>Per-intercept override from {@code intercept} parameter (if present)
   *   <li>Per-intercept-type default from configuration for the given {@code type}
   *   <li>Global default from configuration
   * </ol>
   *
   * @param intercept the intercept message that may contain a per-intercept policy override; may be
   *     null
   * @param type the intercept type to use for per-type policy lookup; must not be null
   * @return the resolved checked exception policy; never null
   * @throws NullPointerException if type is null
   */
  @Nonnull
  public CheckedExceptionPolicy resolveCheckedExceptionPolicy(
      @Nullable InterceptMessage intercept, @Nonnull InterceptType type) {
    if (type == null) {
      throw new NullPointerException("type must not be null");
    }

    // Level 1: Per-intercept override (highest priority)
    if (intercept != null) {
      CheckedExceptionPolicy interceptPolicy = getInterceptCheckedExceptionPolicy(intercept);
      if (interceptPolicy != null) {
        return interceptPolicy;
      }
    }

    // Level 2: Per-intercept-type default
    CheckedExceptionPolicy typePolicy = config.getCheckedExceptionPolicyForType(type);
    if (typePolicy != null) {
      return typePolicy;
    }

    // Level 3: Global default (lowest priority)
    return config.getGlobalCheckedExceptionPolicy();
  }

  /**
   * Extracts the exception propagation policy from an intercept message, if present.
   *
   * <p>This method is a placeholder for future implementation when {@link InterceptMessage} is
   * extended to include exception policy fields.
   *
   * @param intercept the intercept message
   * @return the exception propagation policy from the message, or null if not present
   */
  @Nullable
  @SuppressWarnings("UnusedVariable")
  private ExceptionPropagationPolicy getInterceptPropagationPolicy(InterceptMessage intercept) {
    // TODO: Once InterceptMessage has exceptionPropagationPolicy field, return it here
    // For now, return null to fall through to lower priority policies
    // Example future implementation:
    // return intercept.getExceptionPropagationPolicy();
    return null;
  }

  /**
   * Extracts the checked exception policy from an intercept message, if present.
   *
   * <p>This method is a placeholder for future implementation when {@link InterceptMessage} is
   * extended to include exception policy fields.
   *
   * @param intercept the intercept message
   * @return the checked exception policy from the message, or null if not present
   */
  @Nullable
  @SuppressWarnings("UnusedVariable")
  private CheckedExceptionPolicy getInterceptCheckedExceptionPolicy(InterceptMessage intercept) {
    // TODO: Once InterceptMessage has checkedExceptionPolicy field, return it here
    // For now, return null to fall through to lower priority policies
    // Example future implementation:
    // return intercept.getCheckedExceptionPolicy();
    return null;
  }
}
