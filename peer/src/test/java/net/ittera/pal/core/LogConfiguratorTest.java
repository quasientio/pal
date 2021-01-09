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

import static org.mockito.Mockito.*;

import com.google.inject.Injector;
import java.util.Properties;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.PALDirectory;
import org.junit.Before;
import org.junit.Test;

public class LogConfiguratorTest {

  private PALDirectory mockedPalDirectory;
  private LogReader mockedLogReader;
  private LogWriter mockedLogWriter;
  private Injector mockedInjector;

  @Before
  public void setUp() throws Exception {
    mockedPalDirectory = mock(PALDirectory.class);
    mockedLogReader = mock(LogReader.class);
    mockedLogWriter = mock(LogWriter.class);
    mockedInjector = mock(Injector.class);
    when(mockedInjector.getInstance(PALDirectory.class)).thenReturn(mockedPalDirectory);
    when(mockedInjector.getInstance(LogReader.class)).thenReturn(mockedLogReader);
    when(mockedInjector.getInstance(LogWriter.class)).thenReturn(mockedLogWriter);
  }

  @Test
  public void init_inLogExists_outLogIsNull() throws Exception {

    // mock interactions
    String inLogName = "applog_in";
    when(mockedPalDirectory.logExists(inLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(inLogName)).thenReturn(new LogInfo(inLogName));
    Long inLogOffset = null;
    String outLogName = null;

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory).logExists(inLogName);
    verify(mockedPalDirectory).getLogInfo(inLogName);
    verify(mockedLogReader).readFromLog(inLogName, false, inLogOffset);
    verify(mockedLogWriter, never()).writeToLog(any(), any(), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogIsAuto() throws Exception {

    // mock interactions
    String inLogName = "applog_in";
    Long inLogOffset = null;
    when(mockedPalDirectory.logExists(inLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(inLogName)).thenReturn(new LogInfo(inLogName));
    String outLogName = "auto";
    String generatedLogName = "app_random1";
    when(mockedPalDirectory.newLog(any())).thenReturn(new LogInfo(generatedLogName));

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory).logExists(inLogName);
    verify(mockedPalDirectory).getLogInfo(inLogName);
    verify(mockedPalDirectory).newLog(any());
    verify(mockedLogReader).readFromLog(inLogName, false, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(generatedLogName)), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogIsAuto_outLogIsAuto() throws Exception {

    String inLogName = "auto";
    String generatedLogName = "app_random1";
    when(mockedPalDirectory.newLog(any())).thenReturn(new LogInfo(generatedLogName));
    Long inLogOffset = null;
    String outLogName = "auto";

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory, never()).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory).newLog(any());
    verify(mockedLogReader).readFromLog(generatedLogName, true, inLogOffset);
    // NOTE: when both inLog and outLog are "auto", a single log is created and used as both in and
    // out logs
    verify(mockedLogWriter)
        .writeToLog(
            eq(new LogInfo(generatedLogName)), eq(new LogInfo(generatedLogName)), anyBoolean());
  }

  @Test
  public void init_inLogIsNew_outLogIsAuto() throws Exception {

    String inLogName = "new_applog";
    when(mockedPalDirectory.logExists(inLogName)).thenReturn(false);
    when(mockedPalDirectory.registerLog(inLogName)).thenReturn(new LogInfo(inLogName));
    Long inLogOffset = null;
    String outLogName = "auto";
    String generatedLogName = "app_random1";
    when(mockedPalDirectory.newLog(any())).thenReturn(new LogInfo(generatedLogName));

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory).logExists(inLogName);
    verify(mockedPalDirectory, never()).getLogInfo(inLogName);
    verify(mockedPalDirectory).registerLog(inLogName);
    verify(mockedPalDirectory).newLog(any());
    verify(mockedLogReader).readFromLog(inLogName, false, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(generatedLogName)), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogExists_outLogExists() throws Exception {

    // mock interactions
    String inLogName = "applog_in";
    Long inLogOffset = null;
    when(mockedPalDirectory.logExists(inLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(inLogName)).thenReturn(new LogInfo(inLogName));
    String outLogName = "applog_out";
    when(mockedPalDirectory.logExists(outLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(outLogName)).thenReturn(new LogInfo(outLogName));

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory).logExists(inLogName);
    verify(mockedPalDirectory).getLogInfo(inLogName);
    verify(mockedPalDirectory).logExists(outLogName);
    verify(mockedPalDirectory).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).newLog(any());
    verify(mockedLogReader).readFromLog(inLogName, false, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(outLogName)), eq(new LogInfo(inLogName)), anyBoolean());
  }

  @Test
  public void init_inLogExists_sameOutLogExists() throws Exception {

    // mock interactions
    String logName = "applog_10";
    Long inLogOffset = null;
    when(mockedPalDirectory.logExists(logName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(logName)).thenReturn(new LogInfo(logName));

    // call init()
    new LogConfigurator(logName, inLogOffset, logName, new Properties(), mockedInjector).init();

    // verify interactions
    verify(mockedPalDirectory, times(2)).logExists(logName);
    verify(mockedPalDirectory, times(2)).getLogInfo(logName);
    verify(mockedPalDirectory, never()).newLog(any());
    verify(mockedLogReader).readFromLog(logName, true, inLogOffset);
    verify(mockedLogWriter)
        .writeToLog(eq(new LogInfo(logName)), eq(new LogInfo(logName)), anyBoolean());
  }

  @Test
  public void init_inLogIsNull_outLogExists() throws Exception {

    // mock interactions
    String inLogName = null;
    Long inLogOffset = null;
    String outLogName = "applog_out";
    when(mockedPalDirectory.logExists(outLogName)).thenReturn(true);
    when(mockedPalDirectory.getLogInfo(outLogName)).thenReturn(new LogInfo(outLogName));

    // call init()
    new LogConfigurator(inLogName, inLogOffset, outLogName, new Properties(), mockedInjector)
        .init();

    // verify interactions
    verify(mockedPalDirectory).logExists(outLogName);
    verify(mockedPalDirectory).getLogInfo(outLogName);
    verify(mockedPalDirectory, never()).newLog(any());
    verify(mockedLogReader, never()).readFromLog(any(), anyBoolean(), anyLong());
    verify(mockedLogWriter).writeToLog(eq(new LogInfo(outLogName)), eq(null), anyBoolean());
  }
}
