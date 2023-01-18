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

import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.objects.ObjectStore;
import net.ittera.pal.core.NoSuchSessionException;
import net.ittera.pal.core.SessionStore;
import net.ittera.pal.core.exec.UnsupportedMessageException;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IncomingMessageDispatcher {

  protected static final Logger logger = LoggerFactory.getLogger(IncomingMessageDispatcher.class);
  @Inject private ObjectStore objectStore;
  @Inject private SessionStore sessionStore;
  @Inject MessageBuilder messageBuilder;
  @Inject private UUID peerUuid;

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

    final ExecMessageType execMessageType =
        ExecMessageType.values()[execMessage.getExecMessageType()];
    switch (execMessageType) {
      case CONSTRUCTOR:
        return constructorDispatcher.dispatchIncoming(execMessage, isDirect);
      case INSTANCE_METHOD:
        return instanceMethodDispatcher.dispatchIncoming(execMessage, isDirect);
      case CLASS_METHOD:
        return classMethodDispatcher.dispatchIncoming(execMessage, isDirect);
      case GET_STATIC:
        return getClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case GET_FIELD:
        return getInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case PUT_STATIC:
        return setClassVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      case PUT_FIELD:
        return setInstanceVariableDispatcher.dispatchIncoming(execMessage, isDirect);
      default:
        throw new UnsupportedMessageException(
            String.format(
                "Incoming message ignored - no handler:%n%s", ColferUtils.format(execMessage)));
    }
  }

  public ControlMessage incomingControlMessage(ControlMessage controlMessage) {
    final UUID remotePeerUuid = UUID.fromString(controlMessage.getPeerUuid());
    final ControlCommandType commandType = ControlCommandType.values()[controlMessage.getCommand()];
    String body = null;
    ControlStatusType statusType;
    switch (commandType) {
      case DELETE_OBJECT:
        ObjectRef objectRef = ObjectRef.from(controlMessage.getBody());
        try {
          if (sessionStore.deleteObject(remotePeerUuid, objectRef)) {
            statusType = ControlStatusType.OK;
          } else {
            statusType = ControlStatusType.NO_SUCH_OBJECT;
          }
          // delete object reference in objectStore
          objectStore.remove(objectRef);
          logger.info("Object {} deleted for peer w/uuid: {}", objectRef, remotePeerUuid);
        } catch (NoSuchSessionException ex) {
          statusType = ControlStatusType.NO_SUCH_SESSION;
        } catch (Exception ex) {
          statusType = ControlStatusType.ERROR;
          body = ex.getMessage();
          logger.error("Error deleting object w/objectRef: {}", objectRef, ex);
        }
        break;
      case DELETE_SESSION:
        Set<Entry<ObjectRef, Object>> objectEntriesInSession;
        try {
          objectEntriesInSession = sessionStore.getEntriesInSession(remotePeerUuid);
          // delete references to objects in objectStore
          objectStore.removeAll(
              objectEntriesInSession.stream().map(Entry::getKey).collect(Collectors.toList()));
          // delete session
          sessionStore.deleteSession(remotePeerUuid);
          statusType = ControlStatusType.OK;
          logger.info("Session deleted for peer w/uuid: {}", remotePeerUuid);
        } catch (NoSuchSessionException ex) {
          statusType = ControlStatusType.NO_SUCH_SESSION;
        } catch (Exception ex) {
          statusType = ControlStatusType.ERROR;
          body = ex.getMessage();
          logger.error("Error deleting session for peer with uuid: {}", remotePeerUuid, ex);
        }
        break;
      default:
        String errorMessage =
            String.format(
                "Incoming message w/uuid %s ignored - no handler:%n%s",
                controlMessage.getMessageUuid(), ColferUtils.format(controlMessage));
        logger.warn(errorMessage);
        statusType = ControlStatusType.UNSUPPORTED_COMMAND;
        body = errorMessage;
    }

    return messageBuilder.buildControlMessage(remotePeerUuid, statusType, body);
  }
}
