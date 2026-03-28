/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java;

import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.types.MessageType;

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
