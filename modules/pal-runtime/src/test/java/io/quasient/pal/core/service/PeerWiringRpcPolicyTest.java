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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.rpc.policy.RpcPolicy;
import io.quasient.pal.core.rpc.policy.RpcPolicyAction;
import io.quasient.pal.core.rpc.policy.RpcPolicyFileWatcher;
import io.quasient.pal.core.rpc.policy.RpcPolicyHolder;
import io.quasient.pal.core.rpc.policy.RpcPolicyPresets;
import java.io.IOException;
import java.lang.reflect.Field;
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
   * Tests that buildRpcPolicy returns a default DENY policy with no rules when no RPC policy
   * configuration is provided, so that peers deny all RPC operations unless explicitly allowed.
   */
  @Test
  public void shouldBuildDefaultPolicyWhenNoConfigProvided() {
    // Given: No rpc.policy.path, no presets, no default_action
    Properties props = baseProps();
    PeerWiring wiring = createWiring(props);

    // When
    RpcPolicy policy = wiring.provideRpcPolicy();

    // Then: Returns policy with DENY default, only mandatory rules
    assertThat(policy.getDefaultAction(), is(RpcPolicyAction.DENY));
    int mandatorySize = RpcPolicyPresets.getDenyPalInternalRules().size();
    assertThat(policy.getRules().size(), is(mandatorySize));
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

      // Then: Returns policy with mandatory + parsed rules and ALLOW default from YAML
      int m = RpcPolicyPresets.getDenyPalInternalRules().size();
      assertThat(policy.getRules().size(), is(m + 2));
      assertThat(policy.getRules().get(m).getAction(), is(RpcPolicyAction.ALLOW));
      assertThat(policy.getRules().get(m + 1).getAction(), is(RpcPolicyAction.DENY));
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

    // Then: Returns policy with mandatory + deny-unsafe rules
    int m = RpcPolicyPresets.getDenyPalInternalRules().size();
    int expectedRuleCount = m + RpcPolicyPresets.getDenyUnsafeRules().size();
    assertThat(policy.getRules().size(), is(expectedRuleCount));
    assertThat(policy.getRules().size(), is(greaterThan(0)));
    // All rules (mandatory + preset) should be DENY
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

      // Then: Mandatory rules first, then user rules from YAML, then presets
      int m = RpcPolicyPresets.getDenyPalInternalRules().size();
      int yamlRuleCount = 1;
      int presetRuleCount = RpcPolicyPresets.getDenyUnsafeRules().size();
      assertThat(policy.getRules().size(), is(m + yamlRuleCount + presetRuleCount));
      // First rule(s) are mandatory deny-pal-internals (DENY)
      assertThat(policy.getRules().get(0).getAction(), is(RpcPolicyAction.DENY));
      // Then YAML rule (ALLOW)
      assertThat(policy.getRules().get(m).getAction(), is(RpcPolicyAction.ALLOW));
      // Then preset rules (DENY)
      assertThat(policy.getRules().get(m + 1).getAction(), is(RpcPolicyAction.DENY));
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

  /**
   * Verifies that {@code PeerWiring} provides a bound {@link
   * io.quasient.pal.core.rpc.policy.RpcPolicyHolder} whose initial policy is the default deny-all
   * policy when no RPC policy configuration is provided.
   */
  @Test
  public void shouldProvideRpcPolicyHolder() {
    // Given: Standard base properties with no RPC policy config
    Properties props = baseProps();
    PeerWiring wiring = createWiring(props);

    // When: Construct holder from the provided policy
    RpcPolicy policy = wiring.provideRpcPolicy();
    RpcPolicyHolder holder = new RpcPolicyHolder(policy);

    // Then: RpcPolicyHolder is created and its getPolicy() returns the default deny-all policy
    // with only mandatory deny-pal-internals rules
    assertThat(holder, is(notNullValue()));
    assertThat(holder.getPolicy(), is(notNullValue()));
    assertThat(holder.getPolicy().getDefaultAction(), is(RpcPolicyAction.DENY));
    int mandatorySize = RpcPolicyPresets.getDenyPalInternalRules().size();
    assertThat(holder.getPolicy().getRules().size(), is(mandatorySize));
  }

  /**
   * Verifies that {@code provideRpcPolicyFileWatcher()} returns {@code null} when no {@code
   * rpc.policy.path} property is configured, since there is no file to watch.
   */
  @Test
  public void shouldProvideNullWatcherWhenNoPolicyPath() {
    // Given: Properties without rpc.policy.path
    Properties props = baseProps();
    PeerWiring wiring = createWiring(props);
    RpcPolicyHolder holder = new RpcPolicyHolder(wiring.provideRpcPolicy());

    // When: provideRpcPolicyFileWatcher() is called
    RpcPolicyFileWatcher watcher = wiring.provideRpcPolicyFileWatcher(holder);

    // Then: Returns null (no file to watch)
    assertThat(watcher, is(nullValue()));
  }

  /**
   * Verifies that {@code provideRpcPolicyFileWatcher()} returns a non-null {@link
   * io.quasient.pal.core.rpc.policy.RpcPolicyFileWatcher} when {@code rpc.policy.path} is set to a
   * valid temporary YAML file.
   */
  @Test
  public void shouldProvideWatcherWhenPolicyPathSet() throws IOException {
    // Given: Properties with rpc.policy.path set to a valid temp YAML file
    Path yamlFile = Files.createTempFile("rpc-policy-", ".yaml");
    try {
      Files.writeString(
          yamlFile,
          """
              version: 1
              defaultAction: ALLOW
              rules: []
              """);
      Properties props = baseProps();
      props.setProperty("rpc.policy.path", yamlFile.toString());
      PeerWiring wiring = createWiring(props);
      RpcPolicyHolder holder = new RpcPolicyHolder(wiring.provideRpcPolicy());

      // When: provideRpcPolicyFileWatcher() is called
      RpcPolicyFileWatcher watcher = wiring.provideRpcPolicyFileWatcher(holder);

      // Then: Returns a non-null RpcPolicyFileWatcher
      assertThat(watcher, is(notNullValue()));
    } finally {
      Files.deleteIfExists(yamlFile);
    }
  }

  /**
   * Verifies that {@code provideRpcPolicyFileWatcher()} respects a custom poll interval configured
   * via the {@code rpc.policy.watch.interval.ms} property.
   */
  @Test
  public void shouldRespectCustomPollInterval() throws Exception {
    // Given: Properties with rpc.policy.path and rpc.policy.watch.interval.ms=5000
    Path yamlFile = Files.createTempFile("rpc-policy-", ".yaml");
    try {
      Files.writeString(
          yamlFile,
          """
              version: 1
              defaultAction: ALLOW
              rules: []
              """);
      Properties props = baseProps();
      props.setProperty("rpc.policy.path", yamlFile.toString());
      props.setProperty("rpc.policy.watch.interval.ms", "5000");
      PeerWiring wiring = createWiring(props);
      RpcPolicyHolder holder = new RpcPolicyHolder(wiring.provideRpcPolicy());

      // When: provideRpcPolicyFileWatcher() is called
      RpcPolicyFileWatcher watcher = wiring.provideRpcPolicyFileWatcher(holder);

      // Then: Returns a watcher with the custom poll interval
      assertThat(watcher, is(notNullValue()));
      Field pollField = RpcPolicyFileWatcher.class.getDeclaredField("pollIntervalMs");
      pollField.setAccessible(true);
      assertThat(pollField.getLong(watcher), is(5000L));
    } finally {
      Files.deleteIfExists(yamlFile);
    }
  }

  /**
   * Verifies that {@code provideRpcPolicyFileWatcher()} returns {@code null} when the poll interval
   * is set to zero, effectively disabling file watching.
   */
  @Test
  public void shouldDisableWatcherWhenPollIntervalZero() throws IOException {
    // Given: Properties with rpc.policy.path and rpc.policy.watch.interval.ms=0
    Path yamlFile = Files.createTempFile("rpc-policy-", ".yaml");
    try {
      Files.writeString(
          yamlFile,
          """
              version: 1
              defaultAction: ALLOW
              rules: []
              """);
      Properties props = baseProps();
      props.setProperty("rpc.policy.path", yamlFile.toString());
      props.setProperty("rpc.policy.watch.interval.ms", "0");
      PeerWiring wiring = createWiring(props);
      RpcPolicyHolder holder = new RpcPolicyHolder(wiring.provideRpcPolicy());

      // When: provideRpcPolicyFileWatcher() is called
      RpcPolicyFileWatcher watcher = wiring.provideRpcPolicyFileWatcher(holder);

      // Then: Returns null (watching disabled)
      assertThat(watcher, is(nullValue()));
    } finally {
      Files.deleteIfExists(yamlFile);
    }
  }

  /** Creates a PeerWiring instance with the given properties. */
  private PeerWiring createWiring(Properties props) {
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    return new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
  }
}
