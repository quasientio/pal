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
package io.quasient.pal.core.transport.gateway;

/**
 * Per-thread counters tracking how much a producer had to back off when the queue was full.
 *
 * <ul>
 *   <li>{@code parkedNanos} – total nanoseconds spent in {@code LockSupport.parkNanos()}.
 *   <li>{@code parks} – number of times the thread actually parked.
 *   <li>{@code failedOffers} – how many {@code queue.offer(..)} calls returned {@code false}.
 * </ul>
 *
 * <p>All fields are intentionally package-visible to avoid the overhead of getters in the hot path.
 */
public class WaitStats {

  /** total nanoseconds spent in {@code LockSupport.parkNanos()} */
  long parkedNanos;

  /** number of times thread was parked */
  int parks;

  /** how many times the call to offer() returned false */
  int failedOffers;
}
