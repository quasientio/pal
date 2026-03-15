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
 * Collects and displays message stream statistics from a log.
 *
 * <p>This is the log-specific stats command for the {@code pal log stats} pattern. It accepts a log
 * name as a positional argument and aggregates statistics via Kafka Streams.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "stats", description = "Show log message statistics")
class LogStats implements Runnable {

  /** Constructs a new {@code LogStats} instance. */
  LogStats() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
