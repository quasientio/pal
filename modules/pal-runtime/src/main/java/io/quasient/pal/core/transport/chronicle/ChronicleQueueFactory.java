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

  /**
   * Creates a read-only {@link ChronicleQueue} instance.
   *
   * @param path directory where queue files are located
   * @return the created read-only chronicle queue
   */
  ChronicleQueue createReadOnly(Path path);
}
