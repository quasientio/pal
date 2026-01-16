/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn;

import io.quasient.pal.messages.colfer.Message;

/**
 * Listener interface for receiving notifications when incoming messages arrive at a ThinPeer.
 *
 * <p>Implementations of this interface can be registered with a ThinPeer via {@link
 * ThinPeer#addMessageListener(IncomingMessageListener)} to receive callbacks when messages are
 * received on the peer's inbound RPC socket.
 */
public interface IncomingMessageListener {
  /**
   * Called when a new message is received by the ThinPeer.
   *
   * @param message the received message
   */
  void onMessageReceived(Message message);
}
