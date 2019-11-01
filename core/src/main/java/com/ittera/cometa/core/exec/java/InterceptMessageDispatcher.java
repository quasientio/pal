package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;

public interface InterceptMessageDispatcher {
  boolean dispatchIncoming(InterceptRequest incomingCall, boolean isDirect);
}
