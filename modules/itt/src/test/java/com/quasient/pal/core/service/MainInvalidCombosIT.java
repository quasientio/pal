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

/** Integration tests for invalid CLI option combinations. */
public class MainInvalidCombosIT extends AbstractIntegrationTest {

  @Test
  public void testSourceLogWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPeer("--source-log", "someTopic");

    assertEquals(
        "Expected fatal exit for source-log without Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }
}
