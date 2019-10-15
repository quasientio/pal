package com.ittera.cometa.core.messages;

import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public abstract class BaseMsg<T> {

  protected ZMsg zmsg;

  protected abstract void build();

  /** ZMsg facade */
  public boolean send(ZMQ.Socket socket, boolean destroy) {
    return zmsg.send(socket, destroy);
  }

  public boolean send(ZMQ.Socket socket) {
    return zmsg.send(socket);
  }

  public long contentSize() {
    return zmsg.contentSize();
  }

  public void destroy() {
    zmsg.destroy();
  }

  public int size() {
    return zmsg.size();
  }

  public boolean isEmpty() {
    return zmsg.isEmpty();
  }

  public ZMsg getInner() {
    return zmsg.duplicate();
  }
}
