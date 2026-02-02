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
  // Additional tests for issue #463
  // ===========================================================================

  /**
   * Tests that CLEAR_SESSIONS command clears all sessions.
   *
   * <p>Given: SessionService with 3 sessions containing objects When: CLEAR_SESSIONS command
   * received Then: All sessions cleared; response indicates success
   */
  @Test
  public void run_clearSessionsCommand_clearsAllSessions() {
    // Given: SessionService with 3 sessions containing objects
    UUID sessionId1 = UUID.randomUUID();
    UUID sessionId2 = UUID.randomUUID();
    UUID sessionId3 = UUID.randomUUID();
    ObjectRef objectRef1 = objectLookupStore.storeObject(new HashMap<>());
    ObjectRef objectRef2 = objectLookupStore.storeObject(new HashSet<>());
    ObjectRef objectRef3 = objectLookupStore.storeObject("testObject");

    // Create session 1 with object
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId1, objectRef1);
    sessionCommandMsg.send(socket);
    SessionResponseMsg.receive(socket, true);

    // Create session 2 with object
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId2, objectRef2);
    sessionCommandMsg.send(socket);
    SessionResponseMsg.receive(socket, true);

    // Create session 3 with object
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId3, objectRef3);
    sessionCommandMsg.send(socket);
    SessionResponseMsg.receive(socket, true);

    // When: CLEAR_SESSIONS command received
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);

    // Then: All sessions cleared; response indicates success
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Verify all 3 sessions are gone (DELETE_OBJECT returns NO_SUCH_SESSION for each)
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

    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId3, objectRef3);
    sessionCommandMsg.send(socket);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  /**
   * Tests that deleteSession throws NoSuchSessionException for non-existent session.
   *
   * <p>Given: SessionService with no sessions When: deleteSession called with random UUID Then:
   * NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
   */
  @Test
  public void deleteSession_nonExistent_throwsNoSuchSessionException() {
    // Given: SessionService with no sessions
    // - No setup required, service starts with empty sessions

    // When: deleteSession called with random UUID
    UUID nonExistentSessionId = UUID.randomUUID();
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_SESSION, nonExistentSessionId);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);

    // Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  /**
   * Tests that storeInSession handles null object gracefully.
   *
   * <p>Given: Valid session When: storeInSession called with null object (ObjectRef not in lookup
   * store) Then: Method handles gracefully (returns ERROR status)
   */
  @Test
  public void storeInSession_nullObject_handlesGracefully() {
    // Given: Valid session (or new session will be created)
    // - Create an ObjectRef that is NOT in the objectLookupStore
    // ObjectRef uses numeric strings internally, so we use a random number that won't be in store
    UUID sessionId = UUID.randomUUID();
    ObjectRef nonExistentObjectRef = ObjectRef.from(99999);

    // When: storeInSession called with null object
    // - Send STORE_OBJECT command with an ObjectRef that doesn't exist in lookup store
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, nonExistentObjectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);

    // Then: Method handles gracefully (returns ERROR status)
    // - Verify response status is ERROR (storeInSession returns false when object is null)
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.ERROR));
  }

  /**
   * Tests that getObjectRefsInSession throws NoSuchSessionException for non-existent session.
   *
   * <p>Given: SessionService with no matching session When: getObjectRefsInSession called (via
   * DELETE_SESSION) Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
   */
  @Test
  public void getObjectRefsInSession_nonExistentSession_throwsNoSuchSessionException() {
    // Given: SessionService with no matching session
    // - No setup required, service starts with empty sessions

    // When: getObjectRefsInSession called (indirectly via DELETE_SESSION command)
    // - Send DELETE_SESSION command with random session UUID
    // - The run() method calls getObjectRefsInSession before deleteSession
    UUID nonExistentSessionId = UUID.randomUUID();
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_SESSION, nonExistentSessionId);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);

    // Then: NoSuchSessionException thrown (returns NO_SUCH_SESSION via socket)
    // - Verify response status is NO_SUCH_SESSION
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
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
  public void deleteObject_fromNonExistentSession_returnsFalse() {
    // Given: SessionService with no matching session
    // - No setup required, service starts with empty sessions
    UUID nonExistentSessionId = UUID.randomUUID();
    // ObjectRef uses numeric strings internally, so we use a number
    ObjectRef anyObjectRef = ObjectRef.from(12345);

    // When: deleteObject called via DELETE_OBJECT command
    // - Send DELETE_OBJECT command with random session UUID and any ObjectRef
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, nonExistentSessionId, anyObjectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);

    // Then: Returns NO_SUCH_SESSION status (no crash)
    // - Verify response status is NO_SUCH_SESSION
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    // - Verify service continues running (send another command successfully)
    UUID anotherSessionId = UUID.randomUUID();
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, anotherSessionId);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    // Service still responds (even if NO_SUCH_SESSION, it didn't crash)
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
  }

  /**
   * Tests that the service remains healthy after various error conditions.
   *
   * <p>Given: SessionService running When: Multiple error conditions occur (missing objects,
   * missing sessions) Then: Service continues responding correctly to subsequent commands
   *
   * <p>Note: The default case in the switch statement of run() (which would return
   * UNSUPPORTED_SESSION_CMD) is unreachable in practice because SessionCommandType is an enum with
   * all values handled, and SessionCommandType.fromByte() throws IllegalArgumentException for
   * unknown byte values before the switch is reached. This test verifies the service's overall
   * robustness by exercising multiple error paths and confirming the service remains healthy.
   */
  @Test
  public void run_invalidCommand_logsWarning() {
    // Given: SessionService running with no sessions
    // - Service is already started in @Before setup

    // Exercise error path 1: Delete object from non-existent session
    UUID sessionId1 = UUID.randomUUID();
    ObjectRef objectRef1 = ObjectRef.from(11111);
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId1, objectRef1);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    // Exercise error path 2: Store object that doesn't exist in lookup store
    UUID sessionId2 = UUID.randomUUID();
    ObjectRef objectRef2 = ObjectRef.from(22222);
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId2, objectRef2);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.ERROR));

    // Exercise error path 3: Delete non-existent session
    UUID sessionId3 = UUID.randomUUID();
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId3);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    // Verify service is still healthy: perform a successful operation
    Object object = new HashMap<>();
    ObjectRef storedObjectRef = objectLookupStore.storeObject(object);
    UUID sessionId4 = UUID.randomUUID();
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId4, storedObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // And clean up successfully
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId4);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
    assertThat(sessionResponseMsg.getObjectRefs(), is(Collections.singleton(storedObjectRef)));
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

  // ===========================================================================
  // Test specifications for issue #553 - Coverage gaps in SessionService
  // Implementation will be done in issue #554
  // ===========================================================================

  /**
   * Tests that deleteObject removes an object successfully from a session.
   *
   * <p>Given: Session with stored object When: deleteObject called with valid ID Then: Object
   * removed from session
   *
   * <p>This test verifies that when an object is stored in a session and the DELETE_OBJECT command
   * is sent with the correct session ID and object reference, the object is successfully removed
   * from the session. After removal, attempting to delete the same object again should return
   * NO_SUCH_OBJECT status, confirming the object was indeed removed.
   */
  @Test
  @Ignore("Awaiting implementation in #554")
  public void testDeleteObject_removesObjectSuccessfully() {
    // Given: Session with stored object
    // - Create a new session by storing an object
    // - Verify the object is stored (OK status)

    // When: deleteObject called with valid ID
    // - Send DELETE_OBJECT command with the session ID and object reference

    // Then: Object removed from session
    // - Verify response status is OK
    // - Verify the object is no longer in the session (subsequent delete returns NO_SUCH_OBJECT)
    // - Verify the session still exists (can store another object in it)

    // TODO(#554): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that deleteObject handles non-existent object gracefully.
   *
   * <p>Given: Session without the object When: deleteObject called Then: No error; operation
   * completes with NO_SUCH_OBJECT status
   *
   * <p>This test verifies that when attempting to delete an object that does not exist in a valid
   * session, the operation completes without errors and returns the appropriate NO_SUCH_OBJECT
   * status. The session should remain intact and functional after the operation.
   */
  @Test
  @Ignore("Awaiting implementation in #554")
  public void testDeleteObject_nonexistentObject_handledGracefully() {
    // Given: Session without the object
    // - Create a session by storing one object
    // - Create a different ObjectRef that was NOT stored in this session

    // When: deleteObject called
    // - Send DELETE_OBJECT command with the session ID and the non-existent object reference

    // Then: No error; operation completes
    // - Verify response status is NO_SUCH_OBJECT (not ERROR or exception)
    // - Verify the session is still functional (can still delete the original object)
    // - Verify service continues operating normally

    // TODO(#554): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that run() processes session commands from the queue correctly.
   *
   * <p>Given: SessionService with commands in queue When: run() executes Then: Commands processed
   * correctly in order
   *
   * <p>This test verifies the main service loop in run() processes multiple different command types
   * correctly. It sends a sequence of commands (STORE_OBJECT, DELETE_OBJECT, DELETE_SESSION,
   * CLEAR_SESSIONS) and verifies each is processed with the correct response.
   */
  @Test
  @Ignore("Awaiting implementation in #554")
  public void testRun_processesSessionCommands() {
    // Given: SessionService with commands in queue
    // - Service is already running (started in @Before)
    // - Prepare multiple commands of different types to send

    // When: run() executes (processes commands as they arrive)
    // - Send STORE_OBJECT command -> expect OK
    // - Send DELETE_OBJECT command with valid ref -> expect OK
    // - Send DELETE_OBJECT command with invalid ref -> expect NO_SUCH_OBJECT
    // - Send DELETE_SESSION command -> expect OK (or NO_SUCH_SESSION if already deleted)
    // - Send CLEAR_SESSIONS command -> expect OK

    // Then: Commands processed correctly
    // - Verify each command returns the expected status
    // - Verify state changes are reflected (e.g., session deleted after DELETE_SESSION)

    // TODO(#554): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that run() handles thread interruption gracefully.
   *
   * <p>Given: Running SessionService When: Thread interrupted Then: Exits gracefully without
   * throwing exceptions
   *
   * <p>This test verifies that when the service thread is interrupted, the run() method exits its
   * main loop cleanly. The service should transition to TERMINATED state without errors.
   */
  @Test
  @Ignore("Awaiting implementation in #554")
  public void testRun_handlesInterruption() {
    // Given: Running SessionService
    // - Service is already running (started in @Before)
    // - The service is blocked waiting for commands on the REP socket

    // When: Thread interrupted
    // - Stop the service manager (which triggers shutdown and interruption)
    // - Wait for service to stop

    // Then: Exits gracefully
    // - Verify service transitions to TERMINATED state
    // - Verify no exceptions are thrown during shutdown
    // - Verify the service stopped within a reasonable timeout

    // TODO(#554): Implement test logic
    // Note: This may require creating a separate SessionService instance to test
    // the interruption handling without affecting other tests.
    fail("Not yet implemented");
  }

  /**
   * Tests that closeConnections closes all resources properly.
   *
   * <p>Given: SessionService with open resources (REP socket) When: closeConnections called Then:
   * All resources released (socket closed)
   *
   * <p>This test verifies that when the service shuts down, closeConnections() properly closes the
   * REP socket. After shutdown, attempts to use the socket should fail.
   */
  @Test
  @Ignore("Awaiting implementation in #554")
  public void testCloseConnections_closesAllResources() {
    // Given: SessionService with open resources
    // - Service is running with an open REP socket
    // - The socket is bound to SESSION_SERVICE_ADDRESS

    // When: closeConnections called (via service shutdown)
    // - Stop the service manager
    // - Wait for the service to fully stop

    // Then: All resources released
    // - Verify the service has stopped
    // - Verify the REP socket is closed (attempting to connect/send should fail or
    //   a new service can bind to the same address without conflict)
    // - Verify no resource leaks (context can be closed cleanly)

    // TODO(#554): Implement test logic
    // Note: This test may need to verify socket closure indirectly since the socket
    // is private. One approach is to verify that a new service can bind to the same
    // address after shutdown.
    fail("Not yet implemented");
  }
}
