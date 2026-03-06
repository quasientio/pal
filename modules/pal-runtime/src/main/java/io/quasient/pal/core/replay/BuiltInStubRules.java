/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import java.util.List;

/**
 * Pre-defined replay policy rules for common I/O and non-deterministic operations.
 *
 * <p>These built-in rules are activated by the {@code --shield-io} CLI flag. They cover operations
 * whose return values depend on external state (system clock, random number generators, network
 * I/O, file I/O, JDBC connections) and should be stubbed from the WAL during deterministic replay.
 */
public final class BuiltInStubRules {

  /** Private constructor to prevent instantiation of this utility class. */
  private BuiltInStubRules() {}

  /**
   * Returns the built-in I/O shield rules for deterministic replay.
   *
   * <p>These rules stub operations that depend on external state:
   *
   * <ul>
   *   <li><b>Time:</b> {@code System.currentTimeMillis}, {@code System.nanoTime}
   *   <li><b>Random:</b> {@code Math.random}, {@code java.util.Random.**}, {@code
   *       ThreadLocalRandom.**}
   *   <li><b>I/O reads:</b> {@code InputStream.**}, {@code Reader.**}, {@code java.net.**}
   *   <li><b>Console output:</b> {@code PrintStream.**}, {@code PrintWriter.**}
   *   <li><b>JDBC:</b> {@code DriverManager.getConnection}
   * </ul>
   *
   * @return an unmodifiable list of built-in I/O shield rules
   */
  public static List<ReplayPolicyRule> getIoShieldRules() {
    return List.of(
        // Time
        rule("java.lang.System", "currentTimeMillis", ReplayAction.STUB_FROM_WAL),
        rule("java.lang.System", "nanoTime", ReplayAction.STUB_FROM_WAL),
        // Random
        rule("java.lang.Math", "random", ReplayAction.STUB_FROM_WAL),
        rule("java.util.Random", "**", ReplayAction.STUB_FROM_WAL),
        rule("java.util.concurrent.ThreadLocalRandom", "**", ReplayAction.STUB_FROM_WAL),
        // I/O reads
        rule("java.io.InputStream", "**", ReplayAction.STUB_FROM_WAL),
        rule("java.io.Reader", "**", ReplayAction.STUB_FROM_WAL),
        rule("java.net.**", "**", ReplayAction.STUB_FROM_WAL),
        // Console output
        rule("java.io.PrintStream", "**", ReplayAction.STUB_FROM_WAL),
        rule("java.io.PrintWriter", "**", ReplayAction.STUB_FROM_WAL),
        // JDBC
        rule("java.sql.DriverManager", "getConnection", ReplayAction.STUB_FROM_WAL));
  }

  /**
   * Convenience factory for creating a rule with the given patterns and action.
   *
   * @param classPattern the Ant-style class pattern
   * @param methodPattern the Ant-style method pattern
   * @param action the replay action
   * @return a new rule
   */
  private static ReplayPolicyRule rule(
      String classPattern, String methodPattern, ReplayAction action) {
    return new ReplayPolicyRule(classPattern, methodPattern, action);
  }
}
