/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.quasient.pal.AbstractIntegrationTest;
import org.junit.Test;

/** Integration tests for invalid CLI input combinations that trigger fatal exits. */
public class MainInvalidInputIT extends AbstractIntegrationTest {

  @Test
  public void testStartOffsetWithoutLog_fatalExitNoLogGiven() throws Exception {
    ProcessResult result = runPalCommand("--start-offset", "5");

    assertEquals(
        "Expected fatal exit for start-offset without log",
        PeerException.FatalCode.ERROR_NO_LOG_GIVEN.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_NO_LOG_GIVEN.getMessage()));
  }

  @Test
  public void testInvalidZmqRpcPort_fatalExitParsingZmqPort() throws Exception {
    ProcessResult result = runPalCommand("--zmq-rpc", "abc");

    assertEquals(
        "Expected fatal exit for invalid ZMQ RPC port",
        PeerException.FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getMessage()));
  }

  @Test
  public void testInvalidJsonRpcPort_fatalExitParsingJsonRpcPort() throws Exception {
    ProcessResult result = runPalCommand("--json-rpc", "abc");

    assertEquals(
        "Expected fatal exit for invalid JSON RPC port",
        PeerException.FatalCode.ERROR_PARSING_JSON_RPC_PORT_NUMBER.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_PARSING_JSON_RPC_PORT_NUMBER.getMessage()));
  }
}
