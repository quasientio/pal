/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import picocli.CommandLine.Command;

/**
 * Removes logs from the PAL directory and their backing stores.
 *
 * <p>This is the log-specific remove command for the {@code pal log rm} pattern. It supports
 * removal by name, UUID, or prefix, and handles both Kafka topics and Chronicle Queue directories.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "rm", description = "Remove logs")
class LogRemove implements Runnable {

  /** Constructs a new {@code LogRemove} instance. */
  LogRemove() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
