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
 * Lists intercepts registered in the PAL directory.
 *
 * <p>This is the intercept-specific list command for the {@code pal intercept ls} pattern. It
 * displays intercepts in short or long format with optional sorting and reversal.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "ls", description = "List intercepts")
class InterceptList implements Runnable {

  /** Constructs a new {@code InterceptList} instance. */
  InterceptList() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
