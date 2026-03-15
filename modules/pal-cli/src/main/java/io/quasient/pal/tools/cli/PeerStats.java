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
 * Collects and displays message stream statistics from a peer.
 *
 * <p>This is the peer-specific stats command for the {@code pal peer stats} pattern. It accepts a
 * peer UUID or address as a positional argument and streams statistics via socket connection.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "stats", description = "Show peer message statistics")
class PeerStats implements Runnable {

  /** Constructs a new {@code PeerStats} instance. */
  PeerStats() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
