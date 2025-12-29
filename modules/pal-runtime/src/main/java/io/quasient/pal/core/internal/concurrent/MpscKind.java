/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
