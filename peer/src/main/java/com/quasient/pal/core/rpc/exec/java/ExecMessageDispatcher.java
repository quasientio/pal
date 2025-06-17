/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.core.rpc.exec.java;

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
   * @return the response execution message produced after processing, or null if no response is
   *     generated.
   */
  ExecMessage dispatchIncoming(ExecMessage incomingCall);

  /**
   * Dispatches the provided execution message.
   *
   * @param incomingCall the execution message to process; should be non-null.
   * @param isDirect flag indicating whether the message is via Peer (direct) or from a Log
   *     (non-direct)
   * @return the response execution message produced after processing, or null if no response is
   *     produced.
   */
  ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect);

  /**
   * Retrieves the message type that this dispatcher is designed to handle.
   *
   * @return the supported {@link MessageType} that identifies the kind of execution messages this
   *     dispatcher can process.
   */
  MessageType getSupportedMessageType();
}
