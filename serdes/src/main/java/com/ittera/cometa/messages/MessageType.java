package com.ittera.cometa.messages;

public enum MessageType {
  ExecMessage,
  InterceptMessage,
  InterceptKey,
  Unknown;

  public static final MessageType[] values = values();
}
