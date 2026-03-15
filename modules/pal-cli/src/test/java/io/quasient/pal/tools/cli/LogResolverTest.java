/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.Optional;
import org.junit.Test;

/**
 * Unit test specifications for {@code LogResolver}.
 *
 * <p>LogResolver is a shared utility that resolves a log name or path into a {@code LogInfo}
 * object. It consolidates the duplicated {@code resolveLogInfo()} logic from {@code Caller}, {@code
 * Remove}, and {@code MessageStreamPrinter}.
 *
 * <p>Resolution strategy: (1) {@code file:} prefix detection for Chronicle, (2) PAL directory
 * lookup, (3) Kafka fallback.
 */
public class LogResolverTest {

  // ==================== Directory Lookup Tests ====================

  /**
   * Tests that a known log is resolved via the PAL directory.
   *
   * <p>Verifies that when a PalDirectory mock returns a LogInfo for a given log name, {@code
   * resolveLogInfo} returns the correct LogInfo with the expected name, type, and backend details.
   */
  @Test
  public void resolveLogInfo_withDirectoryAndKnownLog_returnsLogInfo() throws Exception {
    // Given: PalDirectory mock returns LogInfo for log name "my-log"
    PalDirectory mockDir = mock(PalDirectory.class);
    LogInfo expectedLog = new LogInfo("my-log", "localhost:29092");
    expectedLog.setLogType(LogType.KAFKA);
    when(mockDir.getLogInfo("my-log")).thenReturn(expectedLog);

    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));

    LogResolver resolver = new LogResolver(dcp, "localhost:29092");

    // When
    LogInfo result = resolver.resolveLogInfo("my-log");

    // Then
    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("my-log"));
    assertThat(result.getBootstrapServers(), is("localhost:29092"));
    assertThat(result.getLogType(), is(LogType.KAFKA));
  }

  /**
   * Tests that a directory-registered log that is unknown falls back to Kafka.
   *
   * <p>Verifies that when the directory returns null for a log name but Kafka servers are
   * configured, the resolver falls back to creating a Kafka-backed LogInfo.
   */
  @Test
  public void resolveLogInfo_withDirectoryButUnknownLog_fallsBackToKafka() throws Exception {
    // Given: PalDirectory returns null for log name "unknown-log", kafkaServers is set
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getLogInfo("unknown-log")).thenReturn(null);

    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));

    LogResolver resolver = new LogResolver(dcp, "localhost:29092");

    // When
    LogInfo result = resolver.resolveLogInfo("unknown-log");

    // Then: returns LogInfo with Kafka backend
    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("unknown-log"));
    assertThat(result.getBootstrapServers(), is("localhost:29092"));
    assertThat(result.getLogType(), is(LogType.KAFKA));
  }

  // ==================== Chronicle (file: prefix) Tests ====================

  /**
   * Tests that a {@code file:} prefixed path bypasses directory lookup and returns Chronicle
   * LogInfo.
   *
   * <p>Verifies that when the log name starts with {@code file:}, the resolver returns a LogInfo
   * with Chronicle backend and the correct path, without querying the directory.
   */
  @Test
  public void resolveLogInfo_withFilePrefixPath_returnsChronicleLogInfo() throws Exception {
    // Given: log name is "file:/tmp/my-wal", directory is available
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    LogResolver resolver = new LogResolver(dcp, "localhost:29092");

    // When
    LogInfo result = resolver.resolveLogInfo("file:/tmp/my-wal");

    // Then: returns LogInfo with Chronicle backend, path=/tmp/my-wal, without querying directory
    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("/tmp/my-wal"));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    verify(dcp, never()).get();
  }

  // ==================== Kafka Fallback Tests ====================

  /**
   * Tests that Kafka fallback is used when no directory connection is available.
   *
   * <p>Verifies that when there is no directory connection but Kafka servers are configured, the
   * resolver returns a Kafka-backed LogInfo.
   */
  @Test
  public void resolveLogInfo_withKafkaFallback_returnsKafkaLogInfo() {
    // Given: no directory connection, kafkaServers="localhost:29092"
    LogResolver resolver = new LogResolver(null, "localhost:29092");

    // When
    LogInfo result = resolver.resolveLogInfo("my-topic");

    // Then: returns LogInfo with Kafka backend
    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("my-topic"));
    assertThat(result.getBootstrapServers(), is("localhost:29092"));
    assertThat(result.getLogType(), is(LogType.KAFKA));
  }

  // ==================== Error / Edge Case Tests ====================

  /**
   * Tests that null is returned when no resolution is possible.
   *
   * <p>Verifies that when there is no directory connection and no Kafka servers configured, the
   * resolver returns null.
   */
  @Test
  public void resolveLogInfo_withNoDirectoryAndNoKafka_returnsNull() {
    // Given: no directory connection, no Kafka servers
    LogResolver resolver = new LogResolver(null, null);

    // When
    LogInfo result = resolver.resolveLogInfo("my-log");

    // Then: returns null
    assertThat(result, is(nullValue()));
  }

  /**
   * Tests that a null log name is rejected.
   *
   * <p>Verifies that passing null as the log name throws an {@code IllegalArgumentException}.
   */
  @Test(expected = IllegalArgumentException.class)
  public void resolveLogInfo_withNullLogName_throwsIllegalArgument() {
    // Given: any state
    LogResolver resolver = new LogResolver(null, null);

    // When: resolveLogInfo(null)
    resolver.resolveLogInfo(null);

    // Then: throws IllegalArgumentException (handled by annotation)
  }
}
