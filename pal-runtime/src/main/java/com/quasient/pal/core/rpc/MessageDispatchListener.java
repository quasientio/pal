package com.quasient.pal.core.rpc;

import com.quasient.pal.messages.colfer.Message;

/**
 * Defines a listener for message dispatch events.
 *
 * <p>Implementers of this interface should provide custom logic within the {@link
 * #onMessageDispatched(Message)} method to handle messages as they are dispatched.
 */
public interface MessageDispatchListener {

  /**
   * Invoked when a message is dispatched.
   *
   * @param event the dispatched message event; should not be null.
   */
  void onMessageDispatched(Message event);
}
