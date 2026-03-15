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

import io.quasient.pal.common.cli.PalCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Entity group container command for log-related sub-subcommands.
 *
 * <p>This command serves as the parent for log operations following the Docker-style
 * entity-operation pattern ({@code pal log ls}, {@code pal log rm}, {@code pal log print}, {@code
 * pal log call}, {@code pal log index}, {@code pal log stats}). It implements {@link PalCommand} to
 * propagate the directory connection string from the root {@link Pal} command down to its
 * sub-subcommands via {@code @ParentCommand} delegation.
 *
 * <p>When invoked without a subcommand, it prints usage information.
 *
 * @see PalCommand
 * @see Pal
 */
@Command(
    name = "log",
    description = "Manage logs",
    subcommands = {
      LogList.class,
      LogRemove.class,
      LogPrint.class,
      LogCall.class,
      LogIndexCommand.class,
      LogStats.class,
    })
public class LogCommand implements PalCommand, Runnable {

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand parent;

  /** The command specification provided by picocli for accessing usage information. */
  @Spec CommandSpec spec;

  /** Constructs a new {@code LogCommand} instance. */
  LogCommand() {}

  /**
   * Retrieves the PAL directory connection string by delegating to the parent command.
   *
   * @return the PAL directory connection string from the parent command
   */
  @Override
  public String getPalDirectoryConnectionString() {
    return parent.getPalDirectoryConnectionString();
  }

  /** Prints usage information when invoked without a subcommand. */
  @Override
  public void run() {
    spec.commandLine().usage(System.out);
  }
}
