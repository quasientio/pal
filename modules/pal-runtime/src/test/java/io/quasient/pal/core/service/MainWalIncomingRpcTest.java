/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@code --wal-incoming-rpc} and {@code --wal-all-incoming-rpc} CLI options in {@link
 * Main}.
 *
 * <p>These tests verify that the CLI layer correctly translates the {@code --wal-incoming-rpc} and
 * {@code --wal-all-incoming-rpc} command-line flags into the corresponding {@link RunOptions}
 * values ({@link RunOptions#WITH_WAL_INCOMING_RPC} and {@link
 * RunOptions#WITH_WAL_ALL_INCOMING_RPC}).
 *
 * <p>The {@code --wal-incoming-rpc} flag enables writing incoming RPC messages (from ZMQ, JSON-RPC,
 * and CLI channels) to WAL/PUB. The {@code --wal-all-incoming-rpc} flag extends this to also
 * include LOG_RPC channel messages, and implies {@code --wal-incoming-rpc}.
 */
public class MainWalIncomingRpcTest {

  /**
   * Tests that the {@code --wal-incoming-rpc} flag sets {@link RunOptions#WITH_WAL_INCOMING_RPC}
   * when a WAL destination is available via {@code --wal}.
   *
   * <p>Acceptance criterion: [TEST:MainWalIncomingRpcTest.walIncomingRpcFlag_setsRunOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  @Ignore("Awaiting implementation in #778")
  public void walIncomingRpcFlag_setsRunOption() throws Exception {
    // Given: CLI args include --wal-incoming-rpc with --wal my-wal and -k localhost:29092
    // When: Main parses args and validateInput() sets runOptions
    // Then: runOptions contains WITH_WAL_INCOMING_RPC

    // TODO(#778): Implement test logic
    // Main main = new Main();
    // new CommandLine(main).parseArgs(
    //     "--wal-incoming-rpc", "--wal", "my-wal", "-k", "localhost:29092");
    //
    // Method validateInput = Main.class.getDeclaredMethod("validateInput");
    // validateInput.setAccessible(true);
    // validateInput.invoke(main);
    //
    // Field runOptionsField = Main.class.getDeclaredField("runOptions");
    // runOptionsField.setAccessible(true);
    // @SuppressWarnings("unchecked")
    // Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);
    //
    // assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));

    fail("Not yet implemented");
  }

  /**
   * Tests that the {@code --wal-all-incoming-rpc} flag sets both {@link
   * RunOptions#WITH_WAL_INCOMING_RPC} and {@link RunOptions#WITH_WAL_ALL_INCOMING_RPC}, since
   * {@code --wal-all-incoming-rpc} implies {@code --wal-incoming-rpc}.
   *
   * <p>Acceptance criterion: [TEST:MainWalIncomingRpcTest.walAllIncomingRpcFlag_setsBothRunOptions]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  @Ignore("Awaiting implementation in #778")
  public void walAllIncomingRpcFlag_setsBothRunOptions() throws Exception {
    // Given: CLI args include --wal-all-incoming-rpc with --wal my-wal and -k localhost:29092
    // When: Main parses args and validateInput() sets runOptions
    // Then: runOptions contains both WITH_WAL_INCOMING_RPC and WITH_WAL_ALL_INCOMING_RPC

    // TODO(#778): Implement test logic
    // Main main = new Main();
    // new CommandLine(main).parseArgs(
    //     "--wal-all-incoming-rpc", "--wal", "my-wal", "-k", "localhost:29092");
    //
    // Method validateInput = Main.class.getDeclaredMethod("validateInput");
    // validateInput.setAccessible(true);
    // validateInput.invoke(main);
    //
    // Field runOptionsField = Main.class.getDeclaredField("runOptions");
    // runOptionsField.setAccessible(true);
    // @SuppressWarnings("unchecked")
    // Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);
    //
    // assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));
    // assertThat(runOptions.contains(RunOptions.WITH_WAL_ALL_INCOMING_RPC), is(true));

    fail("Not yet implemented");
  }

  /**
   * Tests that the {@code --wal-incoming-rpc} flag does NOT set {@link
   * RunOptions#WITH_WAL_INCOMING_RPC} when no WAL destination is available (no {@code --wal} and no
   * {@code --tcp-pub}).
   *
   * <p>Without a WAL or PUB destination, there is nowhere to write incoming messages, so the option
   * should be a no-op (and a warning may be printed).
   *
   * <p>Acceptance criterion:
   * [TEST:MainWalIncomingRpcTest.walIncomingRpcFlag_withoutWal_doesNotSetOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  @Ignore("Awaiting implementation in #778")
  public void walIncomingRpcFlag_withoutWal_doesNotSetOption() throws Exception {
    // Given: CLI args include --wal-incoming-rpc but NO --wal flag and NO --tcp-pub
    // When: Main parses args and validateInput() sets runOptions
    // Then: runOptions does NOT contain WITH_WAL_INCOMING_RPC (no WAL destination available)

    // TODO(#778): Implement test logic
    // Main main = new Main();
    // new CommandLine(main).parseArgs("--wal-incoming-rpc");
    //
    // Method validateInput = Main.class.getDeclaredMethod("validateInput");
    // validateInput.setAccessible(true);
    // validateInput.invoke(main);
    //
    // Field runOptionsField = Main.class.getDeclaredField("runOptions");
    // runOptionsField.setAccessible(true);
    // @SuppressWarnings("unchecked")
    // Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);
    //
    // assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(false));

    fail("Not yet implemented");
  }

  /**
   * Tests that the {@code --wal-incoming-rpc} flag sets {@link RunOptions#WITH_WAL_INCOMING_RPC}
   * when a TCP PUB destination is available (via {@code --tcp-pub}) even without {@code --wal}.
   *
   * <p>TCP PUB is a valid outbound destination for incoming messages, so the flag should be honored
   * when {@code --tcp-pub} is specified.
   *
   * <p>Acceptance criterion:
   * [TEST:MainWalIncomingRpcTest.walIncomingRpcFlag_withTcpPub_setsRunOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  @Ignore("Awaiting implementation in #778")
  public void walIncomingRpcFlag_withTcpPub_setsRunOption() throws Exception {
    // Given: CLI args include --wal-incoming-rpc with --tcp-pub auto (no --wal)
    // When: Main parses args and validateInput() sets runOptions
    // Then: runOptions contains WITH_WAL_INCOMING_RPC (TCP PUB is a valid destination)

    // TODO(#778): Implement test logic
    // Main main = new Main();
    // new CommandLine(main).parseArgs("--wal-incoming-rpc", "--tcp-pub", "auto");
    //
    // Method validateInput = Main.class.getDeclaredMethod("validateInput");
    // validateInput.setAccessible(true);
    // validateInput.invoke(main);
    //
    // Field runOptionsField = Main.class.getDeclaredField("runOptions");
    // runOptionsField.setAccessible(true);
    // @SuppressWarnings("unchecked")
    // Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);
    //
    // assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));

    fail("Not yet implemented");
  }
}
