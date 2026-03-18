/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code BuiltInScopeRules}, which provides curated I/O boundary rules activated by
 * {@code --scope-io}. These rules define the preset recording scope for common I/O operations
 * (JDBC, HTTP, file I/O, networking, time, random, process, and system properties).
 *
 * <p>Follows the {@link io.quasient.pal.core.replay.BuiltInStubRulesTest} pattern: each test
 * verifies that a category of I/O operations is matched by the rules returned from {@code
 * getIoBoundaryRules()}, and a negative test ensures unrelated classes are not matched.
 *
 * @see RecordingScopeRule
 * @see RecordingScopeAction
 */
public class BuiltInScopeRulesTest {

  /**
   * Verifies that the I/O boundary rules match common JDBC operations: {@code
   * java.sql.DriverManager.getConnection}, {@code java.sql.Connection.prepareStatement}, {@code
   * java.sql.PreparedStatement.executeQuery}, and {@code java.sql.ResultSet.next}.
   */
  @Test
  public void ioRulesMatchJdbcOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.sql.DriverManager.getConnection"));
    assertTrue(anyRuleMatches(rules, "java.sql.Connection.prepareStatement"));
    assertTrue(anyRuleMatches(rules, "java.sql.PreparedStatement.executeQuery"));
    assertTrue(anyRuleMatches(rules, "java.sql.ResultSet.next"));
  }

  /**
   * Verifies that the I/O boundary rules match common HTTP operations: {@code
   * java.net.http.HttpClient.send}, {@code java.net.URL.openConnection}, and {@code
   * java.net.HttpURLConnection.getInputStream}.
   */
  @Test
  public void ioRulesMatchHttpOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.net.http.HttpClient.send"));
    assertTrue(anyRuleMatches(rules, "java.net.URL.openConnection"));
    assertTrue(anyRuleMatches(rules, "java.net.HttpURLConnection.getInputStream"));
  }

  /**
   * Verifies that the I/O boundary rules match common file I/O operations: {@code
   * java.io.FileInputStream.read}, {@code java.io.FileOutputStream.write}, {@code
   * java.nio.file.Files.readAllBytes}, and {@code java.nio.channels.FileChannel.open}.
   */
  @Test
  public void ioRulesMatchFileOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.io.FileInputStream.read"));
    assertTrue(anyRuleMatches(rules, "java.io.FileOutputStream.write"));
    assertTrue(anyRuleMatches(rules, "java.nio.file.Files.readAllBytes"));
    assertTrue(anyRuleMatches(rules, "java.nio.channels.FileChannel.open"));
  }

  /**
   * Verifies that the I/O boundary rules match common network operations: {@code
   * java.net.Socket.connect}, {@code java.net.ServerSocket.accept}, and {@code
   * java.nio.channels.SocketChannel.open}.
   */
  @Test
  public void ioRulesMatchNetworkOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.net.Socket.connect"));
    assertTrue(anyRuleMatches(rules, "java.net.ServerSocket.accept"));
    assertTrue(anyRuleMatches(rules, "java.nio.channels.SocketChannel.open"));
  }

  /**
   * Verifies that the I/O boundary rules match non-deterministic time operations: {@code
   * java.lang.System.currentTimeMillis}, {@code java.lang.System.nanoTime}, {@code
   * java.time.Clock.instant}, {@code java.time.Instant.now}, and {@code
   * java.time.LocalDateTime.now}.
   */
  @Test
  public void ioRulesMatchTimeOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.lang.System.currentTimeMillis"));
    assertTrue(anyRuleMatches(rules, "java.lang.System.nanoTime"));
    assertTrue(anyRuleMatches(rules, "java.time.Clock.instant"));
    assertTrue(anyRuleMatches(rules, "java.time.Instant.now"));
    assertTrue(anyRuleMatches(rules, "java.time.LocalDateTime.now"));
  }

  /**
   * Verifies that the I/O boundary rules match non-deterministic random operations: {@code
   * java.lang.Math.random}, {@code java.util.Random.nextInt}, and {@code
   * java.util.concurrent.ThreadLocalRandom.current}.
   */
  @Test
  public void ioRulesMatchRandomOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.lang.Math.random"));
    assertTrue(anyRuleMatches(rules, "java.util.Random.nextInt"));
    assertTrue(anyRuleMatches(rules, "java.util.concurrent.ThreadLocalRandom.current"));
  }

  /**
   * Verifies that the I/O boundary rules match process execution operations: {@code
   * java.lang.ProcessBuilder.start} and {@code java.lang.Runtime.exec}.
   */
  @Test
  public void ioRulesMatchProcessOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.lang.ProcessBuilder.start"));
    assertTrue(anyRuleMatches(rules, "java.lang.Runtime.exec"));
  }

  /**
   * Verifies that the I/O boundary rules match system property and environment variable operations:
   * {@code java.lang.System.getProperty} and {@code java.lang.System.getenv}.
   */
  @Test
  public void ioRulesMatchSystemPropertyOperations() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertTrue(anyRuleMatches(rules, "java.lang.System.getProperty"));
    assertTrue(anyRuleMatches(rules, "java.lang.System.getenv"));
  }

  /**
   * Verifies that every rule returned by {@code getIoBoundaryRules()} has the {@link
   * RecordingScopeAction#RECORD} action. I/O boundary preset rules are always RECORD rules — they
   * are inclusion rules that capture I/O operations in the WAL.
   */
  @Test
  public void allIoRulesHaveRecordAction() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertFalse("I/O boundary rules should not be empty", rules.isEmpty());
    for (RecordingScopeRule rule : rules) {
      assertThat(
          "Rule "
              + rule.getClassPattern()
              + "."
              + rule.getMemberPattern()
              + " should have RECORD action",
          rule.getAction(),
          is(RecordingScopeAction.RECORD));
    }
  }

  /**
   * Verifies that the I/O boundary rules do not match unrelated classes and methods. Operations
   * like {@code java.util.HashMap.put}, {@code java.lang.String.split}, and {@code
   * com.example.Foo.bar} should not be matched by any I/O boundary rule.
   */
  @Test
  public void ioRulesDoNotMatchUnrelatedClasses() {
    List<RecordingScopeRule> rules = BuiltInScopeRules.getIoBoundaryRules();

    assertFalse(anyRuleMatches(rules, "java.util.HashMap.put"));
    assertFalse(anyRuleMatches(rules, "java.lang.String.split"));
    assertFalse(anyRuleMatches(rules, "com.example.Foo.bar"));
  }

  /**
   * Checks whether any rule in the list matches the given fully-qualified class-member path.
   *
   * <p>The path is split into class name and member name at the last dot, and each rule is tested
   * with a {@code null} category (meaning all categories match). This mirrors how {@link
   * io.quasient.pal.core.replay.BuiltInStubRulesTest} tests its rules.
   *
   * @param rules the list of recording scope rules to check
   * @param classMethodPath the fully-qualified path (e.g. {@code
   *     "java.sql.DriverManager.getConnection"})
   * @return {@code true} if at least one rule matches
   */
  private static boolean anyRuleMatches(List<RecordingScopeRule> rules, String classMethodPath) {
    int lastDot = classMethodPath.lastIndexOf('.');
    String className = classMethodPath.substring(0, lastDot);
    String memberName = classMethodPath.substring(lastDot + 1);
    return rules.stream().anyMatch(r -> r.matches(className, memberName, null));
  }
}
