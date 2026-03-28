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
package io.quasient.pal.core.bench;

/** Enumerates IO profile types for a benchmark run. */
public enum IoProfile {
  /** Used to measure that is quantisation overhead only. */
  CPU_ONLY,

  /**
   * Measure quantisation cost, header conversion, matching, etc. but mock Kafka and PUBlishing.
   * That is, measure Pal CPU cost without network.
   */
  MOCK,

  /** Measure end to end cost, including real Kafka producer, real ZMQ PUB, etc. */
  REAL
}
