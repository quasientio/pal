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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import io.quasient.pal.core.rpc.policy.RpcPolicyPresets;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Tests for the RPC policy construction logic in {@link PeerWiring}.
 *
 * <p>Verifies that the {@code buildRpcPolicy()} method correctly constructs an {@code RpcPolicy}
 * from the configured properties (YAML path, presets, default action).
 */
public class PeerWiringRpcPolicyTest {

  /** ZeroMQ context required by PeerWiring. */
  private ZContext ctx;

  @Before
  public void setUp() {
    ctx = new ZContext(1);
  }

  @After
  public void tearDown() {
    ctx.close();
  }

  /**
   * Tests that buildRpcPolicy returns a default ALLOW policy with no rules when no RPC policy
   * configuration is provided, preserving the pre-policy behavior where all RPC operations were
   * allowed.
   */
  @Test
  public void shouldBuildDefaultPolicyWhenNoConfigProvided() {
    // Given: No rpc.policy.path, no presets, no default_action
    Properties props = baseProps();
    PeerWiring wiring = createWiring(props);

    // When
    RpcPolicy policy = wiring.provideRpcPolicy();

    // Then: Returns policy with ALLOW default, empty rules
    assertThat(policy.getDefaultAction(), is(RpcPolicyAction.ALLOW));
    assertThat(policy.getRules(), is(empty()));
  }

  /** Tests that buildRpcPolicy parses rules from a YAML file when rpc.policy.path is provided. */
  @Test
  public void shouldBuildPolicyFromYamlPath() throws IOException {
    // Given: rpc.policy.path pointing to a temp YAML file with rules
    Path yamlFile = Files.createTempFile("rpc-policy-", ".yaml");
    try {
      Files.writeString(
          yamlFile,
          """
              version: 1
              defaultAction: ALLOW
              rules:
                - pattern: "com.example.api.**"
                  action: ALLOW
                - pattern: "com.example.internal.**"
                  action: DENY
              """);

      Properties props = baseProps();
      props.setProperty("rpc.policy.path", yamlFile.toString());
      PeerWiring wiring = createWiring(props);

      // When
      RpcPolicy policy = wiring.provideRpcPolicy();

      // Then: Returns policy with parsed rules and ALLOW default from YAML
      assertThat(policy.getRules().size(), is(2));
      assertThat(policy.getRules().get(0).getAction(), is(RpcPolicyAction.ALLOW));
      assertThat(policy.getRules().get(1).getAction(), is(RpcPolicyAction.DENY));
      assertThat(policy.getDefaultAction(), is(RpcPolicyAction.ALLOW));
    } finally {
      Files.deleteIfExists(yamlFile);
    }
  }

  /** Tests that buildRpcPolicy applies preset rules when only rpc.policy.presets is provided. */
  @Test
  public void shouldBuildPolicyFromPresetsOnly() {
    // Given: rpc.policy.presets="deny-unsafe", no YAML path
    Properties props = baseProps();
    props.setProperty("rpc.policy.presets", "deny-unsafe");
    PeerWiring wiring = createWiring(props);

    // When
    RpcPolicy policy = wiring.provideRpcPolicy();

    // Then: Returns policy with deny-unsafe rules
    int expectedRuleCount = RpcPolicyPresets.getDenyUnsafeRules().size();
    assertThat(policy.getRules().size(), is(expectedRuleCount));
    assertThat(policy.getRules().size(), is(greaterThan(0)));
    // All preset rules should be DENY
    for (int i = 0; i < policy.getRules().size(); i++) {
      assertThat(policy.getRules().get(i).getAction(), is(RpcPolicyAction.DENY));
    }
  }

  /**
   * Tests that buildRpcPolicy combines YAML rules and preset rules, with user rules from YAML
   * appearing first (higher priority) followed by preset rules.
   */
  @Test
  public void shouldBuildPolicyFromYamlAndPresets() throws IOException {
    // Given: Both YAML path and presets provided
    Path yamlFile = Files.createTempFile("rpc-policy-", ".yaml");
    try {
      Files.writeString(
          yamlFile,
          """
              version: 1
              defaultAction: DENY
              rules:
                - pattern: "com.example.api.**"
                  action: ALLOW
              """);

      Properties props = baseProps();
      props.setProperty("rpc.policy.path", yamlFile.toString());
      props.setProperty("rpc.policy.presets", "deny-unsafe");
      PeerWiring wiring = createWiring(props);

      // When
      RpcPolicy policy = wiring.provideRpcPolicy();

      // Then: User rules from YAML come first, then presets
      int yamlRuleCount = 1;
      int presetRuleCount = RpcPolicyPresets.getDenyUnsafeRules().size();
      assertThat(policy.getRules().size(), is(yamlRuleCount + presetRuleCount));
      // First rule is from YAML (ALLOW)
      assertThat(policy.getRules().get(0).getAction(), is(RpcPolicyAction.ALLOW));
      // Remaining rules are from preset (DENY)
      assertThat(policy.getRules().get(1).getAction(), is(RpcPolicyAction.DENY));
      assertThat(policy.getRules(), is(not(empty())));
    } finally {
      Files.deleteIfExists(yamlFile);
    }
  }

  /** Creates base properties required by PeerWiring. */
  private Properties baseProps() {
    Properties p = new Properties();
    p.setProperty("id", UUID.randomUUID().toString());
    p.setProperty("wal.queue.type", "CHUNKED");
    p.setProperty("wal.queue.initial", "1024");
    p.setProperty("wal.queue.max", "2048");
    p.setProperty("pub.queue.type", "CHUNKED");
    p.setProperty("pub.queue.initial", "1024");
    p.setProperty("pub.queue.max", "2048");
    return p;
  }

  /** Creates a PeerWiring instance with the given properties. */
  private PeerWiring createWiring(Properties props) {
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    return new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
  }
}
