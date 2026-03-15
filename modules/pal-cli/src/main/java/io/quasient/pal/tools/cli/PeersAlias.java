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
 * Shortcut command that lists peers directly from the root {@code pal} command.
 *
 * <p>Running {@code pal peers} is equivalent to {@code pal peer ls}. This alias is registered as a
 * direct child of the root {@link Pal} command so that its {@code @ParentCommand PalCommand}
 * resolves to {@link Pal} directly, enabling correct propagation of the directory connection
 * string.
 *
 * @see PeerList
 * @see PeerCommand
 */
@Command(name = "peers", description = "List peers (shorthand for 'peer ls')")
public class PeersAlias extends PeerList {

  /** Constructs a new {@code PeersAlias} instance. */
  PeersAlias() {}
}
