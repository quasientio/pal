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

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A diff entry describing what would change if a bundle were applied or removed.
 *
 * <p>Each entry corresponds to a single {@link InterceptSpec} and indicates whether it would be
 * created, is unchanged, was modified, or would be removed. The optional {@code details} field
 * describes what specifically differs.
 *
 * @see InterceptSpec
 */
public final class InterceptDiff {

  /** The type of difference detected. */
  public enum DiffType {
    /** The intercept would be newly created. */
    CREATE,
    /** The intercept exists and matches the spec. */
    UNCHANGED,
    /** The intercept exists but differs from the spec. */
    MODIFIED,
    /** The intercept would be removed. */
    REMOVE
  }

  /** The intercept spec this diff corresponds to. */
  private final InterceptSpec interceptSpec;

  /** The type of difference. */
  private final DiffType diffType;

  /** An optional description of what differs. */
  @Nullable private final String details;

  /**
   * Constructs a new diff entry.
   *
   * @param interceptSpec the intercept spec this diff corresponds to
   * @param diffType the type of difference
   * @param details an optional description of what differs
   * @throws NullPointerException if {@code interceptSpec} or {@code diffType} is {@code null}
   */
  public InterceptDiff(InterceptSpec interceptSpec, DiffType diffType, @Nullable String details) {
    this.interceptSpec = Objects.requireNonNull(interceptSpec, "interceptSpec must not be null");
    this.diffType = Objects.requireNonNull(diffType, "diffType must not be null");
    this.details = details;
  }

  /**
   * Returns the intercept spec this diff corresponds to.
   *
   * @return the intercept spec
   */
  public InterceptSpec getInterceptSpec() {
    return interceptSpec;
  }

  /**
   * Returns the type of difference.
   *
   * @return the diff type
   */
  public DiffType getDiffType() {
    return diffType;
  }

  /**
   * Returns an optional description of what differs.
   *
   * @return the details string, or {@code null} if not applicable
   */
  @Nullable
  public String getDetails() {
    return details;
  }
}
