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
package io.quasient.pal.dsl.intercept;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of a remove operation on intercepts.
 *
 * <p>Contains a list of per-intercept {@link Entry} records, each reporting whether the intercept
 * was removed or not found.
 */
public final class RemoveResult {

  /** Status of an individual remove entry. */
  public enum Status {
    /** The intercept was successfully removed. */
    REMOVED,
    /** The intercept was not found in the directory. */
    NOT_FOUND
  }

  /** A single entry in the remove result. */
  public static final class Entry {

    /** The UUID of the intercept. */
    private final UUID uuid;

    /** The status of the remove operation for this entry. */
    private final Status status;

    /**
     * Constructs a new remove result entry.
     *
     * @param uuid the UUID of the intercept
     * @param status the status of the remove operation
     * @throws NullPointerException if any parameter is {@code null}
     */
    public Entry(UUID uuid, Status status) {
      this.uuid = Objects.requireNonNull(uuid, "uuid must not be null");
      this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /**
     * Returns the UUID of the intercept.
     *
     * @return the intercept UUID
     */
    public UUID getUuid() {
      return uuid;
    }

    /**
     * Returns the status of the remove operation for this entry.
     *
     * @return the remove status
     */
    public Status getStatus() {
      return status;
    }
  }

  /** The list of per-intercept result entries. */
  private final List<Entry> entries;

  /**
   * Constructs a new {@code RemoveResult} with the given entries.
   *
   * @param entries the list of per-intercept result entries
   * @throws NullPointerException if {@code entries} is {@code null}
   */
  public RemoveResult(List<Entry> entries) {
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
   * Returns the number of intercepts that were successfully removed.
   *
   * @return the count of removed intercepts
   */
  public long getRemovedCount() {
    return entries.stream().filter(e -> e.getStatus() == Status.REMOVED).count();
  }

  /**
   * Returns the number of intercepts that were not found.
   *
   * @return the count of not-found intercepts
   */
  public long getNotFoundCount() {
    return entries.stream().filter(e -> e.getStatus() == Status.NOT_FOUND).count();
  }
}
