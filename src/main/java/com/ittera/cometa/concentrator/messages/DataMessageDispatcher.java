package com.ittera.cometa.concentrator.messages;

public interface DataMessageDispatcher extends Runnable {
  void requestShutdown();
}
