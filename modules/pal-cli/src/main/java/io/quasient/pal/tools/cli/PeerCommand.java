/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli;

import io.quasient.pal.common.cli.PalCommand;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Entity group container command for peer-related sub-subcommands.
 *
 * <p>This command serves as the parent for peer operations following the Docker-style
 * entity-operation pattern ({@code pal peer ls}, {@code pal peer rm}, {@code pal peer print},
 * {@code pal peer call}, {@code pal peer stats}). It implements {@link PalCommand} to propagate the
 * directory connection string from the root {@link Pal} command down to its sub-subcommands via
 * {@code @ParentCommand} delegation.
 *
 * <p>When invoked without a subcommand, it prints usage information.
 *
 * @see PalCommand
 * @see Pal
 */
@Command(
    name = "peer",
    description = "Manage peers",
    mixinStandardHelpOptions = true,
    subcommands = {
      PeerList.class,
      PeerRemove.class,
      PeerPrune.class,
      PeerPrint.class,
      PeerCall.class,
      PeerStats.class,
    })
public class PeerCommand implements PalCommand, Callable<Integer> {

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand parent;

  /** The command specification provided by picocli for accessing usage information. */
  @Spec CommandSpec spec;

  /** Constructs a new {@code PeerCommand} instance. */
  PeerCommand() {}

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
