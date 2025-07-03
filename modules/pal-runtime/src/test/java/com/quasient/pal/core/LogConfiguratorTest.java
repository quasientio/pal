/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

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
  private LogWriter mockedLogWriter;
  private Injector mockedInjector;
  private Properties appProps;
  private static final String KAFKA_TOPIC_PREFIX = "test_app";
  private static final String KAFKA_SERVERS = "kafka1:9092,kafka2:9094";

  @Before
  public void setUp() throws Exception {
    appProps = new Properties();
    appProps.setProperty("kafka.bootstrap.servers", KAFKA_SERVERS);
    appProps.setProperty("kafkaTopicPrefix", KAFKA_TOPIC_PREFIX);

    mockedPalDirectory = mock(PalDirectory.class);
    mockedLogReader = mock(LogReader.class);
    mockedLogWriter = mock(LogWriter.class);
    mockedInjector = mock(Injector.class);
    var mockedDirectoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(mockedDirectoryConnectionProvider.get()).thenReturn(Optional.of(mockedPalDirectory));
    when(mockedInjector.getInstance(DirectoryConnectionProvider.class))
        .thenReturn(mockedDirectoryConnectionProvider);
    when(mockedInjector.getInstance(LogReader.class)).thenReturn(mockedLogReader);
    when(mockedInjector.getInstance(LogWriter.class)).thenReturn(mockedLogWriter);
  }

  @After
  public void cleanUp() {
    Mockito.reset(mockedPalDirectory, mockedLogReader, mockedLogWriter, mockedInjector);
  }

  @Test
  public void init_missingKafkaServersInProperties_illegalArgumentException() {
    String inLogName = "app_log_in";
    Properties emptyProps = new Properties();

    // call init()
    try {
      new LogConfigurator(inLogName, null, null, emptyProps, mockedInjector);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // ok
    }
  }

  @Test
  public void init_inLogExists_outLogIsNull_ok() throws Exception {

    String inLogName = "app_log_in";

    // call init()
    new LogConfigurator(inLogName, null, null, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(inLogName))), eq(false), eq(null));
    verify(mockedLogWriter, never()).writeToLog(any(), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogIsAuto_ok() throws Exception {

    String inLogName = "app_log_in";
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, null, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(inLogName))), eq(false), eq(null));
    verify(mockedLogWriter, never()).writeToLog(eq(new LogInfo("app_random1")), anyBoolean());
  }

  @Test
  public void init_inLogIsAuto_outLogIsAuto_ok() throws Exception {

    String inLogName = "auto";
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, null, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), eq(true), eq(null));
    verify(mockedLogWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_inLogIsNew_outLogIsAuto_ok() throws Exception {

    String inLogName = "new_app_log";
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, null, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).createLog(any(LogInfo.class));
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(inLogName))), eq(false), eq(null));
    verify(mockedLogWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo("auto"))), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogExists_ok() throws Exception {

    String inLogName = "app_log_in";
    String outLogName = "app_log_out";

    // call init()
    new LogConfigurator(inLogName, null, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).logExists(outLogName);
    verify(mockedPalDirectory, never()).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(inLogName))), eq(false), eq(null));
    verify(mockedLogWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(outLogName))), anyBoolean());
  }

  @Test
  public void init_inLogExists_sameOutLogExists_ok() throws Exception {

    String logName = "app_log_10";

    // call init()
    new LogConfigurator(logName, null, logName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(logName);
    verify(mockedPalDirectory, never()).getLogInfo(logName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader)
        .readFromLog(argThat(new LogInfoMatcher(new LogInfo(logName))), eq(true), eq(null));
    verify(mockedLogWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(logName))), anyBoolean());
  }

  @Test
  public void init_inLogIsNull_outLogExists_ok() throws Exception {

    String outLogName = "app_log_out";

    // call init()
    new LogConfigurator(null, null, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(outLogName);
    verify(mockedPalDirectory, never()).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).createAutoLog(any(), any());
    verify(mockedLogReader, never()).readFromLog(any(), anyBoolean(), anyLong());
    verify(mockedLogWriter)
        .writeToLog(argThat(new LogInfoMatcher(new LogInfo(outLogName))), anyBoolean());
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
