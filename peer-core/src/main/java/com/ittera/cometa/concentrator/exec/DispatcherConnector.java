package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

public interface DispatcherConnector {

	DataMessage sendAndRecv(DataMessage message);

	void closeThreadLocalSocket();
}
