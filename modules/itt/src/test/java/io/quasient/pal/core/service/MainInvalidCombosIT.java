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
