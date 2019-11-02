package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;

public interface ExecMessageDispatcher {
  ExecMessage dispatchIncoming(ExecMessage incomingCall);

  ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect);
}
