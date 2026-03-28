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
package io.quasient.pal.core.transport.chronicle;

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

  /**
   * Creates and returns a read-only {@link SingleChronicleQueueBuilder}
   *
   * <p>{@inheritDoc}
   */
  @Override
  public ChronicleQueue createReadOnly(Path path) {
    return SingleChronicleQueueBuilder.single(path.toFile())
        .readOnly(true)
        .wireType(WireType.BINARY_LIGHT)
        .build();
  }
}
