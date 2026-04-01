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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

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

  /** Temporary folder for creating directories used by Chronicle resolution tests. */
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

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

  /**
   * Tests that a relative {@code file:} path is resolved to an absolute path.
   *
   * <p>Verifies that when the log name starts with {@code file:} followed by a relative path, the
   * resolver returns a LogInfo whose name is an absolute, normalized filesystem path.
   */
  @Test
  public void resolveLogInfo_withRelativeFilePath_resolvesToAbsolutePath() {
    LogResolver resolver = new LogResolver(null, null);

    LogInfo result = resolver.resolveLogInfo("file:my-wal");

    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(
        "Relative path should be resolved to absolute",
        Paths.get(result.getName()).isAbsolute(),
        is(true));
    assertThat(
        "Resolved path should end with my-wal", result.getName().endsWith("my-wal"), is(true));
  }

  /**
   * Tests that a relative {@code file:} path with subdirectories is resolved correctly.
   *
   * <p>Verifies that relative subdirectory paths like {@code file:data/app.wal} are resolved to
   * their absolute form.
   */
  @Test
  public void resolveLogInfo_withRelativeSubdirectoryFilePath_resolvesToAbsolutePath() {
    LogResolver resolver = new LogResolver(null, null);

    LogInfo result = resolver.resolveLogInfo("file:data/app.wal");

    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(
        "Relative subdirectory path should be resolved to absolute",
        Paths.get(result.getName()).isAbsolute(),
        is(true));
    assertThat(
        "Resolved path should contain data/app.wal",
        result.getName().endsWith("data/app.wal"),
        is(true));
  }

  /**
   * Tests that an absolute {@code file:} path is preserved unchanged.
   *
   * <p>Verifies that when the log name starts with {@code file:} followed by an absolute path, the
   * resolver does not modify the path.
   */
  @Test
  public void resolveLogInfo_withAbsoluteFilePath_preservesPath() {
    LogResolver resolver = new LogResolver(null, null);

    LogInfo result = resolver.resolveLogInfo("file:/tmp/my-wal");

    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("/tmp/my-wal"));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
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

  // ==================== Chronicle Base Dir Resolution Tests ====================

  /**
   * Tests that a relative path is resolved against the chronicle base dir when the log exists
   * there.
   */
  @Test
  public void resolveLogInfo_withRelativePath_prefersBaseDirWhenLogExists() throws IOException {
    // Given: log directory exists under base dir
    Path baseDir = tempFolder.newFolder("base").toPath();
    Files.createDirectory(baseDir.resolve("my-wal"));

    LogResolver resolver = new LogResolver(null, null, baseDir);

    // When
    LogInfo result = resolver.resolveLogInfo("file:my-wal");

    // Then: resolves to base dir path
    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(result.getName(), is(baseDir.resolve("my-wal").normalize().toString()));
  }

  /**
   * Tests that when the log does not exist under the base dir but exists in CWD, the CWD path is
   * used.
   */
  @Test
  public void resolveChronicleRelativePath_fallsToCwdWhenNotInBaseDir() throws IOException {
    // Given: base dir exists but does not contain the log
    Path baseDir = tempFolder.newFolder("base").toPath();

    Path resolved = LogResolver.resolveChronicleRelativePath(Paths.get("nonexistent"), baseDir);

    // Then: since neither exists, should prefer base dir
    assertThat(resolved, is(baseDir.resolve("nonexistent").normalize()));
  }

  /** Tests that when no base dir is configured, relative paths resolve against CWD. */
  @Test
  public void resolveChronicleRelativePath_usesCwdWhenNoBaseDir() {
    Path resolved = LogResolver.resolveChronicleRelativePath(Paths.get("my-wal"), null);

    assertThat(resolved.isAbsolute(), is(true));
    assertThat(resolved, is(Paths.get("my-wal").toAbsolutePath().normalize()));
  }

  /** Tests that when the log exists under both base dir and CWD, the base dir takes priority. */
  @Test
  public void resolveChronicleRelativePath_prefersBaseDirWhenBothExist() throws IOException {
    // Given: log exists in both base dir and a "cwd" directory
    Path baseDir = tempFolder.newFolder("base").toPath();
    Files.createDirectory(baseDir.resolve("shared-wal"));

    // The static method checks Files.exists for baseDir path first
    Path resolved = LogResolver.resolveChronicleRelativePath(Paths.get("shared-wal"), baseDir);

    // Then: base dir takes priority
    assertThat(resolved, is(baseDir.resolve("shared-wal").normalize()));
  }

  /** Tests that absolute paths bypass chronicle base dir resolution entirely. */
  @Test
  public void resolveLogInfo_withAbsolutePath_ignoresBaseDir() throws IOException {
    Path baseDir = tempFolder.newFolder("base").toPath();
    LogResolver resolver = new LogResolver(null, null, baseDir);

    LogInfo result = resolver.resolveLogInfo("file:/tmp/absolute-wal");

    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("/tmp/absolute-wal"));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
  }

  /**
   * Tests that the three-argument constructor with null base dir behaves the same as the
   * two-argument constructor.
   */
  @Test
  public void resolveLogInfo_withNullBaseDir_resolvesAgainstCwd() {
    LogResolver resolver = new LogResolver(null, null, null);

    LogInfo result = resolver.resolveLogInfo("file:my-wal");

    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(Paths.get(result.getName()).isAbsolute(), is(true));
    assertThat(result.getName().endsWith("my-wal"), is(true));
  }
}
