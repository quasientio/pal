package com.ittera.cometa.concentrator.messages;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

public interface DataMessageDispatcher extends Runnable {
  void requestShutdown();

  void send(DataMessage message);

  DataMessage receiveMsgForCurrentThread();
}
