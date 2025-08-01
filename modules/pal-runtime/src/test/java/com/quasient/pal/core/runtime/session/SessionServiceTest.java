/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.runtime.session;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.internal.messages.SessionCommandMsg;
import com.quasient.pal.core.internal.messages.SessionResponseMsg;
import com.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import com.quasient.pal.core.runtime.objects.ObjectLookupStore;
import com.quasient.pal.messages.types.SessionCommandType;
import com.quasient.pal.messages.types.SessionStatusType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class SessionServiceTest extends ZmqEnabledTest {
  private static final String SESSION_SERVICE_ADDRESS = "inproc://session.svc";
  private ZContext context;
  private Socket socket;
  private ServiceManager manager;
  private final ObjectLookupStore objectLookupStore =
      ConcurrentHashMapObjectLookupStore.createWithScheduledCleaner();
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");

  @Before
  public void setup() {
    UUID peerUuid = UUID.randomUUID();
    context = createContext();
    SessionService sessionService =
        new SessionService(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "Session_Service",
            SESSION_SERVICE_ADDRESS,
            objectLookupStore);
    final Set<Service> services = new HashSet<>(Collections.singletonList(sessionService));
    manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
    socket = context.createSocket(SocketType.REQ);
    socket.connect(SESSION_SERVICE_ADDRESS);
  }

  @Test
  public void sendStoreObjectCmd_objectExists_ok() {
    UUID sessionId = UUID.randomUUID();
    Object object = new HashMap<>();
    ObjectRef objectRef = objectLookupStore.storeObject(object);
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
  }

  @Test
  public void sendDeleteSessionCmd_sessionDoesNotExist_noSuchSession() {
    UUID sessionId = UUID.randomUUID();
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  @Test
  public void sendDeleteSessionCmd_sessionExists_ok() {
    // store an object so the session is created
    UUID sessionId = UUID.randomUUID();
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // now delete session
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);

    // check status and deleted objectRefs
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
    assertThat(sessionResponseMsg.getObjectRefs(), is(Collections.singleton(objectRef)));
  }

  @Test
  public void sendDeleteObjectCmd_objectInSession_ok() {
    UUID sessionId = UUID.randomUUID();
    // create and store an object
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // now delete object from session
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
  }

  @Test
  public void sendDeleteObjectCmd_sessionDoesNotExist_noSuchSession() {
    UUID sessionId = UUID.randomUUID();
    // try to delete an object from non-existing session
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(
            SessionCommandType.DELETE_OBJECT, sessionId, ObjectRef.from("597636"));
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  @Test
  public void sendDeleteObjectCmd_objectNotInSession_noSuchObject() {
    UUID sessionId = UUID.randomUUID();
    // store an object so the session is created
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // now try to delete object which was not stored in session
    sessionCommandMsg =
        new SessionCommandMsg(
            SessionCommandType.DELETE_OBJECT, sessionId, ObjectRef.from("597636"));
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_OBJECT));
  }

  @Test
  public void clearAllSessions() {
    // store an object into sessionId1
    UUID sessionId1 = UUID.randomUUID();
    ObjectRef objectRef1 = objectLookupStore.storeObject(new HashMap<>());
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId1, objectRef1);
    sessionCommandMsg.send(socket);
    SessionResponseMsg.receive(socket, true);

    // store an object into sessionId2
    UUID sessionId2 = UUID.randomUUID();
    ObjectRef objectRef2 = objectLookupStore.storeObject(new HashSet<>());
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId2, objectRef2);
    sessionCommandMsg.send(socket);
    SessionResponseMsg.receive(socket, true);

    // clear sessions
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // try to delete the stored objects
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId1, objectRef1);
    sessionCommandMsg.send(socket);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId2, objectRef2);
    sessionCommandMsg.send(socket);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  @After
  public void cleanup() throws Exception {
    // shut down services
    manager.stopAsync();

    // close sockets
    socket.close();

    // close zmq context
    closeContext(context);
  }
}
