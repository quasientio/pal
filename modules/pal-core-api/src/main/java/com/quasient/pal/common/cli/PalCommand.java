/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.cli;

/**
 * Defines a contract for PAL (picocli) command classes to enable subcommands to reference the
 * parent command without introducing cyclic dependencies.
 *
 * <p><strong>Purpose and Role:</strong> By implementing this interface, the parent Pal command
 * class allows subcommands to use the {@code @ParentCommand} annotation, facilitating hierarchical
 * command structures within the PAL CLI.
 *
 * @see PalCommand#getPalDirectoryConnectionString()
 */
public interface PalCommand {

  /**
   * Retrieves the connection string used to establish a connection to the PAL directory.
   *
   * @return a {@code String} representing the connection string for the PAL directory.
   */
  String getPalDirectoryConnectionString();
}
