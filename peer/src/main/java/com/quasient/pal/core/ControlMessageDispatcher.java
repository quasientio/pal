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

package com.quasient.pal.core;

import com.quasient.pal.common.objects.ObjectLookupStore;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.core.messages.SessionCommandMsg;
import com.quasient.pal.core.messages.SessionResponseMsg;
import com.quasient.pal.core.rpc.DispatcherConnector;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.types.ControlCommandType;
import com.quasient.pal.messages.types.ControlStatusType;
import com.quasient.pal.messages.types.SessionCommandType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes incoming control messages and dispatches the corresponding commands to session
 * services.
 *
 * <p>This dispatcher interprets commands such as object deletion, session deletion, garbage
 * collection, and ping, by leveraging injected dependencies. For deletion commands, it coordinates
 * with the session service via {@link DispatcherConnector} and updates the {@link
 * ObjectLookupStore} accordingly. Garbage collection is triggered reflectively. Responses are
 * constructed using the injected {@link MessageBuilder} and include the unique peer identifier.
 */
@Singleton
public class ControlMessageDispatcher {
  /**
   * Connector to send commands to the session service.
   *
   * <p>Used primarily to forward session-related commands such as DELETE_OBJECT and DELETE_SESSION.
   */
  @SuppressWarnings("unused")
  @Inject
  private DispatcherConnector dispatcherConnector;

  /**
   * Store for keeping track of object references associated with sessions.
   *
   * <p>It is updated when objects or sessions are deleted.
   */
  @SuppressWarnings("unused")
  @Inject
  private ObjectLookupStore objectLookupStore;

  /**
   * Utility for building control message responses.
   *
   * <p>It creates formatted control status messages based on operation outcomes.
   */
  @SuppressWarnings("unused")
  @Inject
  private MessageBuilder messageBuilder;

  /** Unique identifier representing this peer instance. */
  @SuppressWarnings("unused")
  @Inject
  private UUID peerUuid;

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ControlMessageDispatcher.class);

  /**
   * Processes an incoming control message by dispatching the command to appropriate session
   * actions.
   *
   * <p>Depending on the command type embedded in the {@code controlMessage}, this method may:
   *
   * <ul>
   *   <li>Delete an object from a session and remove its reference.
   *   <li>Delete a session and its associated object references.
   *   <li>Trigger garbage collection reflectively.
   *   <li>Respond to a ping with an acknowledgement.
   *   <li>Return an error response for unsupported commands.
   * </ul>
   *
   * @param controlMessage the incoming control message containing the command and associated
   *     parameters.
   * @return a control message response indicating the status of the operation.
   */
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
      case PING:
        return messageBuilder.buildControlStatusMessage(
            peerUuid, ControlStatusType.OK, controlMessageId);
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

  /**
   * Invokes the Java runtime's garbage collector using reflection.
   *
   * <p>This approach (invoking the GC method dynamically) is proven to effectively trigger the
   * collection, whereas a direct method call not always does.
   *
   * @return {@code true} if the garbage collector was invoked successfully, {@code false}
   *     otherwise.
   */
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

  /**
   * Converts a session service response into a standardized control message response.
   *
   * <p>This helper method maps the status from a {@link SessionResponseMsg} to a corresponding
   * {@link ControlStatusType} and then constructs a control message using the injected message
   * builder.
   *
   * @param sessionResponseMsg the response message received from the session service.
   * @param requestId the identifier corresponding to the original control message request.
   * @return a {@link ControlMessage} encapsulating the status of the session operation.
   */
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
