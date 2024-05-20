package net.ittera.pal.core.exec;

import net.ittera.pal.messages.colfer.Message;

public interface MessageDispatchListener {
  void onMessageDispatched(Message event);
}
