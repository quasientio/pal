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
 * Streams and prints messages from a log (Kafka topic or Chronicle Queue).
 *
 * <p>This is the log-specific print command for the {@code pal log print} pattern. It accepts a log
 * name or path as a positional argument and prints messages in various output formats with optional
 * filtering, following, and offset control.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "print", description = "Print messages from a log")
class LogPrint implements Runnable {

  /** Constructs a new {@code LogPrint} instance. */
  LogPrint() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
