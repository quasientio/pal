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
 * Removes peers from the PAL directory.
 *
 * <p>This is the peer-specific remove command for the {@code pal peer rm} pattern. It supports
 * removal by name, UUID, or prefix with optional force flag.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "rm", description = "Remove peers")
class PeerRemove implements Runnable {

  /** Constructs a new {@code PeerRemove} instance. */
  PeerRemove() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
