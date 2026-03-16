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
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Entity group container command for intercept-related sub-subcommands.
 *
 * <p>This command serves as the parent for intercept operations following the Docker-style
 * entity-operation pattern ({@code pal intercept ls}). It implements {@link PalCommand} to
 * propagate the directory connection string from the root {@link Pal} command down to its
 * sub-subcommands via {@code @ParentCommand} delegation.
 *
 * <p>When invoked without a subcommand, it prints usage information.
 *
 * @see PalCommand
 * @see Pal
 */
@Command(
    name = "intercept",
    description = "Manage intercepts",
    mixinStandardHelpOptions = true,
    subcommands = {
      InterceptList.class,
      InterceptApply.class,
      InterceptRemove.class,
      InterceptDiffCommand.class,
      InterceptStatusCommand.class
    })
public class InterceptCommand implements PalCommand, Callable<Integer> {

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand parent;

  /** The command specification provided by picocli for accessing usage information. */
  @Spec CommandSpec spec;

  /** Constructs a new {@code InterceptCommand} instance. */
  InterceptCommand() {}

  /**
   * Retrieves the PAL directory connection string by delegating to the parent command.
   *
   * @return the PAL directory connection string from the parent command
   */
  @Override
  public String getPalDirectoryConnectionString() {
    return parent.getPalDirectoryConnectionString();
  }

  /**
   * Prints usage information when invoked without a subcommand.
   *
   * @return exit code 0
   */
  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return 0;
  }
}
