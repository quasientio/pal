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
 * Indexes and analyzes a Write-Ahead Log, registered as the {@code index} subcommand of {@code pal
 * log}.
 *
 * <p>This class extends {@link WalIndexCommand} to reuse all WAL indexing functionality under the
 * {@code pal log index} command path. It overrides the command name and synopsis to reflect the
 * entity-operation pattern.
 *
 * @see WalIndexCommand
 */
@Command(
    name = "index",
    customSynopsis = {
      "pal log index [OPTIONS] file:/path       (Chronicle Queue)",
      "pal log index -k <servers> [OPTIONS] <topic>  (Kafka)",
      "pal log index -d <url> [OPTIONS] <name>       (PalDirectory)%n"
    },
    description = "Index and analyze a WAL",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
public class LogIndexCommand extends WalIndexCommand {

  /** Constructs a new {@code LogIndexCommand} instance. */
  LogIndexCommand() {}
}
