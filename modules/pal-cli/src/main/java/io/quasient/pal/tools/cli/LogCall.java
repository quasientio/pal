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
 * Sends method invocation messages to a log.
 *
 * <p>This is the log-specific call command for the {@code pal log call} pattern. It accepts a log
 * name or path as a positional argument and writes method invocation messages to the log,
 * optionally reading responses from an output log.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "call", description = "Call a method via a log")
class LogCall implements Runnable {

  /** Constructs a new {@code LogCall} instance. */
  LogCall() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
