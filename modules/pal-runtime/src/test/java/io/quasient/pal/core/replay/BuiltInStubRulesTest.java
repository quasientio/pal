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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.messages.types.MessageType;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@link BuiltInStubRules} — validates that the built-in IO and FX shield rule sets
 * contain the expected patterns and actions.
 *
 * <p>Tests cover both {@code getIoShieldRules()} (time, random, I/O) and {@code getFxShieldRules()}
 * (JavaFX animation callbacks and timers), as well as negative matching to ensure rules do not
 * overmatch.
 */
public class BuiltInStubRulesTest {

  /** Verifies that IO shield rules include a rule matching System.currentTimeMillis. */
  @Test
  public void getIoShieldRules_containsSystemTimeMillis() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertTrue(anyRuleMatches(rules, "java.lang.System.currentTimeMillis"));
  }

  /** Verifies that IO shield rules include a rule matching System.nanoTime. */
  @Test
  public void getIoShieldRules_containsSystemNanoTime() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertTrue(anyRuleMatches(rules, "java.lang.System.nanoTime"));
  }

  /**
   * Verifies that IO shield rules include rules matching all java.time classes that have now() or
   * instant()/millis() methods.
   */
  @Test
  public void getIoShieldRules_containsJavaTimeClasses() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertTrue(anyRuleMatches(rules, "java.time.Instant.now"));
    assertTrue(anyRuleMatches(rules, "java.time.LocalTime.now"));
    assertTrue(anyRuleMatches(rules, "java.time.LocalDate.now"));
    assertTrue(anyRuleMatches(rules, "java.time.LocalDateTime.now"));
    assertTrue(anyRuleMatches(rules, "java.time.ZonedDateTime.now"));
    assertTrue(anyRuleMatches(rules, "java.time.OffsetDateTime.now"));
    assertTrue(anyRuleMatches(rules, "java.time.OffsetTime.now"));
    assertTrue(anyRuleMatches(rules, "java.time.Year.now"));
    assertTrue(anyRuleMatches(rules, "java.time.YearMonth.now"));
    assertTrue(anyRuleMatches(rules, "java.time.MonthDay.now"));
    assertTrue(anyRuleMatches(rules, "java.time.Clock.instant"));
    assertTrue(anyRuleMatches(rules, "java.time.Clock.millis"));
  }

  /** Verifies that IO shield rules include rules matching Random classes and Math.random. */
  @Test
  public void getIoShieldRules_containsRandomClasses() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertTrue(anyRuleMatches(rules, "java.lang.Math.random"));
    assertTrue(anyRuleMatches(rules, "java.util.Random.nextInt"));
    assertTrue(anyRuleMatches(rules, "java.util.Random.nextDouble"));
    assertTrue(anyRuleMatches(rules, "java.util.concurrent.ThreadLocalRandom.nextLong"));
  }

  /** Verifies that every IO shield rule has the STUB_FROM_WAL action. */
  @Test
  public void getIoShieldRules_allRulesHaveStubFromWalAction() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertFalse("IO shield rules should not be empty", rules.isEmpty());
    for (ReplayPolicyRule rule : rules) {
      assertThat(
          "Rule " + rule.getFullPattern() + " should have STUB_FROM_WAL action",
          rule.getAction(),
          is(ReplayAction.STUB_FROM_WAL));
    }
  }

  /** Verifies that FX shield rules include a rule matching javafx.animation.*.setOnFinished. */
  @Test
  public void getFxShieldRules_containsSetOnFinished() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getFxShieldRules();

    assertTrue(anyRuleMatches(rules, "javafx.animation.FadeTransition.setOnFinished"));
    assertTrue(anyRuleMatches(rules, "javafx.animation.Timeline.setOnFinished"));
  }

  /**
   * Verifies that FX shield rules include rules matching AnimationTimer.start and
   * AnimationTimer.stop.
   */
  @Test
  public void getFxShieldRules_containsAnimationTimer() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getFxShieldRules();

    assertTrue(anyRuleMatches(rules, "javafx.animation.AnimationTimer.start"));
    assertTrue(anyRuleMatches(rules, "javafx.animation.AnimationTimer.stop"));
  }

  /** Verifies that every FX shield rule has the STUB_FROM_WAL action. */
  @Test
  public void getFxShieldRules_allRulesHaveStubFromWalAction() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getFxShieldRules();

    assertFalse("FX shield rules should not be empty", rules.isEmpty());
    for (ReplayPolicyRule rule : rules) {
      assertThat(
          "Rule " + rule.getFullPattern() + " should have STUB_FROM_WAL action",
          rule.getAction(),
          is(ReplayAction.STUB_FROM_WAL));
    }
  }

  /** Verifies that IO shield rules do not match an arbitrary non-IO method. */
  @Test
  public void getIoShieldRules_doesNotMatchArbitraryMethod() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getIoShieldRules();

    assertFalse(anyRuleMatches(rules, "com.example.Foo.bar"));
  }

  /** Verifies that FX shield rules do not match a non-JavaFX class. */
  @Test
  public void getFxShieldRules_doesNotMatchNonFxClass() {
    List<ReplayPolicyRule> rules = BuiltInStubRules.getFxShieldRules();

    assertFalse(anyRuleMatches(rules, "java.lang.String.valueOf"));
  }

  /**
   * Checks whether any rule in the list matches the given class-method path.
   *
   * @param rules the list of rules to check
   * @param classMethodPath the fully-qualified class-method path
   * @return {@code true} if at least one rule matches
   */
  private static boolean anyRuleMatches(List<ReplayPolicyRule> rules, String classMethodPath) {
    return rules.stream().anyMatch(r -> r.matches(classMethodPath, MessageType.EXEC_CLASS_METHOD));
  }
}
