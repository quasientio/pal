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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Result of an apply operation on an intercept bundle.
 *
 * <p>Contains a list of per-intercept {@link Entry} records, each reporting whether the intercept
 * was created, skipped (already exists), or failed.
 *
 * @see InterceptSpec
 */
public final class ApplyResult {

  /** Status of an individual apply entry. */
  public enum Status {
    /** The intercept was successfully created. */
    CREATED,
    /** The intercept already exists and was skipped. */
    SKIPPED,
    /** The intercept creation failed. */
    FAILED
  }

  /** A single entry in the apply result. */
  public static final class Entry {

    /** The intercept spec this entry corresponds to. */
    private final InterceptSpec interceptSpec;

    /** The UUID assigned to the intercept. */
    private final UUID uuid;

    /** The status of the apply operation for this entry. */
    private final Status status;

    /** An optional error message for failed entries. */
    @Nullable private final String errorMessage;

    /**
     * Constructs a new apply result entry.
     *
     * @param interceptSpec the intercept spec this entry corresponds to
     * @param uuid the UUID assigned to the intercept
     * @param status the status of the apply operation
     * @param errorMessage an optional error message for failed entries
     * @throws NullPointerException if any required parameter is {@code null}
     */
    public Entry(
        InterceptSpec interceptSpec, UUID uuid, Status status, @Nullable String errorMessage) {
      this.interceptSpec = Objects.requireNonNull(interceptSpec, "interceptSpec must not be null");
      this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
      this.status = Objects.requireNonNull(status, "status must not be null");
      this.errorMessage = errorMessage;
    }

    /**
     * Returns the intercept spec this entry corresponds to.
     *
     * @return the intercept spec
     */
    public InterceptSpec getInterceptSpec() {
      return interceptSpec;
    }

    /**
     * Returns the UUID assigned to the intercept.
     *
     * @return the intercept UUID
     */
    public UUID getUuid() {
      return uuid;
    }

    /**
     * Returns the status of the apply operation for this entry.
     *
     * @return the apply status
     */
    public Status getStatus() {
      return status;
    }

    /**
     * Returns an optional error message for failed entries.
     *
     * @return the error message, or {@code null} if not applicable
     */
    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  /** The list of per-intercept result entries. */
  private final List<Entry> entries;

  /**
   * Constructs a new {@code ApplyResult} with the given entries.
   *
   * @param entries the list of per-intercept result entries
   * @throws NullPointerException if {@code entries} is {@code null}
   */
  public ApplyResult(List<Entry> entries) {
    Objects.requireNonNull(entries, "entries must not be null");
    this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
  }

  /**
   * Returns the list of per-intercept result entries.
   *
   * @return an unmodifiable list of entries
   */
  public List<Entry> getEntries() {
    return entries;
  }

  /**
   * Returns the number of intercepts that were successfully created.
   *
   * @return the count of created intercepts
   */
  public long getCreatedCount() {
    return entries.stream().filter(e -> e.getStatus() == Status.CREATED).count();
  }

  /**
   * Returns the number of intercepts that were skipped (already exist).
   *
   * @return the count of skipped intercepts
   */
  public long getSkippedCount() {
    return entries.stream().filter(e -> e.getStatus() == Status.SKIPPED).count();
  }

  /**
   * Returns the number of intercepts that failed to be created.
   *
   * @return the count of failed intercepts
   */
  public long getFailedCount() {
    return entries.stream().filter(e -> e.getStatus() == Status.FAILED).count();
  }
}
