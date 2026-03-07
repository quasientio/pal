/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.core.transport.MessageChannelType;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link RpcPolicyPresets}, verifying that built-in deny-list presets correctly
 * block dangerous operations and do not produce false positives on unrelated classes.
 *
 * <p>Each preset category (deny-unsafe, deny-jdk-internals, deny-classloading, deny-reflection,
 * deny-serialization, deny-scripting, deny-pal-internals) is tested against representative target
 * patterns. The field-access bypass scenario is specifically verified for {@code ProcessBuilder} by
 * testing all member categories.
 */
public class RpcPolicyPresetsTest {

  /** Verifies that the deny-unsafe preset blocks {@code System.exit}. */
  @Test
  public void denyUnsafeShouldBlockSystemExit() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyUnsafeRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.System.exit",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that the deny-unsafe preset blocks {@code Runtime.exec}. */
  @Test
  public void denyUnsafeShouldBlockRuntimeExec() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyUnsafeRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.Runtime.exec",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /**
   * Verifies that the deny-unsafe preset blocks all member types on {@code ProcessBuilder}
   * (field-access bypass prevention). Methods, constructors, and field access must all be denied.
   */
  @Test
  public void denyUnsafeShouldBlockProcessBuilderAllMembers() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyUnsafeRules();

    // Method access denied
    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.ProcessBuilder.start",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
    // Field access denied (prevents bypass via field reference)
    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.ProcessBuilder.command",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.FIELD_GET));
    // Constructor denied
    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.ProcessBuilder.ProcessBuilder",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.CONSTRUCTOR));

    // Also verify Process.** blocks all member types
    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.Process.waitFor",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.Process.exitValue",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.FIELD_GET));
  }

  /** Verifies that the deny-jdk-internals preset blocks {@code com.sun} packages. */
  @Test
  public void denyJdkInternalsShouldBlockComSun() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyJdkInternalRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "com.sun.internal.Foo.bar",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that the deny-classloading preset blocks {@code Class.forName}. */
  @Test
  public void denyClassloadingShouldBlockClassForName() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyClassloadingRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.Class.forName",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.STATIC_METHOD));
  }

  /** Verifies that the deny-reflection preset blocks {@code java.lang.reflect} package. */
  @Test
  public void denyReflectionShouldBlockReflectPackage() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyReflectionRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "java.lang.reflect.Method.invoke",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that the deny-serialization preset blocks {@code ObjectInputStream}. */
  @Test
  public void denySerializationShouldBlockObjectInputStream() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenySerializationRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "java.io.ObjectInputStream.readObject",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that the deny-scripting preset blocks {@code ScriptEngine}. */
  @Test
  public void denyScriptingShouldBlockScriptEngine() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyScriptingRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "javax.script.ScriptEngine.eval",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that the deny-pal-internals preset blocks PAL core classes. */
  @Test
  public void denyPalInternalsShouldBlockPalCore() {
    List<RpcPolicyRule> rules = RpcPolicyPresets.getDenyPalInternalRules();

    assertTrue(
        anyRuleMatches(
            rules,
            "io.quasient.pal.core.Main.run",
            MessageChannelType.ZMQ_SOCKET_RPC,
            MemberCategory.METHOD));
  }

  /** Verifies that no preset rules produce false positives on unrelated application classes. */
  @Test
  public void presetRulesShouldNotBlockUnrelatedClasses() {
    for (String presetName : RpcPolicyPresets.allPresetNames()) {
      List<RpcPolicyRule> rules = RpcPolicyPresets.resolvePreset(presetName);
      assertFalse(
          "Preset " + presetName + " should not match com.example.MyApp.doSomething",
          anyRuleMatches(
              rules,
              "com.example.MyApp.doSomething",
              MessageChannelType.ZMQ_SOCKET_RPC,
              MemberCategory.METHOD));
    }
  }

  /** Verifies that {@code resolvePreset} maps preset names to rule lists. */
  @Test
  public void resolvePresetShouldReturnRulesForKnownPresets() {
    for (String name : RpcPolicyPresets.allPresetNames()) {
      List<RpcPolicyRule> rules = RpcPolicyPresets.resolvePreset(name);
      assertFalse("Preset " + name + " should have rules", rules.isEmpty());
      for (RpcPolicyRule rule : rules) {
        assertThat("All preset rules should be DENY", rule.getAction(), is(RpcPolicyAction.DENY));
      }
    }
  }

  /** Verifies that {@code resolvePreset} throws for unknown presets. */
  @Test(expected = IllegalArgumentException.class)
  public void resolvePresetShouldThrowForUnknownPreset() {
    RpcPolicyPresets.resolvePreset("nonexistent-preset");
  }

  /** Verifies that {@code allPresetNames} returns all 7 preset categories. */
  @Test
  public void allPresetNamesShouldReturnSevenPresets() {
    assertThat(RpcPolicyPresets.allPresetNames().size(), is(7));
  }

  /**
   * Checks whether any rule in the list matches the given path, channel, and member category.
   *
   * @param rules the rules to test
   * @param classMethodPath the fully-qualified class.method path
   * @param channel the message channel
   * @param category the member category
   * @return {@code true} if any rule matches
   */
  private static boolean anyRuleMatches(
      List<RpcPolicyRule> rules,
      String classMethodPath,
      MessageChannelType channel,
      MemberCategory category) {
    return rules.stream().anyMatch(rule -> rule.matches(classMethodPath, channel, category));
  }
}
