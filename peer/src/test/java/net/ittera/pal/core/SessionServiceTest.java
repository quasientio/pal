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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.ittera.pal.common.objects.ConcurrentHashMapObjectLookupStore;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.messages.SessionCmdMsg;
import net.ittera.pal.core.messages.SessionReplyMsg;
import net.ittera.pal.messages.types.SessionCommandType;
import net.ittera.pal.messages.types.SessionStatusType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

public class SessionServiceTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String SESSION_SERVICE_ADDR = "inproc://session.svc";
  private UUID peerUuid;
  private SessionService sessionService;
  private ZContext context;
  private Socket socket;
  private ServiceManager manager;
  private final ObjectLookupStore objectLookupStore = new ConcurrentHashMapObjectLookupStore();
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");

  @Before
  public void setup() {
    peerUuid = UUID.randomUUID();
    context = createContext();
    sessionService =
        new SessionService(
            peerUuid,
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "Session_Service",
            SESSION_SERVICE_ADDR,
            objectLookupStore);
    final Set<Service> services = new HashSet<>(Collections.singletonList(sessionService));
    manager = new ServiceManager(services);
    // start service
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
    socket = context.createSocket(SocketType.REQ);
    socket.connect(SESSION_SERVICE_ADDR);
  }

  @Test
  public void sendStoreObjectCmd_objectExists_ok() {
    UUID sessionId = UUID.randomUUID();
    Object object = new HashMap<>();
    ObjectRef objectRef = objectLookupStore.storeObject(object);
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));
  }

  @Test
  public void sendDeleteSessionCmd_sessionDoesNotExist_noSuchSession() {
    UUID sessionId = UUID.randomUUID();
    SessionCmdMsg sessionCmdMsg = new SessionCmdMsg(SessionCommandType.DELETE_SESSION, sessionId);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  @Test
  public void sendDeleteSessionCmd_sessionExists_ok() {
    // store an object so the session is created
    UUID sessionId = UUID.randomUUID();
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));

    // now delete session
    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.DELETE_SESSION, sessionId);
    sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);

    // check status and deleted objectRefs
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));
    assertThat(sessionReplyMsg.getObjectRefs(), is(Collections.singleton(objectRef)));
  }

  @Test
  public void sendDeleteObjectCmd_objectInSession_ok() {
    UUID sessionId = UUID.randomUUID();
    // create and store an object
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));

    // now delete object from session
    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef);
    sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));
  }

  @Test
  public void sendDeleteObjectCmd_sessionDoesNotExist_noSuchSession() {
    UUID sessionId = UUID.randomUUID();
    // try to delete an object from non-existing session
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId, ObjectRef.from("597636"));
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  @Test
  public void sendDeleteObjectCmd_objectNotInSession_noSuchObject() {
    UUID sessionId = UUID.randomUUID();
    // store an object so the session is created
    ObjectRef objectRef = objectLookupStore.storeObject(new HashMap<>());
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));

    // now try to delete object which was not stored in session
    sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId, ObjectRef.from("597636"));
    sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.NO_SUCH_OBJECT));
  }

  @Test
  public void clearAllSessions() throws Exception {
    // store an object into sessionId1
    UUID sessionId1 = UUID.randomUUID();
    ObjectRef objectRef1 = objectLookupStore.storeObject(new HashMap<>());
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId1, objectRef1);
    sessionCmdMsg.send(socket);
    SessionReplyMsg.recvMsg(socket, true);

    // store an object into sessionId2
    UUID sessionId2 = UUID.randomUUID();
    ObjectRef objectRef2 = objectLookupStore.storeObject(new HashSet<>());
    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.STORE_OBJECT, sessionId2, objectRef2);
    sessionCmdMsg.send(socket);
    SessionReplyMsg.recvMsg(socket, true);

    // clear sessions
    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.CLEAR_SESSIONS);
    boolean sentOk = sessionCmdMsg.send(socket);
    assertTrue(sentOk);
    SessionReplyMsg sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.OK));

    // try to delete the stored objects
    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId1, objectRef1);
    sessionCmdMsg.send(socket);
    sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    sessionCmdMsg = new SessionCmdMsg(SessionCommandType.DELETE_OBJECT, sessionId2, objectRef2);
    sessionCmdMsg.send(socket);
    sessionReplyMsg = SessionReplyMsg.recvMsg(socket, true);
    assertThat(sessionReplyMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
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
