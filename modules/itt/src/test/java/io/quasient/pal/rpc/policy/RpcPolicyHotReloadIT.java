/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.policy;

import static org.junit.Assert.fail;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.messages.types.RpcType;
import java.util.Collection;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for end-to-end RPC policy hot-reload behavior with real peer processes.
 *
 * <p>These tests verify that modifying the YAML policy file at runtime changes RPC access decisions
 * without restarting the peer. Each test starts a peer with {@code --rpc-policy} and {@code
 * --rpc-policy-watch-interval 500} for fast polling, then modifies the policy file and verifies the
 * new policy takes effect.
 *
 * <p>Tests are parameterized to run against both ZMQ-RPC and JSON-RPC transports, matching the
 * pattern established by {@link RpcPolicyIT}.
 *
 * <p><b>Infrastructure requirements:</b> etcd (Docker), Kafka (Docker), test application JARs from
 * itt-apps module.
 */
@RunWith(Parameterized.class)
@SuppressWarnings("UnusedVariable") // rpcType will be used when tests are implemented in #1140
public class RpcPolicyHotReloadIT extends AbstractIntegrationTest {

  /** Temporary folder for writing policy YAML files, shared across all test instances. */
  @ClassRule public static TemporaryFolder tempFolder = new TemporaryFolder();

  /** The RPC transport type under test, injected by the parameterized runner. */
  private final RpcType rpcType;

  /**
   * Returns the parameter combinations for the parameterized runner.
   *
   * @return a collection of single-element arrays, each containing an {@link RpcType}
   */
  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> transports() {
    return List.of(new Object[] {RpcType.ZMQ_RPC}, new Object[] {RpcType.JSON_RPC});
  }

  /**
   * Constructs a parameterized test instance for the given RPC transport type.
   *
   * @param rpcType the RPC transport to use for this test run
   */
  public RpcPolicyHotReloadIT(RpcType rpcType) {
    this.rpcType = rpcType;
  }

  /**
   * Verifies that a policy change from deny to allow takes effect without restarting the peer.
   *
   * <p>Starts a peer with a policy that denies {@code com.example.**} and a default DENY action,
   * verifies an RPC call is denied, then modifies the YAML to allow the class and verifies the same
   * RPC call succeeds after the file watcher picks up the change.
   */
  @Test
  @Ignore("Awaiting implementation in #1140")
  public void shouldDenyThenAllowAfterPolicyFileChange() {
    // Given: Peer started with policy YAML that denies com.example.**, default DENY,
    //        poll interval 500ms
    // When: RPC call to a denied class method is made
    // Then: The call is denied (RpcAccessDeniedException)
    // When: YAML file is modified to allow com.example.**, wait ~2 seconds for reload
    // Then: Same RPC call now succeeds (policy hot-reloaded)
    // Cleanup: stop peer, close connections

    // TODO(#1140): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that a policy change from allow to deny takes effect without restarting the peer.
   *
   * <p>Starts a peer with a policy that allows {@code com.example.**} and a default ALLOW action,
   * verifies an RPC call succeeds, then modifies the YAML to deny the class and verifies the same
   * RPC call is denied after the file watcher picks up the change.
   */
  @Test
  @Ignore("Awaiting implementation in #1140")
  public void shouldAllowThenDenyAfterPolicyFileChange() {
    // Given: Peer started with policy YAML that allows com.example.**, default ALLOW,
    //        poll interval 500ms
    // When: RPC call to an allowed class method is made
    // Then: The call succeeds
    // When: YAML file is modified to deny com.example.**, wait ~2 seconds for reload
    // Then: Same RPC call is now denied (RpcAccessDeniedException)
    // Cleanup: stop peer, close connections

    // TODO(#1140): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the current policy is retained when an invalid YAML edit is written to the policy
   * file.
   *
   * <p>Starts a peer with a policy that allows a class, verifies an RPC call succeeds, then
   * overwrites the YAML with invalid content. After the watcher attempts to reload, the same RPC
   * call should still succeed because the current policy is retained on parse errors. The peer log
   * should contain an ERROR about the failed reload.
   */
  @Test
  @Ignore("Awaiting implementation in #1140")
  public void shouldKeepCurrentPolicyOnInvalidYamlEdit() {
    // Given: Peer started with policy YAML that allows com.example.**, poll interval 500ms
    // When: RPC call to an allowed class method is made
    // Then: The call succeeds
    // When: YAML file is overwritten with invalid content (e.g., "{{not valid yaml"),
    //       wait ~2 seconds for reload attempt
    // Then: Same RPC call still succeeds (current policy retained)
    // Verify: Peer log contains ERROR about failed reload
    // Cleanup: stop peer

    // TODO(#1140): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that class metadata reflects a reloaded policy after a policy file change.
   *
   * <p>Starts a peer with a policy that denies a specific class, requests metadata and verifies the
   * class is not included, then modifies the YAML to allow the class. After the file watcher
   * reloads the policy, a metadata request should now include the class. This verifies that {@code
   * ClassMetadataSerializer} also picks up the reloaded policy via {@code RpcPolicyHolder}.
   */
  @Test
  @Ignore("Awaiting implementation in #1140")
  public void shouldReloadMetadataAfterPolicyChange() {
    // Given: Peer started with policy YAML that denies a specific class, poll interval 500ms
    // When: Metadata request is made for the denied class
    // Then: Class is not present in metadata response
    // When: YAML file is modified to allow the class, wait ~2 seconds for reload
    // Then: Same metadata request now includes the class
    // Note: This verifies ClassMetadataSerializer also picks up the reloaded policy
    // Cleanup: stop peer, close connections

    // TODO(#1140): Implement test logic
    fail("Not yet implemented");
  }
}
