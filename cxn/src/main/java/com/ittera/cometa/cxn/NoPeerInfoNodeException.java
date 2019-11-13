package com.ittera.cometa.cxn;

import com.ittera.cometa.common.znodes.PeerInfo;

public class NoPeerInfoNodeException extends Exception {

  private PeerInfo peerInfo;

  public NoPeerInfoNodeException(String message) {
    super(message);
  }

  public NoPeerInfoNodeException(String message, PeerInfo peerInfo) {
    super(message);
    this.peerInfo = peerInfo;
  }

  public PeerInfo getPeerInfo() {
    return peerInfo;
  }
}
