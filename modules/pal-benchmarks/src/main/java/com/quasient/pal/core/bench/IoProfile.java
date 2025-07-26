/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench;

/**
 * Enumerates IO profile types for a benchmark run.
 */
public enum IoProfile {
  /** Used to measure that is quantisation overhead only. */
  CPU_ONLY,

  /** Measure quantisation cost, header conversion, matching, etc. but
   * mock Kafka and PUBlishing. That is, measure Pal CPU cost without network.
   */
  MOCK,

  /** Measure end to end cost, including real Kafka producer, real ZMQ PUB, etc.*/
  REAL
}
