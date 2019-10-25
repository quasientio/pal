package com.ittera.cometa.core;

import com.google.inject.Injector;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PALDirectory;
import java.util.EnumSet;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogConfigurator {

  protected static final Logger logger = LoggerFactory.getLogger(LogConfigurator.class);
  private final String inLogName;
  private final String outLogName;
  private final Long inLogOffset;
  private final Properties appProps;
  private final Injector injector;
  private final EnumSet<RunOptions> runOptions;

  LogConfigurator(
      String inLogName,
      Long inLogOffset,
      String outLogName,
      Properties appProps,
      EnumSet<RunOptions> runOptions,
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
    LogInfo inLog, outLog, newLog = null;

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
