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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.Test;
import picocli.CommandLine;

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
  public void walIncomingRpcFlag_setsRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--wal-incoming-rpc", "--wal", "my-wal", "-k", "localhost:29092");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));
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
  public void walAllIncomingRpcFlag_setsBothRunOptions() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--wal-all-incoming-rpc", "--wal", "my-wal", "-k", "localhost:29092");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));
    assertThat(runOptions.contains(RunOptions.WITH_WAL_ALL_INCOMING_RPC), is(true));
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
  public void walIncomingRpcFlag_withoutWal_doesNotSetOption() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--wal-incoming-rpc");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(false));
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
  public void walIncomingRpcFlag_withTcpPub_setsRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--wal-incoming-rpc", "--tcp-pub", "auto");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_RPC), is(true));
  }
}
