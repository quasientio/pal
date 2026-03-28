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

/**
 * Integration tests for etcd registration errors in Main.
 *
 * <p>Tests error handling when peer registration or log registration fails due to unreachable PAL
 * Directory (etcd). These tests verify that the preflight health check (EtcdHealthCheck) causes
 * fast failure instead of hanging indefinitely.
 *
 * <p>Note: The preflight health check is tested directly in {@link
 * io.quasient.pal.cxn.directory.PalDirectoryConnectionIT}.
 */
public class MainEtcdRegistrationIT extends AbstractIntegrationTest {

  @Test
  public void testRegisterSelfWithUnreachableEtcd_fatalExitUnreachableEtcd() throws Exception {
    String unreachableEtcd = "192.0.2.1:2379";
    AbstractIntegrationTest.ProcessResult result =
        runPeerWithEnv(unreachableEtcd, "--dir", unreachableEtcd, "com.example.DummyMain");

    assertEquals(
        "Expected fatal exit for unreachable etcd",
        PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getMessage()));
  }

  @Test
  public void testRegisterLogsWithUnreachableEtcd_fatalExitUnreachableEtcd() throws Exception {
    // Run with --dir pointing to unreachable etcd, and valid Kafka servers
    // Use 192.0.2.1 (TEST-NET-1, non-routable) to simulate truly unreachable etcd
    String unreachableEtcd = "192.0.2.1:2379";
    String kafka = getKafkaServersOrDefault("kafka:9092");
    ProcessResult result =
        runPeerWithEnv(
            unreachableEtcd,
            "--dir",
            unreachableEtcd,
            "--log",
            "test-log",
            "--kafka-servers",
            kafka,
            "com.example.DummyMain");

    // Expect etcd preflight to fail and terminate with ERROR_UNREACHABLE_ETCD
    assertEquals(
        "Expected fatal exit for unreachable etcd during logs registration",
        PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getMessage()));
  }
}
