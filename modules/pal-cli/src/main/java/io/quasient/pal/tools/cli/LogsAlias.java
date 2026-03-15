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
 * Shortcut command that lists logs directly from the root {@code pal} command.
 *
 * <p>Running {@code pal logs} is equivalent to {@code pal log ls}. This alias is registered as a
 * direct child of the root {@link Pal} command so that its {@code @ParentCommand PalCommand}
 * resolves to {@link Pal} directly, enabling correct propagation of the directory connection
 * string.
 *
 * @see LogList
 * @see LogCommand
 */
@Command(name = "logs", description = "List logs (shorthand for 'log ls')")
public class LogsAlias extends LogList {

  /** Constructs a new {@code LogsAlias} instance. */
  LogsAlias() {}
}
