/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.objects;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents statistical data for an object lookup store, including metrics such as successful
 * lookups, total objects cleared, and maximum store size.
 */
public final class ObjectLookupStoreStats {

  /** The count of successful store lookup operations. */
  private final AtomicLong successfulStoreLookups = new AtomicLong();

  /** The total number of objects that have been cleared from the store. */
  private final AtomicLong totalObjectsCleared = new AtomicLong();

  /** The maximum number of objects that have been in the store at any one time. */
  private final AtomicLong maxSize = new AtomicLong();

  /** Initializes a new instance of {@code ObjectLookupStoreStats}. */
  ObjectLookupStoreStats() {}

  /**
   * Returns the count of successful store lookup operations.
   *
   * @return the {@link AtomicLong} tracking successful store lookups
   */
  public AtomicLong getSuccessfulStoreLookups() {
    return successfulStoreLookups;
  }

  /**
   * Returns the total number of objects that have been cleared from the store.
   *
   * @return the {@link AtomicLong} tracking total objects cleared
   */
  public AtomicLong getTotalObjectsCleared() {
    return totalObjectsCleared;
  }

  /**
   * Returns the maximum number of objects that have been in the store at any one time.
   *
   * @return the {@link AtomicLong} tracking the maximum store size
   */
  public AtomicLong getMaxSize() {
    return maxSize;
  }
}
