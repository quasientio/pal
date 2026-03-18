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

import java.util.List;

/**
 * Pre-defined recording scope rules for common I/O and non-deterministic operations.
 *
 * <p>These built-in rules are activated by the {@code --scope-io} CLI flag. They cover operations
 * whose behavior depends on external state (system clock, random number generators, network I/O,
 * file I/O, JDBC connections, process execution, system properties) and should be recorded to the
 * WAL during execution.
 *
 * <p>This class is complementary to {@link io.quasient.pal.core.replay.BuiltInStubRules}: {@code
 * --scope-io} controls which I/O operations are <b>recorded</b> to WAL, while {@code --shield-io}
 * controls which I/O operations are <b>stubbed</b> during replay.
 *
 * @see RecordingScopeRule
 * @see RecordingScopeAction
 */
public final class BuiltInScopeRules {

  /** Private constructor to prevent instantiation of this utility class. */
  private BuiltInScopeRules() {}

  /**
   * Returns the built-in I/O boundary rules for recording scope.
   *
   * <p>These rules capture operations that cross I/O boundaries or produce non-deterministic
   * results:
   *
   * <ul>
   *   <li><b>JDBC:</b> {@code java.sql.DriverManager.getConnection}, {@code
   *       java.sql.Connection.**}, {@code java.sql.Statement.**}, {@code
   *       java.sql.PreparedStatement.**}, {@code java.sql.CallableStatement.**}, {@code
   *       java.sql.ResultSet.**}
   *   <li><b>HTTP Client:</b> {@code java.net.http.HttpClient.**}, {@code
   *       java.net.http.HttpRequest.**}, {@code java.net.http.HttpResponse.**}, {@code
   *       java.net.URL.openConnection}, {@code java.net.HttpURLConnection.**}
   *   <li><b>File I/O:</b> {@code java.io.FileInputStream.**}, {@code java.io.FileOutputStream.**},
   *       {@code java.io.FileReader.**}, {@code java.io.FileWriter.**}, {@code
   *       java.io.RandomAccessFile.**}, {@code java.nio.file.Files.**}, {@code
   *       java.nio.channels.FileChannel.**}
   *   <li><b>Network I/O:</b> {@code java.net.Socket.**}, {@code java.net.ServerSocket.**}, {@code
   *       java.nio.channels.SocketChannel.**}, {@code java.nio.channels.ServerSocketChannel.**}
   *   <li><b>Time (non-deterministic):</b> {@code System.currentTimeMillis}, {@code
   *       System.nanoTime}, {@code java.time.Clock.**}, {@code java.time.Instant.now}, {@code
   *       java.time.LocalDateTime.now}, {@code java.time.LocalDate.now}, {@code
   *       java.time.LocalTime.now}, {@code java.time.ZonedDateTime.now}, {@code
   *       java.time.OffsetDateTime.now}
   *   <li><b>Random (non-deterministic):</b> {@code Math.random}, {@code java.util.Random.**},
   *       {@code java.util.concurrent.ThreadLocalRandom.**}
   *   <li><b>Process/Runtime:</b> {@code java.lang.ProcessBuilder.**}, {@code
   *       java.lang.Runtime.exec}
   *   <li><b>System properties/env:</b> {@code System.getProperty}, {@code System.getenv}
   * </ul>
   *
   * @return an unmodifiable list of built-in I/O boundary rules, all with {@link
   *     RecordingScopeAction#RECORD} action
   */
  public static List<RecordingScopeRule> getIoBoundaryRules() {
    return List.of(
        // JDBC
        rule("java.sql.DriverManager", "getConnection"),
        rule("java.sql.Connection", "**"),
        rule("java.sql.Statement", "**"),
        rule("java.sql.PreparedStatement", "**"),
        rule("java.sql.CallableStatement", "**"),
        rule("java.sql.ResultSet", "**"),
        // HTTP Client
        rule("java.net.http.HttpClient", "**"),
        rule("java.net.http.HttpRequest", "**"),
        rule("java.net.http.HttpResponse", "**"),
        rule("java.net.URL", "openConnection"),
        rule("java.net.HttpURLConnection", "**"),
        // File I/O
        rule("java.io.FileInputStream", "**"),
        rule("java.io.FileOutputStream", "**"),
        rule("java.io.FileReader", "**"),
        rule("java.io.FileWriter", "**"),
        rule("java.io.RandomAccessFile", "**"),
        rule("java.nio.file.Files", "**"),
        rule("java.nio.channels.FileChannel", "**"),
        // Network I/O
        rule("java.net.Socket", "**"),
        rule("java.net.ServerSocket", "**"),
        rule("java.nio.channels.SocketChannel", "**"),
        rule("java.nio.channels.ServerSocketChannel", "**"),
        // Time - legacy
        rule("java.lang.System", "currentTimeMillis"),
        rule("java.lang.System", "nanoTime"),
        // Time - java.time API
        rule("java.time.Clock", "**"),
        rule("java.time.Instant", "now"),
        rule("java.time.LocalDateTime", "now"),
        rule("java.time.LocalDate", "now"),
        rule("java.time.LocalTime", "now"),
        rule("java.time.ZonedDateTime", "now"),
        rule("java.time.OffsetDateTime", "now"),
        // Random
        rule("java.lang.Math", "random"),
        rule("java.util.Random", "**"),
        rule("java.util.concurrent.ThreadLocalRandom", "**"),
        // Process / Runtime
        rule("java.lang.ProcessBuilder", "**"),
        rule("java.lang.Runtime", "exec"),
        // System properties / env
        rule("java.lang.System", "getProperty"),
        rule("java.lang.System", "getenv"));
  }

  /**
   * Convenience factory for creating a RECORD rule with the given patterns and null categories
   * (match all).
   *
   * @param classPattern the Ant-style class pattern
   * @param memberPattern the Ant-style member pattern
   * @return a new rule with {@link RecordingScopeAction#RECORD} action
   */
  private static RecordingScopeRule rule(String classPattern, String memberPattern) {
    return new RecordingScopeRule(classPattern, memberPattern, RecordingScopeAction.RECORD, null);
  }
}
