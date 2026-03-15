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
 * Lists logs registered in the PAL directory.
 *
 * <p>This is the log-specific list command for the {@code pal log ls} pattern. It displays logs in
 * short or long format with optional sorting by size or creation time, reversal, and trimming.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "ls", description = "List logs")
class LogList implements Runnable {

  /** Constructs a new {@code LogList} instance. */
  LogList() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
