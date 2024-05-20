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

package net.ittera.pal.core.exec.java;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.ittera.pal.core.SessionMessageDispatcher;
import net.ittera.pal.core.exec.UnsupportedMessageException;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class simply delegates messages to the corresponding dispatcher class, depending on their
 * type.
 */
@Singleton
public class IncomingMessageDispatcher {

  protected static final Logger logger = LoggerFactory.getLogger(IncomingMessageDispatcher.class);

  // constructor & method dispatchers
  @SuppressWarnings("unused")
  @Inject
  private ConstructorDispatcher constructorDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private ClassMethodDispatcher classMethodDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private InstanceMethodDispatcher instanceMethodDispatcher;

  // field op dispatchers
  @SuppressWarnings("unused")
  @Inject
  private GetClassVariableDispatcher getClassVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private SetClassVariableDispatcher setClassVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private GetInstanceVariableDispatcher getInstanceVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

  // control message dispatchers
  @SuppressWarnings("unused")
  @Inject
  private SessionMessageDispatcher sessionMessageDispatcher;

  /**
   * Dispatches the incoming message to the corresponding dispatcher.
   *
   * @param execMessage Message to invoke
   * @param isDirect true if message comes from this or another peer, false if it comes from a log
   * @return the returnValue message
   */
  public ExecMessage incomingCall(ExecMessage execMessage, boolean isDirect)
      throws UnsupportedMessageException {

    final ExecMessageType execMessageType =
        ExecMessageType.fromByte(execMessage.getExecMessageType());
    return switch (execMessageType) {
      case CONSTRUCTOR -> constructorDispatcher.dispatchIncoming(execMessage, isDirect);
      case INSTANCE_METHOD -> instanceMethodDispatcher.dispatchIncoming(execMessage, isDirect);
      case CLASS_METHOD -> classMethodDispatcher.dispatchIncoming(execMessage, isDirect);
      case GET_STATIC -> getClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case GET_FIELD -> getInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case PUT_STATIC -> setClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case PUT_FIELD -> setInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      default ->
          throw new UnsupportedMessageException(
              String.format(
                  "Incoming exec message ignored - no handler:%n%s",
                  ColferUtils.format(execMessage)));
    };
  }

  public ControlMessage incomingControlMessage(ControlMessage controlMessage)
      throws UnsupportedMessageException {
    final ControlCommandType commandType = ControlCommandType.fromByte(controlMessage.getCommand());
    return switch (commandType) {
      case DELETE_OBJECT, DELETE_SESSION ->
          sessionMessageDispatcher.incomingControlMessage(controlMessage);
    };
  }
}
