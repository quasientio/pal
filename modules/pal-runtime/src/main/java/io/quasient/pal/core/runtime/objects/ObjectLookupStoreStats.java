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
package io.quasient.pal.core.runtime.objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents statistical data for an object lookup store, including metrics such as successful
 * lookups, total objects cleared, and maximum store size.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "Stats container - atomic counters intentionally shared for monitoring")
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
