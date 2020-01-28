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
import java.util.Properties;
import java.util.Set;
import net.ittera.pal.common.znodes.LogInfo;
import net.ittera.pal.cxn.PALDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogConfigurator {

  protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);
  private final String inLogName;
  private final String outLogName;
  private final Long inLogOffset;
  private final Properties appProps;
  private final Injector injector;
  private final Set<RunOptions> runOptions;

  LogConfigurator(
      String inLogName,
      Long inLogOffset,
      String outLogName,
      Properties appProps,
      Set<RunOptions> runOptions,
      Injector injector) {
    this.inLogName = inLogName;
    this.inLogOffset = inLogOffset;
    this.outLogName = outLogName;
    this.appProps = appProps;
    this.runOptions = runOptions;
    this.injector = injector;
  }

  private LogInfo registerNewLog() throws Exception {

    final PALDirectory palDirectory = injector.getInstance(PALDirectory.class);
    final String kafkaTopicPrefix = appProps.getProperty("kafkaTopic");
    return palDirectory.newLog(kafkaTopicPrefix);
  }

  private LogInfo getOrRegisterGivenLog(String logName) throws Exception {

    final PALDirectory palDirectory = injector.getInstance(PALDirectory.class);
    final LogInfo logInfo;

    // register given log if not registered
    if (palDirectory.logExists(logName)) {
      logInfo = palDirectory.getLogInfo(logName);
    } else {
      logInfo = palDirectory.registerLog(logName);
    }

    return logInfo;
  }

  private void readFromLog(LogInfo log, boolean inAndOutAreSameLog, Long initialOffset)
      throws Exception {
    LogReader logMessageReader = injector.getInstance(LogReader.class);
    logMessageReader.readFromLog(log.getName(), inAndOutAreSameLog, initialOffset);
  }

  private void writeToLog(LogInfo outLog, LogInfo inLog) {
    LogWriter logMessageWriter = injector.getInstance(LogWriter.class);
    boolean publishOffsets = outLog.equals(inLog);
    logMessageWriter.writeToLog(outLog, inLog, publishOffsets);
  }

  void init() throws Exception {

    // register log(s)
    LogInfo inLog;
    LogInfo outLog;
    LogInfo newLog = null;

    if (inLogName != null) {
      inLog = getOrRegisterGivenLog(inLogName);
    } else { // no log given, create new
      inLog = registerNewLog();
      newLog = inLog;
    }

    if (outLogName != null) {
      outLog = getOrRegisterGivenLog(outLogName);
    } else { // no log given, create new if not done already
      if (newLog == null) {
        newLog = registerNewLog();
      }
      outLog = newLog;
    }

    // init log reader+writer
    readFromLog(inLog, runOptions.contains(RunOptions.INLOG_SAME_AS_OUTLOG), inLogOffset);
    writeToLog(outLog, inLog);
  }
}
