package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

public interface ExecMessageDispatcher {
	ExecMessage dispatchIncoming(ExecMessage incomingCall);

	ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect);
}
