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
package io.quasient.pal.core.internal.concurrent;

/** Enum to represent the possible variants of MPSC implementations available. */
public enum MpscKind {
  /** No queue. */
  NONE,
  /**
   * MpscArrayQueue - Small, predictable bursts; lowest per-offer cost; no copying / extra objects.
   */
  FIXED,
  /**
   * MpscChunkedArrayQueue - Very large capacity with modest start-up footprint; every chunk is
   * hot-alloc.
   */
  CHUNKED,
  /** MpscGrowableArrayQueue - Few big bursts; better cache locality and less GC than chunked. */
  GROWABLE,
}
