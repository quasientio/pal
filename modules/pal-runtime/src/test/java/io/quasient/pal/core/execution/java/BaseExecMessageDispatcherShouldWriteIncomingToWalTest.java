/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.junit.Assert.fail;

import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for the {@code shouldWriteIncomingToWal(MessageChannelType)} helper method in {@link
 * BaseExecMessageDispatcher}.
 *
 * <p>These tests verify the decision logic that determines whether incoming messages should be
 * written to WAL/PUB. The helper considers three factors:
 *
 * <ol>
 *   <li>{@link RunOptions} flags ({@code WITH_WAL}, {@code WITH_TCP_PUB}, {@code
 *       WITH_WAL_INCOMING_RPC}, {@code WITH_WAL_ALL_INCOMING_RPC})
 *   <li>{@link MessageChannelType} of the incoming message
 *   <li>The {@code sourceAndWalAreSameLog} circularity guard
 * </ol>
 *
 * <p>Uses the {@code MinimalOk} pattern from {@link BaseExecMessageDispatcherDispatchTest} to
 * create a concrete subclass of {@link BaseExecMessageDispatcher}, then uses reflection to set
 * {@code runOptions} and the {@code sourceAndWalAreSameLog} field, and invokes the helper method.
 *
 * @see BaseExecMessageDispatcher
 * @see RunOptions#WITH_WAL_INCOMING_RPC
 * @see RunOptions#WITH_WAL_ALL_INCOMING_RPC
 */
public class BaseExecMessageDispatcherShouldWriteIncomingToWalTest {

  /**
   * Reflection helper to set {@code runOptions} on the dispatcher under test.
   *
   * @param d the dispatcher instance
   * @param ro the set of run options to inject
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod") // Will be used when #774 removes @Ignore
  private static void setRunOptions(AbstractDispatcher d, Set<RunOptions> ro) throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("runOptions");
    f.setAccessible(true);
    f.set(d, ro);
  }

  /**
   * Reflection helper to set {@code sourceAndWalAreSameLog} on the dispatcher under test.
   *
   * @param d the dispatcher instance
   * @param sameLog whether source log and WAL are the same
   * @throws Exception if reflection fails
   */
  @SuppressWarnings("UnusedMethod") // Will be used when #774 removes @Ignore
  private static void setSourceAndWalAreSameLog(AbstractDispatcher d, boolean sameLog)
      throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("sourceAndWalAreSameLog");
    f.setAccessible(true);
    f.setBoolean(d, sameLog);
  }

  // ---------------------------------------------------------------
  // Test 1: No incoming WAL without explicit opt-in
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withoutWalIncomingRpc_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_WAL} (no WITH_WAL_INCOMING_RPC)
    // When:  shouldWriteIncomingToWal(WEBSOCKET_RPC)
    // Then:  returns false

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 2: ZMQ RPC writes to WAL when opted in
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalIncomingRpc_zmqSocket_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    // When:  shouldWriteIncomingToWal(ZMQ_SOCKET_RPC)
    // Then:  returns true

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 3: Websocket RPC writes to WAL when opted in
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalIncomingRpc_websocket_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    // When:  shouldWriteIncomingToWal(WEBSOCKET_RPC)
    // Then:  returns true

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 4: CLI RPC writes to WAL when opted in
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalIncomingRpc_cliRpc_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC}
    // When:  shouldWriteIncomingToWal(CLI_RPC)
    // Then:  returns true

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 5: LOG_RPC excluded without ALL flag
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalIncomingRpc_logRpc_returnsFalse() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC} (no WITH_WAL_ALL_INCOMING_RPC)
    // When:  shouldWriteIncomingToWal(LOG_RPC)
    // Then:  returns false (LOG_RPC excluded without ALL flag)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 6: LOG_RPC included with ALL flag when logs differ
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_logRpc_returnsTrue() throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = false
    // When:  shouldWriteIncomingToWal(LOG_RPC)
    // Then:  returns true

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 7: Circularity guard blocks LOG_RPC when same log
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_returnsFalse()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    // When:  shouldWriteIncomingToWal(LOG_RPC)
    // Then:  returns false (circularity guard overrides WITH_WAL_ALL_INCOMING_RPC)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 8: Circularity guard only affects LOG_RPC
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_websocket_returnsTrue()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    // When:  shouldWriteIncomingToWal(WEBSOCKET_RPC)
    // Then:  returns true (circularity guard only affects LOG_RPC)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 9: TCP PUB also triggers incoming WAL
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withTcpPubOnly_withWalIncomingRpc_returnsTrue()
      throws Exception {
    // Given: runOptions = {WITH_TCP_PUB, WITH_WAL_INCOMING_RPC} (no WITH_WAL)
    // When:  shouldWriteIncomingToWal(WEBSOCKET_RPC)
    // Then:  returns true (WITH_TCP_PUB also triggers the send path)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 10: No destination means no write
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_noPubNorWal_withWalIncomingRpc_returnsFalse()
      throws Exception {
    // Given: runOptions = {WITH_WAL_INCOMING_RPC} (no WITH_WAL, no WITH_TCP_PUB)
    // When:  shouldWriteIncomingToWal(WEBSOCKET_RPC)
    // Then:  returns false (no destination for messages)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }

  // ---------------------------------------------------------------
  // Test 11: Warning logged for circularity override
  // ---------------------------------------------------------------

  @Test
  @Ignore("Awaiting implementation in #774")
  public void shouldWriteIncomingToWal_withWalAllIncomingRpc_sameLog_logsWarning()
      throws Exception {
    // Given: runOptions = {WITH_WAL, WITH_WAL_INCOMING_RPC, WITH_WAL_ALL_INCOMING_RPC}
    //        sourceAndWalAreSameLog = true
    // When:  shouldWriteIncomingToWal(LOG_RPC)
    // Then:  returns false AND a warning is logged (verify using a log appender or similar)

    // TODO(#774): Implement test logic
    fail("Not yet implemented");
  }
}
