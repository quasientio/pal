/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.objects;

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
