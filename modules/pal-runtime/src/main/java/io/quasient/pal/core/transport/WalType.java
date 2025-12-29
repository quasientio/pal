/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport;

/** Enumerates the different implementations of a Write-Ahead Log destination. */
public enum WalType {
  /** The Write-Ahead Log is a Kafka topic. */
  KAFKA,

  /** The Write-Ahead Log is a Chronicle queue. */
  CHRONICLE
}
