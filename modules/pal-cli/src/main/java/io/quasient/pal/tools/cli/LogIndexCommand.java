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
