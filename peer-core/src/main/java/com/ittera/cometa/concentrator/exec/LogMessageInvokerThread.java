package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.Concentrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogMessageInvokerThread extends Thread {

  protected static final Logger logger = LoggerFactory.getLogger(LogMessageInvokerThread.class);

  public LogMessageInvokerThread(ThreadGroup group, Runnable target, String name) {
    super(group, target, name);
    logger.debug("Initialized new log message invoker thread named: {}", name);
  }

  @Override
  public void run() {

    super.run();

    closeConnections();

    logger.debug("Stopped log executor thread: {}", getName());
  }

  protected void closeConnections() {

    Concentrator.closeThreadLocalSocket();
  }

}
