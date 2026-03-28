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
   *   <li><b>Time:</b> {@code System.currentTimeMillis}, {@code System.nanoTime}, {@code
   *       java.time.Clock.instant/millis}, {@code java.time.*.now} (Instant, LocalTime, LocalDate,
   *       LocalDateTime, ZonedDateTime, OffsetDateTime, OffsetTime, Year, YearMonth, MonthDay)
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
        // Time - legacy
        rule("java.lang.System", "currentTimeMillis", ReplayAction.STUB_FROM_WAL),
        rule("java.lang.System", "nanoTime", ReplayAction.STUB_FROM_WAL),
        // Time - java.time API
        rule("java.time.Clock", "instant", ReplayAction.STUB_FROM_WAL),
        rule("java.time.Clock", "millis", ReplayAction.STUB_FROM_WAL),
        rule("java.time.Instant", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.LocalTime", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.LocalDate", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.LocalDateTime", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.ZonedDateTime", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.OffsetDateTime", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.OffsetTime", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.Year", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.YearMonth", "now", ReplayAction.STUB_FROM_WAL),
        rule("java.time.MonthDay", "now", ReplayAction.STUB_FROM_WAL),
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
   * Returns the built-in JavaFX shield rules for deterministic replay.
   *
   * <p>These rules stub JavaFX operations that depend on wall-clock timing:
   *
   * <ul>
   *   <li><b>Animation callbacks:</b> {@code Animation.setOnFinished} (prevents callbacks from
   *       firing after WAL cursor is exhausted, while still allowing animations to run for visual
   *       effects)
   *   <li><b>Animation Timer:</b> {@code AnimationTimer.start/stop} (prevents per-frame handle()
   *       calls that would cause massive divergences)
   * </ul>
   *
   * @return an unmodifiable list of built-in JavaFX shield rules
   */
  public static List<ReplayPolicyRule> getFxShieldRules() {
    return List.of(
        // Animation callback registration - stub to prevent callbacks from firing
        // after the WAL cursor is exhausted. This allows animations to run (for
        // visual effects) while preventing the callback operations from being
        // logged as "extra operations".
        rule("javafx.animation.*", "setOnFinished", ReplayAction.STUB_FROM_WAL),
        // AnimationTimer (uses wall-clock pulses) - stub start/stop entirely
        // since AnimationTimer.handle() fires every frame and would cause
        // massive divergences
        rule("javafx.animation.AnimationTimer", "start", ReplayAction.STUB_FROM_WAL),
        rule("javafx.animation.AnimationTimer", "stop", ReplayAction.STUB_FROM_WAL));
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
