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

package net.ittera.pal.core;

import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.objects.ObjectStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.core.messages.SessionCmdMsg;
import net.ittera.pal.core.messages.SessionReplyMsg;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.SessionCommandType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SessionsMessageDispatcher {
  @Inject private DispatcherConnector dispatcherConnector;
  @Inject private ObjectStore objectStore;
  @Inject private MessageBuilder messageBuilder;

  @Inject private UUID peerUuid;

  private static final Logger logger = LoggerFactory.getLogger(SessionsMessageDispatcher.class);

  public ControlMessage incomingControlMessage(ControlMessage controlMessage) {
    final UUID remotePeerUuid = UUID.fromString(controlMessage.getFromPeer());

    // for clarity - a peer can only delete an object in its own session
    final UUID sessionId = remotePeerUuid;
    final ControlCommandType commandType = ControlCommandType.values()[controlMessage.getCommand()];
    SessionReplyMsg sessionReplyMsg;
    switch (commandType) {
      case DELETE_OBJECT:
        // delete object from peer's session
        final ObjectRef objectRef = ObjectRef.from(controlMessage.getBody());
        sessionReplyMsg =
            dispatcherConnector.sendMessageToSessionService(
                new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef));

        // delete object reference in objectStore
        objectStore.remove(objectRef);
        logger.info("Object {} deleted for peer w/uuid: {}", objectRef, remotePeerUuid);
        return sessionReplyMessageToControlMessage(sessionReplyMsg);
      case DELETE_SESSION:
        // delete session
        sessionReplyMsg =
            dispatcherConnector.sendMessageToSessionService(
                new SessionCmdMsg(SessionCommandType.DELETE_SESSION, sessionId));
        final Set<ObjectRef> objectRefsInSession = sessionReplyMsg.getObjectRefs();
        // delete references to objects in objectStore
        if (objectRefsInSession != null && !objectRefsInSession.isEmpty()) {
          objectStore.removeAll(objectRefsInSession);
        }
        return sessionReplyMessageToControlMessage(sessionReplyMsg);
      default:
        String errorMessage =
            String.format(
                "Incoming message w/uuid %s ignored - no handler:%n%s",
                controlMessage.getMessageUuid(), ColferUtils.format(controlMessage));
        logger.error(errorMessage);
        return messageBuilder.buildControlMessage(
            peerUuid, ControlStatusType.UNSUPPORTED_COMMAND, errorMessage);
    }
  }

  // helper method to map the internal SessionReplyMessage to the public ControlMessage reply
  private ControlMessage sessionReplyMessageToControlMessage(SessionReplyMsg sessionReplyMsg) {
    final ControlStatusType statusType;
    switch (sessionReplyMsg.getStatus()) {
      case OK:
        statusType = ControlStatusType.OK;
        break;
      case ERROR:
        statusType = ControlStatusType.ERROR;
        break;
      case UNSUPPORTED_SESSION_CMD:
        statusType = ControlStatusType.UNSUPPORTED_COMMAND;
        break;
      case NO_SUCH_SESSION:
        statusType = ControlStatusType.NO_SUCH_SESSION;
        break;
      case NO_SUCH_OBJECT:
        statusType = ControlStatusType.NO_SUCH_OBJECT;
        break;
      default:
        statusType = null;
    }

    return messageBuilder.buildControlMessage(peerUuid, statusType);
  }
}
