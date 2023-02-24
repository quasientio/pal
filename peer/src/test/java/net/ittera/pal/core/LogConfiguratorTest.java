/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

import com.google.inject.Injector;
import java.util.Optional;
import java.util.Properties;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LogConfiguratorTest {

  private PALDirectory mockedPalDirectory;
  private LogReader mockedLogReader;
  private LogWriter mockedLogWriter;
  private Injector mockedInjector;
  private Properties appProps;
  private static final String KAFKA_TOPIC_PREFIX = "testapp";
  private static final String KAFKA_SERVERS = "kafka1:9092,kafka2:9094";

  @Before
  public void setUp() throws Exception {
    appProps = new Properties();
    appProps.setProperty("kafka.bootstrap.servers", KAFKA_SERVERS);
    appProps.setProperty("kafkaTopicPrefix", KAFKA_TOPIC_PREFIX);

    DirectoryConnectionProvider mockedDirectoryConnectionProvider =
        mock(DirectoryConnectionProvider.class);
    mockedPalDirectory = mock(PALDirectory.class);
    mockedLogReader = mock(LogReader.class);
    mockedLogWriter = mock(LogWriter.class);
    mockedInjector = mock(Injector.class);
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
  public void init_missingKafkaServersInProperties_illegalArgumentException() throws Exception {
    String inLogName = "applog_in";
    Long inLogOffset = null;
    String outLogName = null;
    Properties emptyProps = new Properties();

    // call init()
    try {
      new LogConfigurator(inLogName, inLogOffset, outLogName, emptyProps, mockedInjector);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException iae) {
      // ok
    }
  }

  @Test
  public void init_inLogExists_outLogIsNull_ok() throws Exception {

    String inLogName = "applog_in";
    Long inLogOffset = null;
    String outLogName = null;

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedLogReader).readFromLog(new LogInfo(inLogName), false, inLogOffset);
    verify(mockedLogWriter, never()).writeToLog(any(), any(), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogIsAuto_ok() throws Exception {

    String inLogName = "applog_in";
    Long inLogOffset = null;
    String outLogName = "auto";
    String generatedLogName = "app_random1";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader).readFromLog(new LogInfo(inLogName), false, inLogOffset);
    verify(mockedLogWriter, never())
        .writeToLog(eq(new LogInfo(generatedLogName)), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogIsAuto_outLogIsAuto_ok() throws Exception {

    String inLogName = "auto";
    Long inLogOffset = null;
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader).readFromLog(new LogInfo("auto"), true, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo("auto")), eq(new LogInfo("auto")), anyBoolean());
  }

  @Test
  public void init_inLogIsNew_outLogIsAuto_ok() throws Exception {

    String inLogName = "new_applog";
    Long inLogOffset = null;
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).registerLog(any(LogInfo.class));
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader).readFromLog(new LogInfo(inLogName), false, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo("auto")), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogExists_ok() throws Exception {

    String inLogName = "applog_in";
    Long inLogOffset = null;
    String outLogName = "applog_out";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory, never()).logExists(outLogName);
    verify(mockedPalDirectory, never()).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader).readFromLog(new LogInfo(inLogName), false, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(outLogName)), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogExists_sameOutLogExists_ok() throws Exception {

    String logName = "applog_10";
    Long inLogOffset = null;

    // call init()
    new LogConfigurator(logName, inLogOffset, logName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(logName);
    verify(mockedPalDirectory, never()).getLogInfo(logName);
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader).readFromLog(new LogInfo(logName), true, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(logName)), eq(new LogInfo(logName)), anyBoolean());
  }

  @Test
  public void init_inLogIsNull_outLogExists_ok() throws Exception {

    String inLogName = null;
    Long inLogOffset = null;
    String outLogName = "applog_out";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, appProps, mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(outLogName);
    verify(mockedPalDirectory, never()).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).newLog(any(), any());
    verify(mockedLogReader, never()).readFromLog(any(), anyBoolean(), anyLong());
    verify(mockedLogWriter).writeToLog(eq(new LogInfo(outLogName)), eq(null), anyBoolean());
  }
}
