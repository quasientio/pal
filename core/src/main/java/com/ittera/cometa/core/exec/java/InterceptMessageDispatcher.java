package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;

public interface InterceptMessageDispatcher {
  boolean dispatchIncoming(InterceptMessage incomingCall, boolean isDirect);
}
