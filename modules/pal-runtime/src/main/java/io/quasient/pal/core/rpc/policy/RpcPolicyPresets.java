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

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in deny-list presets for common dangerous operation categories.
 *
 * <p>Each preset returns a list of {@link RpcPolicyRule} instances with {@link
 * RpcPolicyAction#DENY} action. Presets are opt-in via the {@code --rpc-policy-preset} CLI flag or
 * the {@code presets} section in the YAML policy file.
 *
 * <p>Preset categories:
 *
 * <ul>
 *   <li><b>deny-unsafe:</b> System.exit, Runtime.exec/halt/load, ProcessBuilder.**, Process.**,
 *       Thread.stop/suspend/resume, ThreadGroup.destroy
 *   <li><b>deny-jdk-internals:</b> com.sun.**, sun.**, jdk.**
 *   <li><b>deny-classloading:</b> ClassLoader.**, URLClassLoader.**, Class.forName/newInstance
 *   <li><b>deny-reflection:</b> java.lang.reflect.**, java.lang.invoke.**
 *   <li><b>deny-serialization:</b> java.io.ObjectInputStream.**
 *   <li><b>deny-scripting:</b> javax.script.**
 *   <li><b>deny-pal-internals:</b> io.quasient.pal.** — <b>always ON</b>, cannot be disabled via
 *       CLI or YAML policy. See below.
 *   <li><b>deny-nonpublic:</b> all non-public members (protected, package-private, private)
 * </ul>
 *
 * <p><b>Mandatory preset — deny-pal-internals:</b> The {@code deny-pal-internals} rules are always
 * enforced by {@link RpcPolicy}, which prepends them before any user-supplied rules. Because rule
 * evaluation is first-match-wins, user ALLOW rules targeting {@code io.quasient.pal.**} are
 * unreachable. Setting {@code deny-pal-internals: false} in a YAML policy file has no effect. The
 * preset remains available in the preset map for documentation and tooling purposes, but enabling
 * or disabling it is a no-op — the rules are always active.
 *
 * <p><b>Field access bypass prevention:</b> ProcessBuilder and Process use {@code **} for the
 * member pattern to deny ALL member types (methods, constructors, fields), preventing bypass via
 * field access to sensitive objects.
 */
public final class RpcPolicyPresets {

  /** Map from preset name to the supplier that produces its rules. */
  private static final Map<String, List<RpcPolicyRule>> PRESET_MAP;

  static {
    Map<String, List<RpcPolicyRule>> map = new LinkedHashMap<>();
    map.put("deny-unsafe", getDenyUnsafeRules());
    map.put("deny-jdk-internals", getDenyJdkInternalRules());
    map.put("deny-classloading", getDenyClassloadingRules());
    map.put("deny-reflection", getDenyReflectionRules());
    map.put("deny-serialization", getDenySerializationRules());
    map.put("deny-scripting", getDenyScriptingRules());
    map.put("deny-pal-internals", getDenyPalInternalRules());
    map.put("deny-nonpublic", getDenyNonpublicRules());
    PRESET_MAP = Collections.unmodifiableMap(map);
  }

  /** Private constructor to prevent instantiation of this utility class. */
  private RpcPolicyPresets() {}

  /**
   * Returns deny rules for dangerous system operations.
   *
   * <p>Blocks:
   *
   * <ul>
   *   <li>{@code System.exit}, {@code System.setSecurityManager}
   *   <li>{@code Runtime.exec}, {@code Runtime.halt}, {@code Runtime.addShutdownHook}, {@code
   *       Runtime.load}, {@code Runtime.loadLibrary}
   *   <li>{@code ProcessBuilder.**} (all members, prevents field access bypass)
   *   <li>{@code Process.**} (all members, prevents field access bypass)
   *   <li>{@code Thread.stop}, {@code Thread.suspend}, {@code Thread.resume}
   *   <li>{@code ThreadGroup.destroy}
   * </ul>
   *
   * @return an unmodifiable list of deny rules for unsafe operations
   */
  public static List<RpcPolicyRule> getDenyUnsafeRules() {
    return List.of(
        deny("java.lang.System", "exit"),
        deny("java.lang.System", "setSecurityManager"),
        deny("java.lang.Runtime", "exec"),
        deny("java.lang.Runtime", "halt"),
        deny("java.lang.Runtime", "addShutdownHook"),
        deny("java.lang.Runtime", "load"),
        deny("java.lang.Runtime", "loadLibrary"),
        deny("java.lang.ProcessBuilder", "**"),
        deny("java.lang.Process", "**"),
        deny("java.lang.Thread", "stop"),
        deny("java.lang.Thread", "suspend"),
        deny("java.lang.Thread", "resume"),
        deny("java.lang.ThreadGroup", "destroy"));
  }

  /**
   * Returns deny rules for JDK-internal packages.
   *
   * <p>Blocks all members in {@code com.sun.**}, {@code sun.**}, and {@code jdk.**} packages.
   *
   * @return an unmodifiable list of deny rules for JDK internals
   */
  public static List<RpcPolicyRule> getDenyJdkInternalRules() {
    return List.of(deny("com.sun.**", "**"), deny("sun.**", "**"), deny("jdk.**", "**"));
  }

  /**
   * Returns deny rules for classloading operations.
   *
   * <p>Blocks all members of {@code ClassLoader} and {@code URLClassLoader}, plus {@code
   * Class.forName} and {@code Class.newInstance}.
   *
   * @return an unmodifiable list of deny rules for classloading operations
   */
  public static List<RpcPolicyRule> getDenyClassloadingRules() {
    return List.of(
        deny("java.lang.ClassLoader", "**"),
        deny("java.net.URLClassLoader", "**"),
        deny("java.lang.Class", "forName"),
        deny("java.lang.Class", "newInstance"));
  }

  /**
   * Returns deny rules for reflection and method-handle operations.
   *
   * <p>Blocks all members in {@code java.lang.reflect.**} and {@code java.lang.invoke.**}.
   *
   * @return an unmodifiable list of deny rules for reflection operations
   */
  public static List<RpcPolicyRule> getDenyReflectionRules() {
    return List.of(deny("java.lang.reflect.**", "**"), deny("java.lang.invoke.**", "**"));
  }

  /**
   * Returns deny rules for deserialization operations.
   *
   * <p>Blocks all members of {@code java.io.ObjectInputStream}.
   *
   * @return an unmodifiable list of deny rules for serialization operations
   */
  public static List<RpcPolicyRule> getDenySerializationRules() {
    return List.of(deny("java.io.ObjectInputStream", "**"));
  }

  /**
   * Returns deny rules for scripting engine operations.
   *
   * <p>Blocks all members in {@code javax.script.**}.
   *
   * @return an unmodifiable list of deny rules for scripting operations
   */
  public static List<RpcPolicyRule> getDenyScriptingRules() {
    return List.of(deny("javax.script.**", "**"));
  }

  /**
   * Returns deny rules for PAL internal packages.
   *
   * <p>Blocks all members in {@code io.quasient.pal.**}. This covers every PAL subpackage (core,
   * weave, common, cxn, dsl, messages, serdes, tools) and any future subpackages, ensuring that
   * remote callers cannot invoke PAL runtime internals. User application classes outside the {@code
   * io.quasient.pal} namespace are unaffected.
   *
   * <p><b>Note:</b> These rules are <em>always enforced</em> by {@link RpcPolicy}, which prepends
   * them as mandatory rules before any user-supplied rules. Explicitly enabling or disabling this
   * preset via CLI or YAML has no effect on enforcement.
   *
   * @return an unmodifiable list of deny rules for PAL internals
   */
  public static List<RpcPolicyRule> getDenyPalInternalRules() {
    return List.of(deny("io.quasient.pal.**", "**"));
  }

  /**
   * Returns a deny rule for all non-public member visibilities.
   *
   * <p>Blocks any RPC call targeting a protected, package-private, or private member, regardless of
   * class pattern, channel, or member category. Since presets are evaluated after user rules,
   * explicit ALLOW rules for specific non-public members take precedence.
   *
   * @return an unmodifiable list containing a single deny rule for non-public visibilities
   */
  public static List<RpcPolicyRule> getDenyNonpublicRules() {
    return List.of(
        new RpcPolicyRule(
            "**",
            "**",
            RpcPolicyAction.DENY,
            null,
            null,
            EnumSet.of(
                MemberVisibility.PROTECTED,
                MemberVisibility.PACKAGE_PRIVATE,
                MemberVisibility.PRIVATE)));
  }

  /**
   * Resolves a preset name to its corresponding list of deny rules.
   *
   * @param name the preset name (e.g. {@code "deny-unsafe"}, {@code "deny-jdk-internals"})
   * @return the list of rules for the given preset
   * @throws IllegalArgumentException if the preset name is not recognized
   */
  public static List<RpcPolicyRule> resolvePreset(String name) {
    List<RpcPolicyRule> rules = PRESET_MAP.get(name);
    if (rules == null) {
      throw new IllegalArgumentException("Unknown RPC policy preset: " + name);
    }
    return rules;
  }

  /**
   * Returns all recognized preset names.
   *
   * @return an unmodifiable set of all preset names
   */
  public static Set<String> allPresetNames() {
    return PRESET_MAP.keySet();
  }

  /**
   * Convenience factory for creating a DENY rule with the given class and member patterns.
   *
   * @param classPattern the Ant-style class pattern
   * @param memberPattern the Ant-style member pattern
   * @return a new deny rule matching all channels and all member categories
   */
  private static RpcPolicyRule deny(String classPattern, String memberPattern) {
    return new RpcPolicyRule(classPattern, memberPattern, RpcPolicyAction.DENY, null, null, null);
  }
}
