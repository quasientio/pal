/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.chronicle;

import java.nio.file.Path;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.RollCycle;

/** Factory for building ChronicleQueue instances. Abstracted for DI and tests. */
public interface ChronicleQueueFactory {

  /**
   * Creates a {@link ChronicleQueue} instance.
   *
   * @param path directory where to create file(s)
   * @param rollCycle roll cycle to use
   * @param indexSpacing index spacing to use (higher to lower index writes)
   * @param blockSize block size to use
   * @return the created chronicle queue
   */
  ChronicleQueue create(Path path, RollCycle rollCycle, int indexSpacing, int blockSize);
}
