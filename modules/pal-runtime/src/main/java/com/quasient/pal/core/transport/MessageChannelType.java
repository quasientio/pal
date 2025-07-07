package com.quasient.pal.core.transport;

/** Enumeration representing the type of messaging channel used for thread creation. */
public enum MessageChannelType {
  /** Socket RPC channel. */
  SOCKET_RPC("SOCKET_RPC"),

  /** Log channel. */
  LOG_RPC("LOG_RPC");

  /** Name representing the message channel type. */
  final String name;

  /**
   * Constructs an MessageChannelType with a given name.
   *
   * @param name The name associated with the RPC channel type.
   */
  MessageChannelType(String name) {
    this.name = name;
  }

  /**
   * Returns the channel's name
   *
   * @return the channel name
   */
  public String getName() {
    return name;
  }
}
