package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

/**
 * TODO: This class should NOT depend on protobuf
 */
public interface MessageBroker {

  void send(DataMessage message);

  DataMessage receiveMsgForCurrentThread();

  void shutdown();
}
