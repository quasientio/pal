package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers;

public interface IncomingProxyDispatcher {
	Wrappers.DataMessage incomingCall(Wrappers.DataMessage dataMessage);
}
