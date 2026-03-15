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
 * Sends RPC calls to a specific peer.
 *
 * <p>This is the peer-specific call command for the {@code pal peer call} pattern. It accepts a
 * peer identifier (UUID, address, or name) as a positional argument and sends method invocation
 * requests via ZMQ or JSON-RPC.
 *
 * <p>Placeholder: full implementation pending.
 */
@Command(name = "call", description = "Call a method on a peer")
class PeerCall implements Runnable {

  /** Constructs a new {@code PeerCall} instance. */
  PeerCall() {}

  /** Prints usage when invoked directly. */
  @Override
  public void run() {}
}
