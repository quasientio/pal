/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.kafka;

import static org.junit.Assert.fail;
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
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.transport.WalWriter;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import java.util.Optional;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

@SuppressWarnings("DoNotMock")
public class LogConfiguratorTest {

  private PalDirectory mockedPalDirectory;
  private LogReader mockedLogReader;
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
    mockedLogReader = mock(LogReader.class);
    mockedKafkaWalWriter = mock(KafkaWalWriter.class);
    mockedInjector = mock(Injector.class);
    var mockedDirectoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(mockedDirectoryConnectionProvider.get()).thenReturn(Optional.of(mockedPalDirectory));
    when(mockedInjector.getInstance(DirectoryConnectionProvider.class))
        .thenReturn(mockedDirectoryConnectionProvider);
    when(mockedInjector.getInstance(LogReader.class)).thenReturn(mockedLogReader);
    when(mockedInjector.getInstance(WalWriter.class)).thenReturn(mockedKafkaWalWriter);
  }

  @After
  public void cleanUp() {
    Mockito.reset(mockedPalDirectory, mockedLogReader, mockedKafkaWalWriter, mockedInjector);
  }

  @Test
  public void init_missingKafkaServersInProperties_illegalArgumentException() {
    String sourceLogName = "app_log_in";
    Properties emptyProps = new Properties();

    // call init()
    try {
      new LogConfigurator(sourceLogName, null, null, emptyProps, mockedInjector);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // ok
    }
  }

  @Test
  public void init_sourceLogExists_writeAheadLogIsNull_ok() throws Exception {

    String sourceLogName = "app_log_in";

    // call init()
    new LogConfigurator(sourceLogName, null, null, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(sourceLogName))), eq(false), eq(null));
    verify(mockedKafkaWalWriter, never()).writeToLog(any(), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "app_log_in";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(sourceLogName))), eq(false), eq(null));
    verify(mockedKafkaWalWriter, never()).writeToLog(eq(new LogInfo("app_random1")), anyBoolean());
  }

  @Test
  public void init_sourceLogIsAuto_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "auto";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), eq(true), eq(null));
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_sourceLogIsNew_writeAheadLogIsAuto_ok() throws Exception {

    String sourceLogName = "new_app_log";
    String writeAheadLogName = "auto";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).createLog(any(LogInfo.class));
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(sourceLogName))), eq(false), eq(null));
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_writeAheadLogExists_ok() throws Exception {

    String sourceLogName = "app_log_in";
    String writeAheadLogName = "app_log_out";

    // call init()
    new LogConfigurator(sourceLogName, null, writeAheadLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(sourceLogName);
    verify(mockedPalDirectory, never()).getLogInfo(sourceLogName);
    verify(mockedPalDirectory, never()).logExists(writeAheadLogName);
    verify(mockedPalDirectory, never()).getLogInfo(writeAheadLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(sourceLogName))), eq(false), eq(null));
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(writeAheadLogName))), anyBoolean());
  }

  @Test
  public void init_sourceLogExists_sameWriteAheadLogExists_ok() throws Exception {

    String logName = "app_log_10";

    // call init()
    new LogConfigurator(logName, null, logName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(logName);
    verify(mockedPalDirectory, never()).getLogInfo(logName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(logName))), eq(true), eq(null));
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(logName))), anyBoolean());
  }

  @Test
  public void init_sourceLogIsNull_writeAheadLogExists_ok() throws Exception {

    String writeAheadLogName = "app_log_out";

    // call init()
    new LogConfigurator(null, null, writeAheadLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(writeAheadLogName);
    verify(mockedPalDirectory, never()).getLogInfo(writeAheadLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader, never()).readFromLog(any(), anyBoolean(), anyLong());
    verify(mockedKafkaWalWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(writeAheadLogName))), anyBoolean());
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
