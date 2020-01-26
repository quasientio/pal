package net.ittera.pal.core.exec.java;

import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;

public interface InterceptMessageDispatcher {
  boolean dispatchIncoming(InterceptMessage incomingCall, boolean isDirect);
}
