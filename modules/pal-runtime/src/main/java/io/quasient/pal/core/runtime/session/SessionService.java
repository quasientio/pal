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
package io.quasient.pal.core.runtime.session;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.internal.messages.SessionResponseMsg;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.core.service.ConnectedService;
import io.quasient.pal.messages.types.SessionStatusType;
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
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Service responsible for handling session-related commands in the Pal runtime.
 *
 * <p>This class maintains a mapping between peer session identifiers and their stored objects. It
 * processes commands such as storing objects, deleting objects, removing entire sessions, and
 * clearing all sessions by interacting with a ZeroMQ REP socket.
 */
@Singleton
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Service requires shared reference to object lookup store")
public class SessionService extends ConnectedService {

  /** Logging instance. */
  private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

  /**
   * Maps each session's unique identifier (UUID) to its corresponding session storage. Each session
   * storage maps an {@link ObjectRef} to the actual Object instance.
   */
  private final Map<UUID, HashMap<ObjectRef, Object>> sessionsMap;

  /** Object lookup store used to retrieve objects associated with a given reference. */
  private final ObjectLookupStore objectLookupStore;

  /** Address used for binding the ZeroMQ REP (reply) socket for session communication. */
  private final String repAddress;

  /** ZeroMQ REP (reply) socket used to receive session commands and send responses. */
  private Socket repSocket;

  /**
   * Constructs a new SessionService with the required configuration parameters.
   *
   * <p>This service handles session-related operations such as storing objects, deleting objects,
   * and managing session lifecycles. It establishes communication channels using ZeroMQ and relies
   * on an object lookup store to retrieve objects.
   *
   * @param peerUuid Unique identifier for the current peer.
   * @param context ZeroMQ context used to create communication sockets.
   * @param syncSocketAddress Address for synchronization readiness.
   * @param serviceThreadGroup Thread group in which the service operates.
   * @param serviceName Identifier name for this session service instance.
   * @param repAddress Address for binding the REP socket used for session command responses.
   * @param objectLookupStore Service used for looking up objects using their references.
   */
  @Inject
  public SessionService(
      UUID peerUuid,
      ZContext context,
      @Named("sync.ready") String syncSocketAddress,
      ThreadGroup serviceThreadGroup,
      @Named("Session.service") String serviceName,
      @Named("sessionServiceEndpoint") @Nullable String repAddress,
      ObjectLookupStore objectLookupStore) {
    super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
    this.repAddress = repAddress;
    this.objectLookupStore = objectLookupStore;
    sessionsMap = new HashMap<>();
  }

  /**
   * Stores the object associated with the specified object reference into the session.
   *
   * <p>The method retrieves the object using the {@link #objectLookupStore}. If the object is
   * found, it is added to the session corresponding to {@code sessionId}. If the session does not
   * exist, a new session is created.
   *
   * @param sessionId Unique identifier for the session.
   * @param objectRef Reference to the object to be stored.
   * @return {@code true} if the object was successfully stored; {@code false} if the object could
   *     not be retrieved.
   */
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

  /**
   * Deletes the entire session identified by the specified session ID.
   *
   * <p>This method clears all stored objects within the session and removes the session from the
   * internal mapping.
   *
   * @param sessionId Unique identifier for the session to be deleted.
   * @throws NoSuchSessionException if no session exists with the provided {@code sessionId}.
   */
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

  /**
   * Removes a specific object from the session based on its object reference.
   *
   * @param sessionId Unique identifier of the session containing the object.
   * @param objectRef Reference to the object to be removed.
   * @return {@code true} if the object was present in the session and removed; {@code false}
   *     otherwise.
   * @throws NoSuchSessionException if the session identified by {@code sessionId} does not exist.
   */
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

  /**
   * Retrieves all object references stored within the specified session.
   *
   * @param sessionId Unique identifier of the session.
   * @return A set of {@link ObjectRef} instances present in the session.
   * @throws NoSuchSessionException if no session exists with the provided {@code sessionId}.
   */
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

  /**
   * {@inheritDoc}
   *
   * <p>This method implements the main service loop. It continuously reads session commands from
   * the REP socket, processes each command (such as storing objects, deleting objects, or clearing
   * sessions), and sends back an appropriate response. The loop terminates if the thread is
   * interrupted or a socket-related error occurs.
   */
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

      final SessionResponseMsg responseMessage;
      SessionStatusType status;
      switch (cmdMsg.getCommand()) {
        case STORE_OBJECT -> {
          Objects.requireNonNull(cmdMsg.getSessionId());
          Objects.requireNonNull(cmdMsg.getObjectRef());
          boolean stored = false;
          try {
            ObjectRef objRef = cmdMsg.getObjectRef();
            if (objRef == null) {
              throw new IllegalArgumentException("ObjectRef cannot be null");
            }
            UUID sessionId = cmdMsg.getSessionId();
            if (sessionId == null) {
              throw new IllegalArgumentException("SessionId cannot be null");
            }

            stored = storeInSession(sessionId, objRef);
          } catch (Exception e) {
            logger.error("Error storing object in session w/uuid: {}", cmdMsg.getSessionId(), e);
          }
          status = stored ? SessionStatusType.OK : SessionStatusType.ERROR;
          responseMessage = new SessionResponseMsg(status);
        }
        case DELETE_OBJECT -> {
          boolean objectDeleted;
          try {
            objectDeleted =
                deleteObject(
                    Objects.requireNonNull(cmdMsg.getSessionId()),
                    Objects.requireNonNull(cmdMsg.getObjectRef()));
            status = objectDeleted ? SessionStatusType.OK : SessionStatusType.NO_SUCH_OBJECT;
          } catch (NoSuchSessionException e) {
            if (logger.isDebugEnabled()) {
              logger.debug(
                  "No session found w/uuid: {} while deleting object w/objectRef: {}",
                  cmdMsg.getSessionId(),
                  Objects.requireNonNull(cmdMsg.getObjectRef()).asString());
            }
            status = SessionStatusType.NO_SUCH_SESSION;
          } catch (Exception e) {
            logger.error(
                "Unexpected error deleting object w/objectRef: {} from session w/uuid: {}",
                cmdMsg.getObjectRef() == null
                    ? "<null>"
                    : Objects.requireNonNull(cmdMsg.getObjectRef()).asString(),
                cmdMsg.getSessionId(),
                e);
            status = SessionStatusType.ERROR;
          }
          responseMessage = new SessionResponseMsg(status);
        }
        case DELETE_SESSION -> {
          Objects.requireNonNull(cmdMsg.getSessionId());
          Set<ObjectRef> objectsInSession = null;
          try {
            // make a copy since the keySet returned will be empty after deleteSession()
            objectsInSession =
                new HashSet<>(
                    getObjectRefsInSession(Objects.requireNonNull(cmdMsg.getSessionId())));
            deleteSession(Objects.requireNonNull(cmdMsg.getSessionId()));
            status = SessionStatusType.OK;
          } catch (NoSuchSessionException e) {
            if (logger.isDebugEnabled()) {
              logger.debug("No session found w/uuid: {}", cmdMsg.getSessionId());
            }
            status = SessionStatusType.NO_SUCH_SESSION;
          } catch (Exception e) {
            logger.error("Unexpected error deleting session w/uuid: {}", cmdMsg.getSessionId(), e);
            status = SessionStatusType.ERROR;
          }
          responseMessage =
              new SessionResponseMsg(
                  status, objectsInSession != null ? objectsInSession : new HashSet<>());
        }
        case CLEAR_SESSIONS -> {
          sessionsMap.clear();
          logger.info("All sessions cleared.");
          status = SessionStatusType.OK;
          responseMessage = new SessionResponseMsg(status);
        }
        default ->
            responseMessage = new SessionResponseMsg(SessionStatusType.UNSUPPORTED_SESSION_CMD);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("Sending back response: {}", responseMessage);
      }
      responseMessage.send(repSocket);
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Opens the necessary connections for the SessionService. In particular, it creates and binds
   * the REP socket using the configured address.
   */
  @Override
  protected void openConnections() {
    repSocket = zmqContext.createSocket(SocketType.REP);
    Objects.requireNonNull(repAddress, "Session service endpoint not set");
    repSocket.bind(repAddress);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the established connections for the SessionService. This method closes the REP socket
   * and logs any errors encountered during the closure.
   */
  @Override
  protected void closeConnections() {
    closeConnection(repSocket, "Error closing REP socket");
  }
}
