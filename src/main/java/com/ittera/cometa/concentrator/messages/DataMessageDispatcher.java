package com.ittera.cometa.concentrator.messages;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

public interface DataMessageDispatcher {

  void send(DataMessage message);

  DataMessage receiveMsgForCurrentThread();

  void acceptConnections(boolean acceptConnections);
}
