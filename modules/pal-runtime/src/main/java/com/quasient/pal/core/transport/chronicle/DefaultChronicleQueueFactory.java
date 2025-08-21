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
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;

/** Default implementation which creates instances of {@link SingleChronicleQueueBuilder}. */
public class DefaultChronicleQueueFactory implements ChronicleQueueFactory {

  /**
   * Creates and returns a {@link SingleChronicleQueueBuilder}
   *
   * <p>{@inheritDoc}
   */
  @Override
  public ChronicleQueue create(Path path, RollCycle rollCycle, int indexSpacing, int blockSize) {
    return SingleChronicleQueueBuilder.single(path.toFile())
        .rollCycle(rollCycle)
        .indexSpacing(indexSpacing)
        .blockSize(blockSize)
        .wireType(WireType.BINARY_LIGHT)
        .build();
  }
}
