package com.ittera.cometa.messages;

import org.zeromq.ZMQ;

public abstract class BaseMsg {

  protected int size = -1;
  // Non-blocking (as opposed to ZMsg.send() with no flag, which is blocking)
  public abstract boolean send(ZMQ.Socket socket);

  public int getSize() {
    return size;
  }
}
