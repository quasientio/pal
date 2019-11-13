package com.ittera.cometa.cxn;

import com.ittera.cometa.common.znodes.LogRequest;

public class NoLogRequestNodeException extends Exception {

  private LogRequest logRequest;

  public NoLogRequestNodeException(String message) {
    super(message);
  }

  public NoLogRequestNodeException(String message, LogRequest logRequest) {
    super(message);
    this.logRequest = logRequest;
  }

  public LogRequest getLogRequest() {
    return logRequest;
  }
}
