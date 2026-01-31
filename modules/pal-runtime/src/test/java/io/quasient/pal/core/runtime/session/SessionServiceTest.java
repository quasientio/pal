/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.runtime.session;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.SessionCommandMsg;
import io.quasient.pal.core.internal.messages.SessionResponseMsg;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.runtime.objects.ObjectLookupStore;
import io.quasient.pal.messages.types.SessionCommandType;
import io.quasient.pal.messages.types.SessionStatusType;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
      ConcurrentHashMapObjectLookupStore.createAsyncManaged();
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

  // ===========================================================================
  // Test specifications for issue #462 - Awaiting implementation in #463
  // ===========================================================================

  /**
   * Tests that CLEAR_SESSIONS command clears all sessions.
   *
   * <p>Given: SessionService with 3 sessions containing objects When: CLEAR_SESSIONS command
   * received Then: All sessions cleared; response indicates success
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void run_clearSessionsCommand_clearsAllSessions() {
    // Given: SessionService with 3 sessions containing objects
    // - Create session 1 with object
    // - Create session 2 with object
    // - Create session 3 with object

    // When: CLEAR_SESSIONS command received
    // - Send CLEAR_SESSIONS command via socket

    // Then: All sessions cleared; response indicates success
    // - Verify response status is OK
    // - Verify all 3 sessions are gone (DELETE_OBJECT returns NO_SUCH_SESSION for each)

    // TODO(#463): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that deleteSession throws NoSuchSessionException for non-existent session.
   *
   * <p>Given: SessionService with no sessions When: deleteSession called with random UUID Then:
   * NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void deleteSession_nonExistent_throwsNoSuchSessionException() {
    // Given: SessionService with no sessions
    // - No setup required, service starts with empty sessions

    // When: deleteSession called with random UUID
    // - Send DELETE_SESSION command with random session UUID

    // Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
    // - Verify response status is NO_SUCH_SESSION

    // TODO(#463): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that storeInSession handles null object gracefully.
   *
   * <p>Given: Valid session When: storeInSession called with null object (ObjectRef not in lookup
   * store) Then: Method handles gracefully (returns ERROR status)
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void storeInSession_nullObject_handlesGracefully() {
    // Given: Valid session (or new session will be created)
    // - Create an ObjectRef that is NOT in the objectLookupStore

    // When: storeInSession called with null object
    // - Send STORE_OBJECT command with an ObjectRef that doesn't exist in lookup store

    // Then: Method handles gracefully (returns ERROR status)
    // - Verify response status is ERROR (storeInSession returns false when object is null)

    // TODO(#463): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that getObjectRefsInSession throws NoSuchSessionException for non-existent session.
   *
   * <p>Given: SessionService with no matching session When: getObjectRefsInSession called (via
   * DELETE_SESSION) Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void getObjectRefsInSession_nonExistentSession_throwsNoSuchSessionException() {
    // Given: SessionService with no matching session
    // - No setup required, service starts with empty sessions

    // When: getObjectRefsInSession called (indirectly via DELETE_SESSION command)
    // - Send DELETE_SESSION command with random session UUID
    // - The run() method calls getObjectRefsInSession before deleteSession

    // Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
    // - Verify response status is NO_SUCH_SESSION

    // TODO(#463): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that deleteObject from non-existent session returns appropriate status.
   *
   * <p>Given: SessionService with no matching session When: deleteObject called Then: Returns
   * NO_SUCH_SESSION status (no crash)
   *
   * <p>Note: The private deleteObject method throws NoSuchSessionException, which is caught in
   * run() and converted to NO_SUCH_SESSION status.
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void deleteObject_fromNonExistentSession_returnsFalse() {
    // Given: SessionService with no matching session
    // - No setup required, service starts with empty sessions

    // When: deleteObject called via DELETE_OBJECT command
    // - Send DELETE_OBJECT command with random session UUID and any ObjectRef

    // Then: Returns NO_SUCH_SESSION status (no crash)
    // - Verify response status is NO_SUCH_SESSION
    // - Verify service continues running (send another command successfully)

    // TODO(#463): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that invalid/unknown command type results in appropriate warning.
   *
   * <p>Given: SessionService running When: Unknown command type received Then: Warning logged;
   * UNSUPPORTED_SESSION_CMD status returned; no crash
   *
   * <p>Note: This tests the default case in the switch statement of run(). Since SessionCommandType
   * is an enum with fixed values, we cannot send an unknown value via the normal API. This test
   * verifies the default branch exists and handles gracefully. Implementation may require mocking
   * or special test infrastructure.
   */
  @Test
  @Ignore("Awaiting implementation in #463")
  public void run_invalidCommand_logsWarning() {
    // Given: SessionService running
    // - Service is already started in @Before setup

    // When: Unknown command type received
    // - This is challenging as SessionCommandType is an enum
    // - May need to: (a) use reflection to inject invalid command, or
    //                (b) verify via integration with custom message, or
    //                (c) document that this path is unreachable in practice

    // Then: Warning logged; UNSUPPORTED_SESSION_CMD status returned; no crash
    // - If we can send invalid command: verify response is UNSUPPORTED_SESSION_CMD
    // - Verify service continues to respond to valid commands

    // TODO(#463): Implement test logic - may require special test approach
    fail("Not yet implemented");
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
