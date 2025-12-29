/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */

/**
 * PAL command-line interface implementation using picocli.
 *
 * <p>{@link Pal} is the main entry point. Available subcommands:
 *
 * <ul>
 *   <li>{@code run} - Start a PAL peer (delegates to pal-runtime)
 *   <li>{@code print} - Print messages from a log ({@link MessageStreamPrinter})
 *   <li>{@code call} - Invoke a method on a remote peer ({@link Caller})
 *   <li>{@code ls} - List peers and logs ({@link List})
 *   <li>{@code rm} - Remove peers or logs ({@link Remove})
 * </ul>
 */
package io.quasient.pal.tools.cli;
