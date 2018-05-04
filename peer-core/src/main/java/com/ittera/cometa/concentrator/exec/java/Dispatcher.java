package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

public interface Dispatcher {

	Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable;

	DataMessage dispatchIncoming(DataMessage incomingCall);
}
