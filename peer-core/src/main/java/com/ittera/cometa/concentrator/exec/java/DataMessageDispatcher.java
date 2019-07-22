package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

public interface DataMessageDispatcher {
	DataMessage dispatchIncoming(DataMessage incomingCall);
}
