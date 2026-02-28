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
  @Ignore("Awaiting implementation in #877")
  public void walIncomingCliFlag_setsRunOption() throws Exception {
    // Given: Main parsed with --wal-incoming-cli --wal my-wal -k localhost:29092
    // When: validateInput() is invoked via reflection
    // Then: runOptions contains WITH_WAL_INCOMING_CLI

    // TODO(#877): Implement test logic
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #877")
  public void walIncomingCliFlag_withoutWal_doesNotSetOption() throws Exception {
    // Given: Main parsed with --wal-incoming-cli only (no --wal, no --tcp-pub)
    // When: validateInput() is invoked via reflection
    // Then: runOptions does NOT contain WITH_WAL_INCOMING_CLI

    // TODO(#877): Implement test logic
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #877")
  public void walIncomingCliFlag_withTcpPub_setsRunOption() throws Exception {
    // Given: Main parsed with --wal-incoming-cli --tcp-pub auto
    // When: validateInput() is invoked via reflection
    // Then: runOptions contains WITH_WAL_INCOMING_CLI

    // TODO(#877): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that the {@code --wal-incoming-rpc} flag does NOT set {@code WITH_WAL_INCOMING_CLI}.
   *
   * <p>The {@code --wal-incoming-rpc} and {@code --wal-incoming-cli} flags are independent; each
   * controls its own channel. Enabling one should not imply the other.
   *
   * <p>Acceptance criterion: [TEST:MainWalIncomingCliTest.walIncomingRpcFlag_doesNotSetCliOption]
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  @Ignore("Awaiting implementation in #877")
  public void walIncomingRpcFlag_doesNotSetCliOption() throws Exception {
    // Given: Main parsed with --wal-incoming-rpc --wal my-wal -k localhost:29092 (no
    //        --wal-incoming-cli)
    // When: validateInput() is invoked via reflection
    // Then: runOptions does NOT contain WITH_WAL_INCOMING_CLI (flags are independent)

    // TODO(#877): Implement test logic
    fail("Not yet implemented");
  }
}
