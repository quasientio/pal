package com.ittera.cometa.common.znodes;

import java.util.UUID;

public class InterceptEvent {

  public enum Type {
    INTERCEPT_ADDED,
    INTERCEPT_REMOVED;
  }

  private final Type type;
  private final String interceptPath;
  private final UUID peerUUID;
  private final UUID interceptUUID;

  public InterceptEvent(Type type, String interceptPath, UUID peerUUID, UUID interceptUUID) {
    this.type = type;
    this.interceptPath = interceptPath;
    this.peerUUID = peerUUID;
    this.interceptUUID = interceptUUID;
  }

  public Type getType() {
    return type;
  }

  public String getInterceptPath() {
    return interceptPath;
  }

  public UUID getPeerUUID() {
    return peerUUID;
  }

  public UUID getInterceptUUID() {
    return interceptUUID;
  }
}
