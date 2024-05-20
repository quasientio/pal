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
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.messages.SessionCommandMsg;
import net.ittera.pal.core.messages.SessionReplyMsg;
import net.ittera.pal.messages.types.SessionStatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

// The remote peer's UUID is used as its sessionId
@Singleton
public class SessionService extends ConnectedService {

  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

  // one objectRef -> object map for each peer
  private final Map<UUID, HashMap<ObjectRef, Object>> sessionsMap;
  private final ObjectLookupStore objectLookupStore;
  private final String repAddress;
  private Socket repSocket;

  @Inject
  public SessionService(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Session.service") String serviceName,
      @Named("session.svc") String repAddress,
      ObjectLookupStore objectLookupStore) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.repAddress = repAddress;
    this.objectLookupStore = objectLookupStore;
    sessionsMap = new HashMap<>();
  }

  private boolean storeInSession(@Nonnull UUID sessionId, @Nonnull ObjectRef objectRef) {
    final Object object = objectLookupStore.lookupObject(objectRef);
    if (object == null) {
      logger.warn("Cannot store a null object in session, for {}", objectRef.asString());
      return false;
    }
    HashMap<ObjectRef, Object> peerSession = sessionsMap.get(sessionId);
    if (peerSession == null) {
      peerSession = new HashMap<>();
      sessionsMap.put(sessionId, peerSession);
      logger.info("New session created w/uuid: {}", sessionId);
    }

    peerSession.put(objectRef, object);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Object w/objectRef: {} stored in session w/uuid: {}", objectRef.asString(), sessionId);
    }
    return true;
  }

  private void deleteSession(@Nonnull UUID sessionId) throws NoSuchSessionException {
    HashMap<ObjectRef, Object> peerSession = sessionsMap.get(sessionId);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found w/uuid: " + sessionId);
    }
    final long objectsInSession = peerSession.size();
    peerSession.clear();
    sessionsMap.remove(sessionId);
    if (logger.isDebugEnabled()) {
      logger.debug("Session w/uuid: {} deleted ({} objects)", sessionId, objectsInSession);
    }
  }

  private boolean deleteObject(@Nonnull UUID sessionId, @Nonnull ObjectRef objectRef)
      throws NoSuchSessionException {
    Map<ObjectRef, Object> peerSession = sessionsMap.get(sessionId);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found w/uuid: " + sessionId);
    }
    boolean deleted = peerSession.remove(objectRef) != null;
    if (logger.isDebugEnabled()) {
      if (deleted) {
        logger.debug(
            "Object w/objectRef: {} cleared from session w/uuid: {}", objectRef, sessionId);
      } else {
        logger.debug("Object w/objectRef: {} was not in session w/uuid: {}", objectRef, sessionId);
      }
    }
    return deleted;
  }

  private Set<ObjectRef> getObjectRefsInSession(@Nonnull UUID sessionId)
      throws NoSuchSessionException {
    final HashMap<ObjectRef, Object> peerSession = sessionsMap.get(sessionId);
    if (peerSession == null) {
      throw new NoSuchSessionException("No session found w/uuid: " + sessionId);
    }
    if (logger.isDebugEnabled()) {
      logger.debug(
          "objectrefs in session: {}",
          peerSession.keySet().stream().map(ObjectRef::asString).collect(Collectors.joining(",")));
    }
    return peerSession.keySet();
  }

  @Override
  protected void run() {
    boolean socketError = false;
    while (!Thread.interrupted() && !socketError) {
      SessionCommandMsg cmdMsg = null;
      try {
        cmdMsg = SessionCommandMsg.receive(repSocket, true);
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught ETERM during blocking read. Breaking out.");
          }
          socketError = true;
        } else if (errorCode == ZError.EINTR) {
          if (logger.isDebugEnabled()) {
            logger.debug("Caught EINTR during blocking read. Breaking out.");
          }
          socketError = true;
        } else {
          throw ex;
        }
      } catch (Exception e) {
        logger.error("Error receiving message", e);
      }
      if (cmdMsg == null) {
        continue;
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Received new message: {} ({} bytes)", cmdMsg, cmdMsg.getSize());
      }

      final SessionReplyMsg replyMsg;
      SessionStatusType status;
      switch (cmdMsg.getCommand()) {
        case STORE_OBJECT:
          Objects.requireNonNull(cmdMsg.getSessionId());
          Objects.requireNonNull(cmdMsg.getObjectRef());
          boolean stored = false;
          try {
            stored = storeInSession(cmdMsg.getSessionId(), cmdMsg.getObjectRef());
          } catch (Exception e) {
            logger.error("Error storing object in session w/uuid: {}", cmdMsg.getSessionId(), e);
          }
          status = stored ? SessionStatusType.OK : SessionStatusType.ERROR;
          replyMsg = new SessionReplyMsg(status);
          break;
        case DELETE_OBJECT:
          Objects.requireNonNull(cmdMsg.getSessionId());
          Objects.requireNonNull(cmdMsg.getObjectRef());
          boolean objectDeleted;
          try {
            objectDeleted = deleteObject(cmdMsg.getSessionId(), cmdMsg.getObjectRef());
            status = objectDeleted ? SessionStatusType.OK : SessionStatusType.NO_SUCH_OBJECT;
          } catch (NoSuchSessionException e) {
            logger.error(
                "No session found w/uuid: {} while deleting object w/objectRef: {}",
                cmdMsg.getSessionId(),
                cmdMsg.getObjectRef().asString(),
                e);
            status = SessionStatusType.NO_SUCH_SESSION;
          } catch (Exception e) {
            logger.error(
                "Unexpected error deleting object w/objectRef: {} from session w/uuid: {}",
                cmdMsg.getObjectRef() == null ? "<null>" : cmdMsg.getObjectRef().asString(),
                cmdMsg.getSessionId(),
                e);
            status = SessionStatusType.ERROR;
          }
          replyMsg = new SessionReplyMsg(status);
          break;
        case DELETE_SESSION:
          Objects.requireNonNull(cmdMsg.getSessionId());
          Set<ObjectRef> objectsInSession = null;
          try {
            // make a copy since the keySet returned will be empty after deleteSession()
            objectsInSession = new HashSet<>(getObjectRefsInSession(cmdMsg.getSessionId()));
            deleteSession(cmdMsg.getSessionId());
            status = SessionStatusType.OK;
          } catch (NoSuchSessionException e) {
            logger.error("No session found w/uuid: {}", cmdMsg.getSessionId(), e);
            status = SessionStatusType.NO_SUCH_SESSION;
          } catch (Exception e) {
            logger.error("Unexpected error deleting session w/uuid: {}", cmdMsg.getSessionId(), e);
            status = SessionStatusType.ERROR;
          }
          replyMsg =
              new SessionReplyMsg(
                  status, objectsInSession != null ? objectsInSession : new HashSet<>());
          break;
        case CLEAR_SESSIONS:
          sessionsMap.clear();
          logger.info("All sessions cleared.");
          status = SessionStatusType.OK;
          replyMsg = new SessionReplyMsg(status);
          break;
        default:
          replyMsg = new SessionReplyMsg(SessionStatusType.UNSUPPORTED_SESSION_CMD);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Sending back reply: {}", replyMsg);
      }
      replyMsg.send(repSocket);
    }
  }

  @Override
  protected void openConnections() {
    repSocket = zmqContext.createSocket(SocketType.REP);
    repSocket.bind(repAddress);
  }

  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
  }
}
