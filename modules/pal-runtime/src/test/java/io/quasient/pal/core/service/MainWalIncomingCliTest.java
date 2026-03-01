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
 * Tests for the {@code --wal-incoming-cli} CLI option in {@link Main}.
 *
 * <p>These tests verify that the CLI layer correctly translates the {@code --wal-incoming-cli}
 * command-line flag into the corresponding {@link RunOptions} value ({@code
 * WITH_WAL_INCOMING_CLI}).
 *
 * <p>The {@code --wal-incoming-cli} flag enables writing incoming CLI-channel messages (from {@code
 * SelfBootstrapInvoker}) to WAL/PUB. This flag is independent of {@code --wal-incoming-rpc}; each
 * controls its own channel.
 *
 * <p>Follows the same reflection-based testing pattern as {@link MainWalIncomingRpcTest}: picocli
 * parsing followed by {@code Main.validateInput()} invocation via reflection.
 */
public class MainWalIncomingCliTest {

  /**
   * Tests that the {@code --wal-incoming-cli} flag sets {@code WITH_WAL_INCOMING_CLI} when a WAL
   * destination is available via {@code --wal}.
   *
   * <p>Acceptance criterion: [TEST:MainWalIncomingCliTest.walIncomingCliFlag_setsRunOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void walIncomingCliFlag_setsRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--wal-incoming-cli", "--wal", "my-wal", "-k", "localhost:29092");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_CLI), is(true));
  }

  /**
   * Tests that the {@code --wal-incoming-cli} flag does NOT set {@code WITH_WAL_INCOMING_CLI} when
   * no WAL destination is available (no {@code --wal} and no {@code --tcp-pub}).
   *
   * <p>Without a WAL or PUB destination, there is nowhere to write incoming messages, so the option
   * should be a no-op.
   *
   * <p>Acceptance criterion:
   * [TEST:MainWalIncomingCliTest.walIncomingCliFlag_withoutWal_doesNotSetOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void walIncomingCliFlag_withoutWal_doesNotSetOption() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--wal-incoming-cli");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_CLI), is(false));
  }

  /**
   * Tests that the {@code --wal-incoming-cli} flag sets {@code WITH_WAL_INCOMING_CLI} when a TCP
   * PUB destination is available (via {@code --tcp-pub}) even without {@code --wal}.
   *
   * <p>TCP PUB is a valid outbound destination for incoming messages, so the flag should be honored
   * when {@code --tcp-pub} is specified.
   *
   * <p>Acceptance criterion:
   * [TEST:MainWalIncomingCliTest.walIncomingCliFlag_withTcpPub_setsRunOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void walIncomingCliFlag_withTcpPub_setsRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--wal-incoming-cli", "--tcp-pub", "auto");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_CLI), is(true));
  }

  /**
   * Tests that explicitly disabling {@code --no-wal-incoming-cli} prevents {@code
   * WITH_WAL_INCOMING_CLI} from being set, even when {@code --wal-incoming-rpc} is enabled.
   *
   * <p>The {@code --wal-incoming-rpc} and {@code --wal-incoming-cli} flags are independent; each
   * controls its own channel. Disabling one does not affect the other.
   *
   * <p>Acceptance criterion: [TEST:MainWalIncomingCliTest.noWalIncomingCliFlag_doesNotSetCliOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void noWalIncomingCliFlag_doesNotSetCliOption() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs(
            "--no-wal-incoming-cli",
            "--wal-incoming-rpc",
            "--wal",
            "my-wal",
            "-k",
            "localhost:29092");

    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Field runOptionsField = Main.class.getDeclaredField("runOptions");
    runOptionsField.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) runOptionsField.get(main);

    assertThat(runOptions.contains(RunOptions.WITH_WAL_INCOMING_CLI), is(false));
  }
}
