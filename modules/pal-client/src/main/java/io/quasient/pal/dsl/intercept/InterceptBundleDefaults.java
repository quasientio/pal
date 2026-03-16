/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import java.time.Duration;
import javax.annotation.Nullable;

/**
 * Immutable value class holding default values for an intercept bundle.
 *
 * <p>Each field is nullable; a {@code null} value means no default is specified for that field.
 * Individual {@link InterceptSpec} instances may override any of these defaults.
 *
 * @see InterceptSpec
 * @see InterceptBundleSpec
 */
public final class InterceptBundleDefaults {

  /** An empty defaults instance with all fields set to {@code null}. */
  public static final InterceptBundleDefaults EMPTY =
      new InterceptBundleDefaults(null, null, null, null, null, null);

  /** The default callback peer name or UUID. */
  @Nullable private final String peer;

  /** The default execution priority. */
  @Nullable private final Integer priority;

  /** The default TTL duration. */
  @Nullable private final Duration ttl;

  /** The default force-immediate flag. */
  @Nullable private final Boolean forceImmediate;

  /** The default exception propagation policy. */
  @Nullable private final ExceptionPropagationPolicy exceptionPolicy;

  /** The default checked exception policy. */
  @Nullable private final CheckedExceptionPolicy checkedExceptionPolicy;

  /**
   * Constructs a new {@code InterceptBundleDefaults} with all fields explicitly set.
   *
   * @param peer the default callback peer name or UUID, or {@code null}
   * @param priority the default execution priority, or {@code null}
   * @param ttl the default TTL duration, or {@code null}
   * @param forceImmediate the default force-immediate flag, or {@code null}
   * @param exceptionPolicy the default exception propagation policy, or {@code null}
   * @param checkedExceptionPolicy the default checked exception policy, or {@code null}
   */
  public InterceptBundleDefaults(
      @Nullable String peer,
      @Nullable Integer priority,
      @Nullable Duration ttl,
      @Nullable Boolean forceImmediate,
      @Nullable ExceptionPropagationPolicy exceptionPolicy,
      @Nullable CheckedExceptionPolicy checkedExceptionPolicy) {
    this.peer = peer;
    this.priority = priority;
    this.ttl = ttl;
    this.forceImmediate = forceImmediate;
    this.exceptionPolicy = exceptionPolicy;
    this.checkedExceptionPolicy = checkedExceptionPolicy;
  }

  /**
   * Returns the default callback peer name or UUID.
   *
   * @return the default peer, or {@code null} if not specified
   */
  @Nullable
  public String getPeer() {
    return peer;
  }

  /**
   * Returns the default execution priority.
   *
   * @return the default priority, or {@code null} if not specified
   */
  @Nullable
  public Integer getPriority() {
    return priority;
  }

  /**
   * Returns the default TTL duration.
   *
   * @return the default TTL, or {@code null} if not specified
   */
  @Nullable
  public Duration getTtl() {
    return ttl;
  }

  /**
   * Returns the default force-immediate flag.
   *
   * @return the default force-immediate setting, or {@code null} if not specified
   */
  @Nullable
  public Boolean getForceImmediate() {
    return forceImmediate;
  }

  /**
   * Returns the default exception propagation policy.
   *
   * @return the default exception policy, or {@code null} if not specified
   */
  @Nullable
  public ExceptionPropagationPolicy getExceptionPolicy() {
    return exceptionPolicy;
  }

  /**
   * Returns the default checked exception policy.
   *
   * @return the default checked exception policy, or {@code null} if not specified
   */
  @Nullable
  public CheckedExceptionPolicy getCheckedExceptionPolicy() {
    return checkedExceptionPolicy;
  }
}
