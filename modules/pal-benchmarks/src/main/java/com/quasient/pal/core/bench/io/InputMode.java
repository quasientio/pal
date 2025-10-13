/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.bench.io;

/** Which {@link InvocationArgsSource} to use. */
public enum InputMode {
  /** Asynchronous producer thread that refills a ring‑buffer on the fly. */
  ASYNC,
  /** One big pre‑generated batch that every worker thread samples from. */
  PRELOADED
}
