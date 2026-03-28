/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.AbstractIntegrationTest;
import org.junit.Test;

/** Integration tests for invalid CLI input combinations that trigger fatal exits. */
public class MainInvalidInputIT extends AbstractIntegrationTest {

  @Test
  public void testStartOffsetWithoutLog_fatalExitNoLogGiven() throws Exception {
    ProcessResult result = runPeer("--start-offset", "5");

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
    ProcessResult result = runPeer("--zmq-rpc", "abc");

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
    ProcessResult result = runPeer("--json-rpc", "abc");

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
