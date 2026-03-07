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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests for the RPC policy CLI options in {@link Main}.
 *
 * <p>Verifies that {@code --rpc-policy}, {@code --rpc-policy-preset}, and {@code
 * --rpc-default-action} are correctly parsed and propagated into properties. Also verifies that the
 * removed {@code --rpc-allow-nonpublic} flag is no longer accepted.
 */
public class MainRpcPolicyTest {

  /** Tests that --rpc-policy sets the rpcPolicyPath field. */
  @Test
  public void shouldAcceptRpcPolicyFlag() throws Exception {
    // Given: CLI args including --rpc-policy /path/to/policy.yaml
    Main main = new Main();
    new CommandLine(main).parseArgs("--rpc-policy", "/path/to/policy.yaml");

    // Then: rpcPolicyPath field is set to "/path/to/policy.yaml"
    Field field = Main.class.getDeclaredField("rpcPolicyPath");
    field.setAccessible(true);
    assertThat(field.get(main), is("/path/to/policy.yaml"));
  }

  /** Tests that --rpc-policy-preset sets the rpcPolicyPresets field. */
  @Test
  public void shouldAcceptRpcPolicyPresetFlag() throws Exception {
    // Given: CLI args including --rpc-policy-preset deny-unsafe,deny-jdk-internals
    Main main = new Main();
    new CommandLine(main).parseArgs("--rpc-policy-preset", "deny-unsafe,deny-jdk-internals");

    // Then: rpcPolicyPresets field is set to "deny-unsafe,deny-jdk-internals"
    Field field = Main.class.getDeclaredField("rpcPolicyPresets");
    field.setAccessible(true);
    assertThat(field.get(main), is("deny-unsafe,deny-jdk-internals"));
  }

  /** Tests that --rpc-default-action accepts and stores the given value. */
  @Test
  public void shouldAcceptRpcDefaultActionFlag() throws Exception {
    // Given: CLI args including --rpc-default-action ALLOW
    Main main = new Main();
    new CommandLine(main).parseArgs("--rpc-default-action", "ALLOW");

    // Then: rpcDefaultAction field is "ALLOW"
    Field field = Main.class.getDeclaredField("rpcDefaultAction");
    field.setAccessible(true);
    assertThat(field.get(main), is("ALLOW"));
  }

  /** Tests that --rpc-default-action defaults to ALLOW when not specified. */
  @Test
  public void shouldDefaultRpcDefaultActionToAllow() throws Exception {
    // Given: CLI args without --rpc-default-action
    Main main = new Main();
    new CommandLine(main).parseArgs();

    // Then: rpcDefaultAction defaults to "ALLOW"
    Field field = Main.class.getDeclaredField("rpcDefaultAction");
    field.setAccessible(true);
    assertThat(field.get(main), is("ALLOW"));
  }

  /** Tests that the removed --rpc-allow-nonpublic flag is no longer accepted. */
  @Test(expected = CommandLine.UnmatchedArgumentException.class)
  public void shouldNotHaveRpcAllowNonpublicFlag() {
    // Given: CLI args with --rpc-allow-nonpublic
    // When: Main parses CLI
    // Then: Parsing fails (flag has been removed)
    new CommandLine(new Main()).parseArgs("--rpc-allow-nonpublic");
  }

  /** Tests that all RPC policy flags are propagated as properties via addMiscProperties(). */
  @Test
  public void shouldPropagateRpcPolicyProperties() throws Exception {
    // Given: All RPC policy flags set
    Main main = new Main();
    new CommandLine(main)
        .parseArgs(
            "--rpc-policy", "/tmp/policy.yaml",
            "--rpc-policy-preset", "deny-unsafe,deny-jdk-internals",
            "--rpc-default-action", "ALLOW");

    // Set required uuid to avoid NPE in addMiscProperties
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, UUID.randomUUID());

    // When: addMiscProperties() runs
    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    // Then: Properties contain rpc.policy.path, rpc.policy.presets, rpc.default_action
    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    assertThat(properties.getProperty("rpc.policy.path"), is("/tmp/policy.yaml"));
    assertThat(properties.getProperty("rpc.policy.presets"), is("deny-unsafe,deny-jdk-internals"));
    assertThat(properties.getProperty("rpc.default_action"), is("ALLOW"));
  }

  /** Tests that optional properties are not set when CLI flags are omitted. */
  @Test
  public void shouldNotSetOptionalPropertiesWhenFlagsOmitted() throws Exception {
    // Given: No RPC policy flags set (only defaults)
    Main main = new Main();
    new CommandLine(main).parseArgs();

    // Set required uuid
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, UUID.randomUUID());

    // When: addMiscProperties() runs
    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    // Then: Optional properties are absent, default action is ALLOW
    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    assertThat(properties.getProperty("rpc.policy.path"), is(nullValue()));
    assertThat(properties.getProperty("rpc.policy.presets"), is(nullValue()));
    assertThat(properties.getProperty("rpc.default_action"), is("ALLOW"));
  }
}
