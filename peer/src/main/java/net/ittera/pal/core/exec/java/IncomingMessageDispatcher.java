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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.core.exec.UnsupportedMessageException;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IncomingMessageDispatcher {

  protected static final Logger logger = LoggerFactory.getLogger(IncomingMessageDispatcher.class);

  // constructor & method dispatchers
  @Inject private ConstructorDispatcher constructorDispatcher;
  @Inject private ClassMethodDispatcher classMethodDispatcher;
  @Inject private InstanceMethodDispatcher instanceMethodDispatcher;

  // fieldop dispatchers
  @Inject private GetClassVariableDispatcher getClassVariableDispatcher;
  @Inject private SetClassVariableDispatcher setClassVariableDispatcher;
  @Inject private GetInstanceVariableDispatcher getInstanceVariableDispatcher;
  @Inject private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

  /**
   * @param execMessage Message to invoke
   * @param isDirect true if message comes from this or another peer, false if it comes from a log
   * @return the returnValue message
   */
  public ExecMessage incomingCall(ExecMessage execMessage, boolean isDirect)
      throws UnsupportedMessageException {

    if (execMessage.hasConstructorCall()) {
      return constructorDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasClassMethodCall()) {
      return classMethodDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceMethodCall()) {
      return instanceMethodDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasStaticFieldGet()) {
      return getClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceFieldGet()) {
      return getInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasStaticFieldPut()) {
      return setClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else if (execMessage.hasInstanceFieldPut()) {
      return setInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
    } else {
      throw new UnsupportedMessageException(
          String.format("Incoming message ignored - no handler:%n%s", execMessage));
    }
  }

  public boolean incomingIntercept(InterceptMessage interceptMessage, boolean isDirect) {
    logger.warn("DEPRECATED incomingCall with intercept msg: {}", interceptMessage);
    return false;
  }
}
