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

import com.google.inject.Injector;
import java.util.Objects;
import java.util.Properties;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogConfigurator {

  protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);
  private final String inLogName;
  private final String outLogName;
  private final Long inLogOffset;
  private final Properties appProps;
  private final boolean noPaldir;
  private final Injector injector;
  private final String kafkaBootstrapServers;

  LogConfigurator(
      String inLogName,
      Long inLogOffset,
      String outLogName,
      Properties appProps,
      Injector injector) {
    this.inLogName = inLogName;
    this.inLogOffset = inLogOffset;
    this.outLogName = outLogName;
    this.appProps = appProps;
    this.injector = injector;
    final String givenPaldirUrl = appProps.getProperty("paldir_url");
    noPaldir = givenPaldirUrl == null || givenPaldirUrl.equals(PALDirectory.NO_URL);
    kafkaBootstrapServers = appProps.getProperty("kafka.bootstrap.servers");
    if (noPaldir && kafkaBootstrapServers == null) {
      throw new IllegalArgumentException(
          "No 'paldir_url' nor 'kafka.bootstrap.servers' in properties.");
    }
  }

  private LogInfo registerNewLog() throws Exception {
    final PALDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    return palDirectory.newLog(appProps.getProperty("kafkaTopic"));
  }

  private LogInfo getOrRegisterGivenLog(String logName) throws Exception {

    final PALDirectory palDirectory =
        injector
            .getInstance(DirectoryConnectionProvider.class)
            .get()
            .orElseThrow(RuntimeException::new);
    final LogInfo logInfo;

    // register given log if not registered
    if (palDirectory.logExists(logName)) {
      logInfo = palDirectory.getLogInfo(logName);
    } else {
      logInfo = palDirectory.registerLog(logName);
    }

    return logInfo;
  }

  private void readFromLog(LogInfo inLog, boolean inAndOutAreSameLog, Long initialOffset)
      throws Exception {
    LogReader logMessageReader = injector.getInstance(LogReader.class);
    logMessageReader.readFromLog(inLog, inAndOutAreSameLog, initialOffset);
  }

  private void writeToLog(LogInfo outLog, LogInfo inLog) {
    LogWriter logMessageWriter = injector.getInstance(LogWriter.class);
    logMessageWriter.writeToLog(outLog, inLog, true);
  }

  /**
   * When both inLog and outLog are "auto", a single log is created and used as both in and out Logs
   * With noPaldir, 'auto' does not register a new sequentially-named log, but is taken literally as
   * the log's name.
   *
   * @throws Exception
   */
  void init() throws Exception {

    // register log(s)
    LogInfo inLog = null;
    LogInfo outLog = null;
    LogInfo newLog = null;

    if ("auto".equalsIgnoreCase(inLogName)) {
      if (noPaldir) {
        inLog = new LogInfo(inLogName, kafkaBootstrapServers);
      } else {
        inLog = registerNewLog();
        newLog = inLog;
      }
    } else if (inLogName != null) {
      inLog =
          noPaldir
              ? new LogInfo(inLogName, kafkaBootstrapServers)
              : getOrRegisterGivenLog(inLogName);
    }

    if ("auto".equalsIgnoreCase(outLogName)) {
      if (noPaldir) {
        outLog = new LogInfo(outLogName, kafkaBootstrapServers);
      } else {
        outLog = newLog != null ? newLog : registerNewLog();
      }
    } else if (outLogName != null) {
      outLog =
          noPaldir
              ? new LogInfo(outLogName, kafkaBootstrapServers)
              : getOrRegisterGivenLog(outLogName);
    }

    // init log reader
    if (inLog != null) {
      readFromLog(inLog, Objects.equals(inLog, outLog), inLogOffset);
    }

    // init log writer
    if (outLog != null) {
      writeToLog(outLog, inLog);
    }
  }
}
