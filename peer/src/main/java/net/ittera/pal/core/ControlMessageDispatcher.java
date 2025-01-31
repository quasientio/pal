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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.messages.SessionCommandMsg;
import net.ittera.pal.core.messages.SessionResponseMsg;
import net.ittera.pal.core.rpc.DispatcherConnector;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.SessionCommandType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ControlMessageDispatcher {
  @SuppressWarnings("unused")
  @Inject
  private DispatcherConnector dispatcherConnector;

  @SuppressWarnings("unused")
  @Inject
  private ObjectLookupStore objectLookupStore;

  @SuppressWarnings("unused")
  @Inject
  private MessageBuilder messageBuilder;

  @SuppressWarnings("unused")
  @Inject
  private UUID peerUuid;

  private static final Logger logger = LoggerFactory.getLogger(ControlMessageDispatcher.class);

  public ControlMessage incomingControlMessage(ControlMessage controlMessage) {

    final UUID remotePeerUuid = UUID.fromString(controlMessage.getFromPeer());
    final String controlMessageId = controlMessage.getMessageId();
    final ControlCommandType commandType = ControlCommandType.fromId(controlMessage.getCommand());
    SessionResponseMsg sessionResponseMsg;
    switch (commandType) {
      case DELETE_OBJECT:
        // delete object from peer's session
        // NOTE: the remotePeerUuid is the session id of the peer
        final ObjectRef objectRef =
            ObjectRef.from(controlMessage.getParams()[0].getValue().getRef());
        sessionResponseMsg =
            dispatcherConnector.sendMessageToSessionService(
                new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, remotePeerUuid, objectRef));

        // delete object reference in objectLookupStore
        objectLookupStore.remove(objectRef);
        logger.info("Object {} deleted for peer w/uuid: {}", objectRef, remotePeerUuid);
        return sessionResponseMessageToControlMessage(sessionResponseMsg, controlMessageId);
      case DELETE_SESSION:
        // delete session
        sessionResponseMsg =
            dispatcherConnector.sendMessageToSessionService(
                new SessionCommandMsg(SessionCommandType.DELETE_SESSION, remotePeerUuid));
        final Set<ObjectRef> objectRefsInSession = sessionResponseMsg.getObjectRefs();
        // delete references to objects in objectLookupStore
        if (objectRefsInSession != null && !objectRefsInSession.isEmpty()) {
          objectLookupStore.removeAll(objectRefsInSession);
        }
        return sessionResponseMessageToControlMessage(sessionResponseMsg, controlMessageId);
      case GC:
        ControlStatusType status =
            invokeGCReflectively() ? ControlStatusType.OK : ControlStatusType.ERROR;
        return messageBuilder.buildControlStatusMessage(peerUuid, status, controlMessageId);
      default:
        String errorMessage =
            String.format(
                "Incoming Control message w/id %s ignored - no handler:%n%s",
                controlMessageId, ColferUtils.format(controlMessage));
        logger.error(errorMessage);
        return messageBuilder.buildControlStatusMessage(
            peerUuid, ControlStatusType.UNSUPPORTED, controlMessageId, errorMessage);
    }
  }

  private boolean invokeGCReflectively() {
    try {
      Class<?> runtimeClass = Class.forName("java.lang.Runtime");
      Method getRuntimeMethod = runtimeClass.getMethod("getRuntime");
      Object runtimeInstance = getRuntimeMethod.invoke(null);
      Method gcMethod = runtimeClass.getMethod("gc");
      gcMethod.invoke(runtimeInstance);
      if (logger.isDebugEnabled()) {
        logger.debug("Garbage collector invoked successfully.");
      }
      return true;
    } catch (Exception e) {
      logger.error("Error while invoking Runtime.gc()", e);
      return false;
    }
  }

  // helper method to map the internal SessionResponseMessage to the public ControlMessage response
  @SuppressWarnings("CheckStyle")
  private ControlMessage sessionResponseMessageToControlMessage(
      SessionResponseMsg sessionResponseMsg, String requestId) {
    final ControlStatusType statusType =
        switch (sessionResponseMsg.getStatus()) {
          case OK -> ControlStatusType.OK;
          case ERROR -> ControlStatusType.ERROR;
          case UNSUPPORTED_SESSION_CMD -> ControlStatusType.UNSUPPORTED;
          case NO_SUCH_SESSION -> ControlStatusType.NO_SUCH_SESSION;
          case NO_SUCH_OBJECT -> ControlStatusType.NO_SUCH_OBJECT;
        };

    return messageBuilder.buildControlStatusMessage(peerUuid, statusType, requestId);
  }
}
