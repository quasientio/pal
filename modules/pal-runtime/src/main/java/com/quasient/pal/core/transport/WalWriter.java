/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport;

import com.quasient.pal.common.directory.nodes.LogInfo;

/** Common interface for WAL writer implementations. */
public interface WalWriter {

  /**
   * Returns a consistent, side-effect-free view of the counters.
   *
   * @return snapshot of live stats
   */
  WalWriterStats getLiveStats();

  /**
   * Returns the destination {@link LogInfo}, where WAL messages are being written.
   *
   * @return the current LogInfo object
   */
  LogInfo getCurrentWal();

  /**
   * Configure the writer for a particular log.
   *
   * @param writeAheadLog log information containing details such as the Log name.
   * @param publishOffsets flag indicating whether written offsets/indexes should be published via
   *     ZeroMQ.
   */
  void writeToLog(LogInfo writeAheadLog, boolean publishOffsets);
}
