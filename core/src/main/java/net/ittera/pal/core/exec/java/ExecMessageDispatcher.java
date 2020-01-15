package net.ittera.pal.core.exec.java;

import net.ittera.pal.messages.protobuf.Exec.ExecMessage;

public interface ExecMessageDispatcher {
  ExecMessage dispatchIncoming(ExecMessage incomingCall);

  ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect);
}
