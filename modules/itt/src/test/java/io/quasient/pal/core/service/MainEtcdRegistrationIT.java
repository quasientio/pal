/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
