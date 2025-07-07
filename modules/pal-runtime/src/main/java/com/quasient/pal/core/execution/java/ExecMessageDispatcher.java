/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;

/**
 * Defines a contract for dispatching execution messages for processing.
 *
 * <p>Implementations of this interface are responsible for processing incoming {@link ExecMessage}
 * instances and returning an appropriate response, if applicable. Additionally, a dispatcher must
 * indicate the type of message it is capable of handling through the {@link
 * #getSupportedMessageType()} method.
 */
public interface ExecMessageDispatcher {

  /**
   * Dispatches the provided execution message.
   *
   * @param incomingCall the execution message to process; should be non-null.
   * @param messageChannel flag indicating whether the message is via Peer (direct) or from a Log
   *     (non-direct)
   * @return the response execution message produced after processing, or null if no response is
   *     produced.
   */
  ExecMessage dispatchIncoming(ExecMessage incomingCall, MessageChannelType messageChannel);

  /**
   * Retrieves the message type that this dispatcher is designed to handle.
   *
   * @return the supported {@link MessageType} that identifies the kind of execution messages this
   *     dispatcher can process.
   */
  MessageType getSupportedMessageType();
}
