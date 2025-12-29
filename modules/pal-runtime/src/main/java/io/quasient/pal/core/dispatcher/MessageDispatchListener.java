/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import io.quasient.pal.messages.colfer.Message;

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
