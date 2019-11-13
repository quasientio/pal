package com.ittera.cometa.cxn;

import com.ittera.cometa.common.znodes.LogInfo;

public class NoLogInfoNodeException extends Exception {

  private LogInfo logInfo;

  public NoLogInfoNodeException(String message) {
    super(message);
  }

  public NoLogInfoNodeException(String message, LogInfo logInfo) {
    super(message);
    this.logInfo = logInfo;
  }

  public LogInfo getLogInfo() {
    return logInfo;
  }
}
