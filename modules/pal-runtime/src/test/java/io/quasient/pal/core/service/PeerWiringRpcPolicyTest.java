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
 * Tests for the RPC policy construction logic in {@link PeerWiring}.
 *
 * <p>Verifies that the {@code buildRpcPolicy()} method correctly constructs an {@code RpcPolicy}
 * from the configured properties (YAML path, presets, default action).
 */
public class PeerWiringRpcPolicyTest {

  /**
   * Tests that buildRpcPolicy returns a default DENY policy with no rules when no RPC policy
   * configuration is provided.
   */
  @Test
  @Ignore("Awaiting implementation in #1001")
  public void shouldBuildDefaultPolicyWhenNoConfigProvided() {
    // Given: No rpc.policy.path, no presets, default_action=DENY
    // When: buildRpcPolicy() called
    // Then: Returns policy with DENY default, empty rules

    // TODO(#1001): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that buildRpcPolicy parses rules from a YAML file when rpc.policy.path is provided. */
  @Test
  @Ignore("Awaiting implementation in #1001")
  public void shouldBuildPolicyFromYamlPath() {
    // Given: rpc.policy.path pointing to a temp YAML file with rules
    // When: buildRpcPolicy() called
    // Then: Returns policy with parsed rules

    // TODO(#1001): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that buildRpcPolicy applies preset rules when only rpc.policy.presets is provided. */
  @Test
  @Ignore("Awaiting implementation in #1001")
  public void shouldBuildPolicyFromPresetsOnly() {
    // Given: rpc.policy.presets="deny-unsafe", no YAML path
    // When: buildRpcPolicy() called
    // Then: Returns policy with deny-unsafe rules

    // TODO(#1001): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that buildRpcPolicy combines YAML rules and preset rules, with user rules from YAML
   * appearing first (higher priority) followed by preset rules.
   */
  @Test
  @Ignore("Awaiting implementation in #1001")
  public void shouldBuildPolicyFromYamlAndPresets() {
    // Given: Both YAML path and presets provided
    // When: buildRpcPolicy() called
    // Then: User rules from YAML come first, then presets

    // TODO(#1001): Implement test logic
    fail("Not yet implemented");
  }
}
