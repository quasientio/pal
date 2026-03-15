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
 * Streams and prints messages from a peer's socket connection.
 *
 * <p>This is the peer-specific print command for the {@code pal peer print} pattern. It accepts a
 * peer UUID or address as a positional argument and streams messages in various output formats.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "print", description = "Print messages from a peer")
class PeerPrint implements Runnable {

  /** Constructs a new {@code PeerPrint} instance. */
  PeerPrint() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
