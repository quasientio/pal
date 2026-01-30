/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.kafka;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Injector;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.transport.SourceLogReader;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

@SuppressWarnings("DoNotMock")
public class LogConfiguratorTest {

  private PalDirectory mockedPalDirectory;
  private SourceLogReader mockedLogReader;
  private KafkaWalWriter mockedKafkaWalWriter;
  private Injector mockedInjector;
  private Properties appProps;
  private static final String LOG_PREFIX = "test_app";
  private static final String KAFKA_SERVERS = "kafka1:9092,kafka2:9094";

  @Before
  public void setUp() throws Exception {
    appProps = new Properties();
    appProps.setProperty("kafka.bootstrap.servers", KAFKA_SERVERS);
    appProps.setProperty("logPrefix", LOG_PREFIX);

    mockedPalDirectory = mock(PalDirectory.class);
    mockedLogReader = mock(SourceLogReader.class);
    mockedKafkaWalWriter = mock(KafkaWalWriter.class);
    mockedInjector = mock(Injector.class);
    var mockedDirectoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(mockedDirectoryConnectionProvider.get()).thenReturn(Optional.of(mockedPalDirectory));
    when(mockedInjector.getInstance(DirectoryConnectionProvider.class))
        .thenReturn(mockedDirectoryConnectionProvider);
    when(mockedInjector.getInstance(SourceLogReader.class)).thenReturn(mockedLogReader);
    when(mockedInjector.getInstance(WalWriter.class)).thenReturn(mockedKafkaWalWriter);
  }

  @After
  public void cleanUp() {
    Mockito.reset(mockedPalDirectory, mockedLogReader, mockedKafkaWalWriter, mockedInjector);
  }

  @Test
  public void init_missingKafkaServersWithChronicleQueue_ok() throws Exception {
    String sourceLogName = "file:/tmp/chronicle-queue";
    Properties emptyProps = new Properties();

    // With Chronicle queues (file:/ prefix), Kafka servers are not required
    LogConfigurator configurator =
        new LogConfigurator(sourceLogName, null, null, emptyProps, mockedInjector, false);
    configurator.init();

    // verify that log reader was called with Chronicle queue
    verify(mockedLogReader)
        .readFromLog(
            argThat(
                logInfo ->
                    logInfo.getName().equals("/tmp/chronicle-queue")
                        && logInfo.getLogType() == LogInfo.LogType.CHRONICLE),
            eq(false),
            eq(null),
            eq(false)); // sourceLogWillBeCreated = false (no WAL specified)
  }

  @Test
  public void init_sourceLogExists_writeAheadLogIsNull_ok() throws Exception {

    String sourceLogName = "app_log_in";

    // call init()
    new LogConfigurator(sourceLogName, null, null, appProps, mockedInjector, false).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo(sourceLogName))),
            eq(false),
            eq(null),
            eq(false)); // sourceLogWillBeCreated = false (no WAL specified)
    verify(mockedKafkaWalWriter, never()).writeToLog(any(), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "app_log_in";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector, false)
        .init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo(sourceLogName))),
            eq(false),
            eq(null),
            eq(false)); // sourceLogWillBeCreated = false (no WAL specified)
    verify(mockedKafkaWalWriter, never()).writeToLog(eq(new LogInfo("app_random1")), anyBoolean());
  }

  @Test
  public void init_sourceLogIsAuto_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "auto";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector, false)
        .init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo("auto"))),
            eq(true),
            eq(null),
            eq(true)); // sourceLogWillBeCreated = true (same log for source and WAL)
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_sourceLogIsNew_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "new_app_log";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector, false)
        .init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createLog(any(LogInfo.class));
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo(sourceLogName))),
            eq(false),
            eq(null),
            eq(false)); // sourceLogWillBeCreated = false (no WAL specified)
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_writeAheadLogExists_ok() throws Exception {

    String sourceLogName = "app_log_in";
    String writeAheadLogName = "app_log_out";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector, false)
        .init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).logExists(writeAheadLogName);
    verify(mockedPalDirectory, never()).getLogInfo(writeAheadLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo(sourceLogName))),
            eq(false),
            eq(null),
            eq(false)); // sourceLogWillBeCreated = false (no WAL specified)
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(writeAheadLogName))), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_sameWriteAheadLogExists_ok() throws Exception {

    String logName = "app_log_10";

    // call init()
    new LogConfigurator(logName, null, logName, appProps, mockedInjector, false).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(logName);
    verify(mockedPalDirectory, never()).getLogInfo(logName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(
            argThat(new LogInfoMatcher(new LogInfo(logName))),
            eq(true),
            eq(null),
            eq(true)); // sourceLogWillBeCreated = true (same log for source and WAL)
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(logName))), anyBoolean());
  }

  @Test
  public void init_sourceLogIsNull_writeAheadLogExists_ok() throws Exception {

    String writeAheadLogName = "app_log_out";

    // call init()
    new LogConfigurator(null, null, writeAheadLogName, appProps, mockedInjector, false).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(writeAheadLogName);
    verify(mockedPalDirectory, never()).getLogInfo(writeAheadLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader, never()).readFromLog(any(), anyBoolean(), anyLong(), anyBoolean());
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(writeAheadLogName))), anyBoolean());
  }

  // ========== isChronicleLog() Tests ==========

  /** Tests isChronicleLog returns true for file: prefix. */
  @Test
  public void isChronicleLog_filePrefix_returnsTrue() throws Exception {
    Method method = LogConfigurator.class.getDeclaredMethod("isChronicleLog", String.class);
    method.setAccessible(true);

    assertTrue((Boolean) method.invoke(null, "file:/tmp/queue"));
    assertTrue((Boolean) method.invoke(null, "file:relative/path"));
    assertTrue((Boolean) method.invoke(null, "file:"));
  }

  /** Tests isChronicleLog returns false for Kafka topics. */
  @Test
  public void isChronicleLog_kafkaTopic_returnsFalse() throws Exception {
    Method method = LogConfigurator.class.getDeclaredMethod("isChronicleLog", String.class);
    method.setAccessible(true);

    assertFalse((Boolean) method.invoke(null, "my-kafka-topic"));
    assertFalse((Boolean) method.invoke(null, "app_log_in"));
    assertFalse((Boolean) method.invoke(null, ""));
  }

  /** Tests isChronicleLog handles null. */
  @Test
  public void isChronicleLog_null_returnsFalse() throws Exception {
    Method method = LogConfigurator.class.getDeclaredMethod("isChronicleLog", String.class);
    method.setAccessible(true);

    assertFalse((Boolean) method.invoke(null, (Object) null));
  }

  // ========== extractLogName() Tests ==========

  /** Tests extractLogName removes file: prefix for Chronicle queues. */
  @Test
  public void extractLogName_chronicleQueue_removesPrefix() throws Exception {
    Method method = LogConfigurator.class.getDeclaredMethod("extractLogName", String.class);
    method.setAccessible(true);

    assertEquals("/tmp/chronicle-queue", method.invoke(null, "file:/tmp/chronicle-queue"));
    assertEquals("relative/path", method.invoke(null, "file:relative/path"));
    assertEquals("", method.invoke(null, "file:"));
  }

  /** Tests extractLogName returns Kafka topic names as-is. */
  @Test
  public void extractLogName_kafkaTopic_returnsAsIs() throws Exception {
    Method method = LogConfigurator.class.getDeclaredMethod("extractLogName", String.class);
    method.setAccessible(true);

    assertEquals("my-kafka-topic", method.invoke(null, "my-kafka-topic"));
    assertEquals("app_log_in", method.invoke(null, "app_log_in"));
  }

  // ========== normalizeChronicleQueuePath() Tests ==========

  /** Tests normalizeChronicleQueuePath returns absolute paths unchanged. */
  @Test
  public void normalizeChronicleQueuePath_absolutePath_returnsUnchanged() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, null, appProps, mockedInjector, false);
    Method method =
        LogConfigurator.class.getDeclaredMethod("normalizeChronicleQueuePath", String.class);
    method.setAccessible(true);

    assertEquals("/tmp/chronicle-queue", method.invoke(configurator, "/tmp/chronicle-queue"));
    assertEquals("/var/log/pal", method.invoke(configurator, "/var/log/pal"));
  }

  /** Tests normalizeChronicleQueuePath converts relative paths to absolute. */
  @Test
  public void normalizeChronicleQueuePath_relativePath_convertsToAbsolute() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, null, appProps, mockedInjector, false);
    Method method =
        LogConfigurator.class.getDeclaredMethod("normalizeChronicleQueuePath", String.class);
    method.setAccessible(true);

    String result = (String) method.invoke(configurator, "relative/path");
    assertTrue("Path should be absolute", result.startsWith("/"));
    assertTrue("Path should end with relative/path", result.endsWith("relative/path"));
  }

  // ========== createChronicleLogInfo() Tests ==========

  /** Tests createChronicleLogInfo creates proper LogInfo with Chronicle type. */
  @Test
  public void createChronicleLogInfo_createsProperLogInfo() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, null, appProps, mockedInjector, false);
    Method method = LogConfigurator.class.getDeclaredMethod("createChronicleLogInfo", String.class);
    method.setAccessible(true);

    LogInfo logInfo = (LogInfo) method.invoke(configurator, "/tmp/chronicle-queue");

    assertEquals("/tmp/chronicle-queue", logInfo.getName());
    assertEquals(LogInfo.LogType.CHRONICLE, logInfo.getLogType());
    assertNotNull(logInfo.getUuid());
  }

  /** Tests createChronicleLogInfo normalizes relative paths. */
  @Test
  public void createChronicleLogInfo_normalizesRelativePath() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, null, appProps, mockedInjector, false);
    Method method = LogConfigurator.class.getDeclaredMethod("createChronicleLogInfo", String.class);
    method.setAccessible(true);

    LogInfo logInfo = (LogInfo) method.invoke(configurator, "relative/queue");

    assertTrue("Path should be absolute", logInfo.getName().startsWith("/"));
    assertEquals(LogInfo.LogType.CHRONICLE, logInfo.getLogType());
  }

  // ========== getKafkaAdminProperties() Tests ==========

  /** Tests getKafkaAdminProperties creates proper properties. */
  @Test
  public void getKafkaAdminProperties_createsProperProperties() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, null, appProps, mockedInjector, false);
    Method method = LogConfigurator.class.getDeclaredMethod("getKafkaAdminProperties", int.class);
    method.setAccessible(true);

    Properties adminProps = (Properties) method.invoke(configurator, 3000);

    assertEquals(KAFKA_SERVERS, adminProps.get("bootstrap.servers"));
    assertEquals("3000", adminProps.get("request.timeout.ms"));
    assertEquals("3000", adminProps.get("connections.max.idle.ms"));
    assertEquals("3000", adminProps.get("default.api.timeout.ms"));
    assertEquals("3000", adminProps.get("socket.connection.setup.timeout.ms"));
    assertEquals("3000", adminProps.get("socket.connection.setup.timeout.max.ms"));
    assertEquals("250", adminProps.get("retry.backoff.ms"));
  }

  // ========== getSourceLog() / getWriteAheadLog() Tests ==========

  /** Tests getSourceLog returns empty before init. */
  @Test
  public void getSourceLog_beforeInit_returnsEmpty() {
    LogConfigurator configurator =
        new LogConfigurator("my-log", null, null, appProps, mockedInjector, false);

    assertFalse(configurator.getSourceLog().isPresent());
  }

  /** Tests getWriteAheadLog returns empty before init. */
  @Test
  public void getWriteAheadLog_beforeInit_returnsEmpty() {
    LogConfigurator configurator =
        new LogConfigurator(null, null, "my-wal", appProps, mockedInjector, false);

    assertFalse(configurator.getWriteAheadLog().isPresent());
  }

  /** Tests getSourceLog returns value after init. */
  @Test
  public void getSourceLog_afterInit_returnsValue() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator("file:/tmp/queue", null, null, appProps, mockedInjector, false);

    configurator.init();

    assertTrue(configurator.getSourceLog().isPresent());
    assertEquals(LogInfo.LogType.CHRONICLE, configurator.getSourceLog().get().getLogType());
  }

  /** Tests getWriteAheadLog returns value after init. */
  @Test
  public void getWriteAheadLog_afterInit_returnsValue() throws Exception {
    LogConfigurator configurator =
        new LogConfigurator(null, null, "file:/tmp/wal", appProps, mockedInjector, false);

    configurator.init();

    assertTrue(configurator.getWriteAheadLog().isPresent());
    assertEquals(LogInfo.LogType.CHRONICLE, configurator.getWriteAheadLog().get().getLogType());
  }

  // ========== init() with PalDirectory Integration ==========

  /** Tests init with PalDirectory and existing Kafka log. */
  @Test
  public void init_withPaldir_kafkaLogExists_retrievesFromDirectory() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    String sourceLogName = "existing-kafka-topic";
    LogInfo existingLog = new LogInfo(sourceLogName, KAFKA_SERVERS);
    existingLog.setUuid(UUID.randomUUID());

    when(mockedPalDirectory.logExists(sourceLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(sourceLogName)).thenReturn(existingLog);

    new LogConfigurator(sourceLogName, null, null, appProps, mockedInjector, false).init();

    verify(mockedPalDirectory).logExists(sourceLogName);
    verify(mockedPalDirectory).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createLog(any());
  }

  /** Tests init with PalDirectory and new Kafka log. */
  @Test
  public void init_withPaldir_kafkaLogNotExists_createsInDirectory() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    String sourceLogName = "new-kafka-topic";

    when(mockedPalDirectory.logExists(sourceLogName)).thenReturn(false);

    new LogConfigurator(sourceLogName, null, null, appProps, mockedInjector, false).init();

    verify(mockedPalDirectory).logExists(sourceLogName);
    verify(mockedPalDirectory).createLog(argThat(log -> log.getName().equals(sourceLogName)));
  }

  /** Tests init with PalDirectory and auto source log. */
  @Test
  public void init_withPaldir_autoSourceLog_registersNewLog() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    LogInfo autoLog = new LogInfo("auto-generated-log", KAFKA_SERVERS);
    autoLog.setUuid(UUID.randomUUID());

    when(mockedPalDirectory.createAutoLog(LOG_PREFIX, KAFKA_SERVERS)).thenReturn(autoLog);

    LogConfigurator configurator =
        new LogConfigurator("auto", null, null, appProps, mockedInjector, false);
    configurator.init();

    verify(mockedPalDirectory).createAutoLog(LOG_PREFIX, KAFKA_SERVERS);
    assertTrue(configurator.getSourceLog().isPresent());
  }

  /** Tests init with PalDirectory and auto source+WAL logs reuses same log. */
  @Test
  public void init_withPaldir_autoSourceAndWal_reusesSameLog() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    LogInfo autoLog = new LogInfo("auto-generated-log", KAFKA_SERVERS);
    autoLog.setUuid(UUID.randomUUID());

    when(mockedPalDirectory.createAutoLog(LOG_PREFIX, KAFKA_SERVERS)).thenReturn(autoLog);

    LogConfigurator configurator =
        new LogConfigurator("auto", null, "auto", appProps, mockedInjector, false);
    configurator.init();

    // Should only call createAutoLog once, not twice
    verify(mockedPalDirectory).createAutoLog(LOG_PREFIX, KAFKA_SERVERS);
    assertTrue(configurator.getSourceLog().isPresent());
    assertTrue(configurator.getWriteAheadLog().isPresent());
    assertEquals(
        configurator.getSourceLog().get().getUuid(),
        configurator.getWriteAheadLog().get().getUuid());
  }

  /** Tests init with PalDirectory and Chronicle log that doesn't exist. */
  @Test
  public void init_withPaldir_chronicleLogNotExists_createsInDirectory() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    String chronicleSpec = "file:/tmp/new-chronicle";

    when(mockedPalDirectory.getLogsInfoByName("/tmp/new-chronicle"))
        .thenReturn(Collections.emptyList());

    new LogConfigurator(chronicleSpec, null, null, appProps, mockedInjector, false).init();

    verify(mockedPalDirectory).getLogsInfoByName("/tmp/new-chronicle");
    verify(mockedPalDirectory)
        .createLog(
            argThat(
                log ->
                    log.getName().equals("/tmp/new-chronicle")
                        && log.getLogType() == LogInfo.LogType.CHRONICLE));
  }

  /** Tests init with PalDirectory and existing Chronicle log. */
  @Test
  public void init_withPaldir_chronicleLogExists_retrievesFromDirectory() throws Exception {
    appProps.setProperty("paldir_url", "localhost:2379");
    String chronicleSpec = "file:/tmp/existing-chronicle";
    LogInfo existingLog = new LogInfo("/tmp/existing-chronicle");
    existingLog.setLogType(LogInfo.LogType.CHRONICLE);
    existingLog.setUuid(UUID.randomUUID());

    when(mockedPalDirectory.getLogsInfoByName("/tmp/existing-chronicle"))
        .thenReturn(List.of(existingLog));

    new LogConfigurator(chronicleSpec, null, null, appProps, mockedInjector, false).init();

    verify(mockedPalDirectory).getLogsInfoByName("/tmp/existing-chronicle");
    verify(mockedPalDirectory, never()).createLog(any());
  }

  // ========== init() with sourceLogOffset ==========

  /** Tests init passes source log offset to reader. */
  @Test
  public void init_withSourceLogOffset_passesToReader() throws Exception {
    String sourceLogName = "file:/tmp/queue";
    Long offset = 42L;

    new LogConfigurator(sourceLogName, offset, null, appProps, mockedInjector, false).init();

    verify(mockedLogReader)
        .readFromLog(
            argThat(log -> log.getName().equals("/tmp/queue")), eq(false), eq(42L), eq(false));
  }

  // ========== init() both source and WAL are Chronicle ==========

  /** Tests init with both source and WAL as Chronicle queues. */
  @Test
  public void init_bothChronicle_configuresBoth() throws Exception {
    String sourceLog = "file:/tmp/source-queue";
    String walLog = "file:/tmp/wal-queue";

    LogConfigurator configurator =
        new LogConfigurator(sourceLog, null, walLog, appProps, mockedInjector, false);
    configurator.init();

    verify(mockedLogReader)
        .readFromLog(
            argThat(log -> log.getName().equals("/tmp/source-queue")),
            eq(false),
            eq(null),
            eq(false));
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(log -> log.getName().equals("/tmp/wal-queue")), anyBoolean());

    assertTrue(configurator.getSourceLog().isPresent());
    assertTrue(configurator.getWriteAheadLog().isPresent());
    assertEquals(LogInfo.LogType.CHRONICLE, configurator.getSourceLog().get().getLogType());
    assertEquals(LogInfo.LogType.CHRONICLE, configurator.getWriteAheadLog().get().getLogType());
  }

  /** Tests init with same Chronicle queue for source and WAL. */
  @Test
  public void init_sameChronicleQueue_configuresAsShared() throws Exception {
    String logSpec = "file:/tmp/shared-queue";

    LogConfigurator configurator =
        new LogConfigurator(logSpec, null, logSpec, appProps, mockedInjector, false);
    configurator.init();

    // When source and WAL are the same, the flags should indicate shared log
    verify(mockedLogReader)
        .readFromLog(
            argThat(log -> log.getName().equals("/tmp/shared-queue")),
            eq(true), // sourceAndWalAreSameLog
            eq(null),
            eq(true)); // sourceLogWillBeCreated
  }

  // ArgumentMatcher for LogInfo which ignores the UUID and bootstrapServers on equals()
  static class LogInfoMatcher implements ArgumentMatcher<LogInfo> {
    private final LogInfo expected;

    public LogInfoMatcher(LogInfo expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(LogInfo actual) {
      if (actual == null) {
        return false;
      }
      return expected.getName().equals(actual.getName());
    }

    @Override
    public String toString() {
      return "LogInfo with name: " + expected.getName();
    }
  }
}
