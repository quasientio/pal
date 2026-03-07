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
 * Tests for the RPC policy CLI options in {@link Main}.
 *
 * <p>Verifies that {@code --rpc-policy}, {@code --rpc-policy-preset}, and {@code
 * --rpc-default-action} are correctly parsed and propagated into properties. Also verifies that the
 * removed {@code --rpc-allow-nonpublic} flag is no longer accepted.
 */
public class MainRpcPolicyTest {

  /** Tests that --rpc-policy sets the rpc.policy.path property. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldAcceptRpcPolicyFlag() {
    // Given: CLI args including --rpc-policy /path/to/policy.yaml
    // When: Main parses CLI
    // Then: rpcPolicyPath property is set to "/path/to/policy.yaml"

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that --rpc-policy-preset sets the rpc.policy.presets property. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldAcceptRpcPolicyPresetFlag() {
    // Given: CLI args including --rpc-policy-preset deny-unsafe,deny-jdk-internals
    // When: Main parses CLI
    // Then: rpcPolicyPresets property is set to "deny-unsafe,deny-jdk-internals"

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that --rpc-default-action accepts and stores the given value. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldAcceptRpcDefaultActionFlag() {
    // Given: CLI args including --rpc-default-action ALLOW
    // When: Main parses CLI
    // Then: rpcDefaultAction property is "ALLOW"

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that --rpc-default-action defaults to DENY when not specified. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldDefaultRpcDefaultActionToDeny() {
    // Given: CLI args without --rpc-default-action
    // When: Main parses CLI
    // Then: rpcDefaultAction defaults to "DENY"

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that the removed --rpc-allow-nonpublic flag is no longer accepted. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldNotHaveRpcAllowNonpublicFlag() {
    // Given: CLI args with --rpc-allow-nonpublic
    // When: Main parses CLI
    // Then: Parsing fails with ParameterException (flag has been removed)

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }

  /** Tests that all RPC policy flags are propagated as properties. */
  @Test
  @Ignore("Awaiting implementation in #999")
  public void shouldPropagateRpcPolicyProperties() {
    // Given: All RPC policy flags set (--rpc-policy, --rpc-policy-preset, --rpc-default-action)
    // When: addMiscProperties() runs (via validateInput)
    // Then: Properties contain rpc.policy.path, rpc.policy.presets, rpc.default_action

    // TODO(#999): Implement test logic
    fail("Not yet implemented");
  }
}
