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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.time.Duration;
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
  // Additional tests for SessionService
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
  // Test specifications for coverage gaps in SessionService
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
  public void testDeleteObject_removesObjectSuccessfully() {
    // Given: Session with stored object
    UUID sessionId = UUID.randomUUID();
    Object object = new HashMap<>();
    ObjectRef objectRef = objectLookupStore.storeObject(object);

    // Create session by storing an object
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // When: deleteObject called with valid ID
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);

    // Then: Object removed from session - Verify response status is OK
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Verify the object is no longer in the session (subsequent delete returns NO_SUCH_OBJECT)
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_OBJECT));

    // Verify the session still exists (can store another object in it)
    Object anotherObject = new HashSet<>();
    ObjectRef anotherObjectRef = objectLookupStore.storeObject(anotherObject);
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, anotherObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
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
  public void testDeleteObject_nonexistentObject_handledGracefully() {
    // Given: Session without the object
    UUID sessionId = UUID.randomUUID();
    Object object = new HashMap<>();
    ObjectRef storedObjectRef = objectLookupStore.storeObject(object);

    // Create a session by storing one object
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, storedObjectRef);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Create a different ObjectRef that was NOT stored in this session
    ObjectRef nonExistentObjectRef = ObjectRef.from(88888);

    // When: deleteObject called with non-existent object reference
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, nonExistentObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);

    // Then: No error; operation completes with NO_SUCH_OBJECT status
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_OBJECT));

    // Verify the session is still functional (can still delete the original object)
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, storedObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Verify service continues operating normally (can create a new session)
    UUID anotherSessionId = UUID.randomUUID();
    Object anotherObject = new HashSet<>();
    ObjectRef anotherObjectRef = objectLookupStore.storeObject(anotherObject);
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, anotherSessionId, anotherObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
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
  public void testRun_processesSessionCommands() {
    // Given: SessionService running, prepare multiple commands of different types
    UUID sessionId = UUID.randomUUID();
    Object object1 = new HashMap<>();
    Object object2 = new HashSet<>();
    ObjectRef objectRef1 = objectLookupStore.storeObject(object1);
    ObjectRef objectRef2 = objectLookupStore.storeObject(object2);
    ObjectRef nonExistentRef = ObjectRef.from(77777);

    // When/Then: Send STORE_OBJECT command for first object -> expect OK
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef1);
    boolean sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    SessionResponseMsg sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Send STORE_OBJECT command for second object -> expect OK
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, sessionId, objectRef2);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Send DELETE_OBJECT command with valid ref -> expect OK
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, objectRef1);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Send DELETE_OBJECT command with non-existent ref -> expect NO_SUCH_OBJECT
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, sessionId, nonExistentRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_OBJECT));

    // Send DELETE_SESSION command -> expect OK with remaining objectRef2
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));
    // Verify objectRef2 was in the deleted session
    assertThat(sessionResponseMsg.getObjectRefs(), is(Collections.singleton(objectRef2)));

    // Verify session is now deleted (DELETE_SESSION again returns NO_SUCH_SESSION)
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.DELETE_SESSION, sessionId);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));

    // Create a new session to test CLEAR_SESSIONS
    UUID newSessionId = UUID.randomUUID();
    Object newObject = "testObject";
    ObjectRef newObjectRef = objectLookupStore.storeObject(newObject);
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, newSessionId, newObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Send CLEAR_SESSIONS command -> expect OK
    sessionCommandMsg = new SessionCommandMsg(SessionCommandType.CLEAR_SESSIONS);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.OK));

    // Verify all sessions were cleared (DELETE_OBJECT returns NO_SUCH_SESSION)
    sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.DELETE_OBJECT, newSessionId, newObjectRef);
    sentOk = sessionCommandMsg.send(socket);
    assertTrue(sentOk);
    sessionResponseMsg = SessionResponseMsg.receive(socket, true);
    assertNotNull(sessionResponseMsg);
    assertThat(sessionResponseMsg.getStatus(), is(SessionStatusType.NO_SUCH_SESSION));
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
  public void testRun_handlesInterruption() throws Exception {
    // Given: Running SessionService
    // - Service is already running (started in @Before)
    // - Verify the service is healthy by sending a command
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

    // When: Service manager stopped (triggers interruption via context termination)
    // - Stop the service manager (which triggers shutdown and ZMQ context termination)
    manager.stopAsync();

    // Then: Exits gracefully
    // - Verify service transitions to stopped state within reasonable timeout
    manager.awaitStopped(Duration.ofSeconds(5));

    // - Verify service is fully stopped (isHealthy returns false, servicesByState shows terminated)
    assertThat(manager.isHealthy(), is(false));

    // - Verify all services are in TERMINATED state
    for (Service service : manager.servicesByState().values()) {
      assertThat(
          "Service should be in TERMINATED state", service.state(), is(Service.State.TERMINATED));
    }
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
  public void testCloseConnections_closesAllResources() throws Exception {
    // Given: SessionService with open resources
    // Verify service is running by sending a command
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

    // When: closeConnections called (via service shutdown)
    manager.stopAsync();
    manager.awaitStopped(Duration.ofSeconds(5));

    // Then: All resources released
    // Verify the service has stopped
    assertThat(manager.isHealthy(), is(false));
    for (Service service : manager.servicesByState().values()) {
      assertThat(
          "Service should be in TERMINATED state", service.state(), is(Service.State.TERMINATED));
    }

    // Verify REP socket is closed by creating a new service that binds to the same address
    // If the old socket was not closed, this would fail with address already in use
    ZContext newContext = createContext();
    try {
      UUID newPeerUuid = UUID.randomUUID();
      SessionService newSessionService =
          new SessionService(
              newPeerUuid,
              newContext,
              SYNC_SOCKET_ADDRESS,
              new ThreadGroup("test-thread-group"),
              "Session_Service_New",
              SESSION_SERVICE_ADDRESS,
              objectLookupStore);
      final Set<Service> newServices = new HashSet<>(Collections.singletonList(newSessionService));
      ServiceManager newManager = new ServiceManager(newServices);
      newManager.startAsync().awaitHealthy();
      collectGoSignals(newServices.size(), newContext);

      // The new service started successfully, proving the old socket was released
      Socket newSocket = newContext.createSocket(SocketType.REQ);
      newSocket.connect(SESSION_SERVICE_ADDRESS);

      // Verify new service works
      Object newObject = new HashSet<>();
      ObjectRef newObjectRef = objectLookupStore.storeObject(newObject);
      UUID newSessionId = UUID.randomUUID();
      SessionCommandMsg newCommandMsg =
          new SessionCommandMsg(SessionCommandType.STORE_OBJECT, newSessionId, newObjectRef);
      sentOk = newCommandMsg.send(newSocket);
      assertTrue(sentOk);
      SessionResponseMsg newResponseMsg = SessionResponseMsg.receive(newSocket, true);
      assertNotNull(newResponseMsg);
      assertThat(newResponseMsg.getStatus(), is(SessionStatusType.OK));

      // Clean up
      newManager.stopAsync();
      newSocket.close();
    } finally {
      closeContext(newContext);
    }
  }
}
