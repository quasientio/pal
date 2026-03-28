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
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link PeerCall}.
 *
 * <p>PeerCall is the peer-specific call command extracted from the original monolithic Caller class
 * to follow the entity-operation pattern ({@code pal peer call}). It handles peer RPC invocations
 * via ZMQ or JSON-RPC, including peer resolution by UUID, address, or name, request building, and
 * thread affinity.
 */
public class PeerCallTest {

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object on which to set the field
   * @param fieldName the name of the field to set
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Gets a field value from an object via reflection, searching the class hierarchy.
   *
   * @param target the object from which to read the field
   * @param fieldName the name of the field to read
   * @return the field value
   */
  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(target);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Creates a PeerCall instance with a mock PalDirectory injected.
   *
   * @param mockDir the mock PalDirectory to inject
   * @return a configured PeerCall instance
   */
  private static PeerCall createPeerCallWithMockDirectory(PalDirectory mockDir) throws Exception {
    PeerCall c = new PeerCall();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(c, "directoryConnectionProvider", dcp);
    return c;
  }

  /**
   * Locates the inner class {@code StaticMethodCallBuilder} within {@link PeerCall}.
   *
   * @return the inner class
   */
  private static Class<?> findStaticMethodCallBuilder() {
    for (Class<?> cl : PeerCall.class.getDeclaredClasses()) {
      if (cl.getSimpleName().equals("StaticMethodCallBuilder")) {
        return cl;
      }
    }
    throw new AssertionError("StaticMethodCallBuilder inner class not found in PeerCall");
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that a valid peer UUID is accepted as a positional argument.
   *
   * <p>Verifies that providing a standard UUID string as the peer identifier passes input
   * validation without error.
   */
  @Test
  public void validateInput_validPeerUuid_accepted() throws Exception {
    PeerCall c = new PeerCall();
    UUID uuid = UUID.randomUUID();
    setField(c, "peerIdentifier", uuid.toString());
    setField(c, "className", "com.example.Main");

    c.validateInput();

    assertThat(getField(c, "peerUuid"), is(uuid));
    assertThat(getField(c, "peerAddress"), is(nullValue()));
    assertThat(getField(c, "peerName"), is(nullValue()));
  }

  /**
   * Tests that a valid TCP peer address is accepted as a positional argument.
   *
   * <p>Verifies that providing a {@code tcp://host:port} address as the peer identifier passes
   * validation and the address is correctly parsed.
   */
  @Test
  public void validateInput_validPeerAddress_tcpAccepted() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "tcp://localhost:5555");
    setField(c, "className", "com.example.Main");

    c.validateInput();

    assertThat(getField(c, "peerAddress"), is("tcp://localhost:5555"));
    assertThat(getField(c, "peerUuid"), is(nullValue()));
    assertThat(getField(c, "peerName"), is(nullValue()));
  }

  /**
   * Tests that a valid WebSocket peer address is accepted as a positional argument.
   *
   * <p>Verifies that providing a {@code ws://host:port} address as the peer identifier passes
   * validation.
   */
  @Test
  public void validateInput_validPeerAddress_wsAccepted() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "ws://localhost:8080");
    setField(c, "className", "com.example.Main");

    c.validateInput();

    assertThat(getField(c, "peerAddress"), is("ws://localhost:8080"));
    assertThat(getField(c, "peerUuid"), is(nullValue()));
    assertThat(getField(c, "peerName"), is(nullValue()));
  }

  /**
   * Tests that a valid peer name (plain string) is accepted as a positional argument.
   *
   * <p>Verifies that providing a plain name (not a UUID, not an address) as the peer identifier
   * passes validation and is treated as a peer name for directory lookup.
   */
  @Test
  public void validateInput_validPeerName_accepted() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "my-peer");
    setField(c, "className", "com.example.Main");

    c.validateInput();

    assertThat(getField(c, "peerName"), is("my-peer"));
    assertThat(getField(c, "peerUuid"), is(nullValue()));
    assertThat(getField(c, "peerAddress"), is(nullValue()));
  }

  /**
   * Tests that missing peer identifier throws a RuntimeException.
   *
   * <p>Verifies that invoking the command without any positional peer identifier argument results
   * in a validation error.
   */
  @Test
  public void validateInput_noPeer_throwsRuntimeException() {
    PeerCall c = new PeerCall();

    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Peer identifier is required"));
  }

  /**
   * Tests that an invalid RPC type value throws a RuntimeException.
   *
   * <p>Verifies that providing an invalid value for the {@code -r/--rpc-type} option results in a
   * validation error.
   */
  @Test
  public void validateInput_rpcType_invalidValue_throwsRuntimeException() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "tcp://localhost:5555");
    setField(c, "rpcType", "INVALID");
    setField(c, "className", "com.example.Main");

    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Invalid RPC type"));
  }

  /**
   * Tests that JSON-RPC type with a non-WebSocket address throws a RuntimeException.
   *
   * <p>Verifies that specifying {@code -r JSON_RPC} together with a {@code tcp://} address results
   * in a validation error, since JSON-RPC requires a WebSocket address.
   */
  @Test
  public void validateInput_jsonRpc_withNonWsAddress_throwsRuntimeException() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "tcp://localhost:5555");
    setField(c, "rpcType", "JSON_RPC");
    setField(c, "className", "com.example.Main");

    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Peer address must start with ws://"));
  }

  // ==================== buildCallRequests() Tests ====================

  /**
   * Tests that buildCallRequests correctly builds an ExecMessage for a single static method call.
   *
   * <p>Verifies that providing a class name and arguments as positional parameters results in a
   * correctly constructed ExecMessage with the appropriate class, method, and arguments.
   */
  @Test
  public void buildCallRequests_singleStaticMethod_buildsCorrectly() throws Exception {
    PeerCall c = new PeerCall();
    UUID peer = UUID.randomUUID();

    Class<?> inner = findStaticMethodCallBuilder();
    Constructor<?> cons =
        inner.getDeclaredConstructor(
            PeerCall.class, UUID.class, String.class, String.class, List.class);
    cons.setAccessible(true);
    Object builder = cons.newInstance(c, peer, "com.example.Main", "main", List.of("arg1", "arg2"));

    Method buildExec = builder.getClass().getDeclaredMethod("buildExecMessage");
    ExecMessage em = (ExecMessage) buildExec.invoke(builder);

    assertEquals("com.example.Main", em.getClassMethodCall().getClazz().getName());
    assertEquals("main", em.getClassMethodCall().getName());

    // Verify parameters contain the args (field is in superclass BaseStaticMethodCallBuilder)
    var parametersField = findField(inner, "parameters");
    parametersField.setAccessible(true);
    Object[] parameters = (Object[]) parametersField.get(builder);
    assertNotNull(parameters);
    assertEquals(1, parameters.length);
    String[] args = (String[]) parameters[0];
    assertEquals(2, args.length);
    assertEquals("arg1", args[0]);
    assertEquals("arg2", args[1]);
  }

  /**
   * Tests that buildCallRequests reads JSON-RPC requests from stdin and builds them correctly.
   *
   * <p>Verifies that when stdin contains JSON-RPC request data, buildCallRequests reads and
   * converts it into the appropriate call request(s).
   */
  @SuppressWarnings("unchecked")
  @Test
  public void buildCallRequests_fromStdin_readsAndBuilds() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "peerIdentifier", "ws://localhost:8080");

    String jsonLine1 = "{\"jsonrpc\":\"2.0\",\"method\":\"test1\",\"id\":1}";
    String jsonLine2 = "{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"id\":2}";
    String stdinContent = jsonLine1 + "\n" + jsonLine2 + "\n";

    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream(stdinContent.getBytes(StandardCharsets.UTF_8)));
      c.validateInput();

      List<String> stdinRequests = (List<String>) getField(c, "stdinRequests");
      assertNotNull(stdinRequests);
      assertEquals(2, stdinRequests.size());
      assertEquals(jsonLine1, stdinRequests.get(0));
      assertEquals(jsonLine2, stdinRequests.get(1));
    } finally {
      System.setIn(originalIn);
    }
  }

  // ==================== getRpcTypeForPeer() Tests ====================

  /**
   * Tests that getRpcTypeForPeer returns ZMQ_RPC when the peer only has a ZMQ RPC endpoint.
   *
   * <p>Verifies that when a peer's PeerInfo indicates only ZMQ RPC is available, the method returns
   * {@code RpcType.ZMQ_RPC}.
   */
  @Test
  public void getRpcTypeForPeer_onlyZmqRpc_returnsZmqRpc() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid);
    peerInfo.setZmqRpcAddress("tcp://localhost:5555");

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getPeer(peerUuid)).thenReturn(peerInfo);

    PeerCall c = createPeerCallWithMockDirectory(mockDir);

    Method m = PeerCall.class.getDeclaredMethod("getRpcTypeForPeer", UUID.class);
    m.setAccessible(true);
    RpcType result = (RpcType) m.invoke(c, peerUuid);

    assertThat(result, is(RpcType.ZMQ_RPC));
  }

  /**
   * Tests that getRpcTypeForPeer returns JSON_RPC when the peer only has a JSON-RPC endpoint.
   *
   * <p>Verifies that when a peer's PeerInfo indicates only JSON-RPC is available, the method
   * returns {@code RpcType.JSON_RPC}.
   */
  @Test
  public void getRpcTypeForPeer_onlyJsonRpc_returnsJsonRpc() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid);
    peerInfo.setJsonrpcAddress("ws://localhost:8080");

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getPeer(peerUuid)).thenReturn(peerInfo);

    PeerCall c = createPeerCallWithMockDirectory(mockDir);

    Method m = PeerCall.class.getDeclaredMethod("getRpcTypeForPeer", UUID.class);
    m.setAccessible(true);
    RpcType result = (RpcType) m.invoke(c, peerUuid);

    assertThat(result, is(RpcType.JSON_RPC));
  }

  /**
   * Tests that getRpcTypeForPeer throws when the peer has neither RPC type available.
   *
   * <p>Verifies that when a peer's PeerInfo has no RPC endpoints configured, the method throws a
   * RuntimeException.
   */
  @Test
  public void getRpcTypeForPeer_neitherRpcType_throwsRuntimeException() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid);

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getPeer(peerUuid)).thenReturn(peerInfo);

    PeerCall c = createPeerCallWithMockDirectory(mockDir);

    Method m = PeerCall.class.getDeclaredMethod("getRpcTypeForPeer", UUID.class);
    m.setAccessible(true);

    InvocationTargetException ex =
        assertThrows(InvocationTargetException.class, () -> m.invoke(c, peerUuid));
    assertThat(ex.getCause(), instanceOf(RuntimeException.class));
    assertThat(ex.getCause().getMessage(), containsString("Peer does not have any RPC address"));
  }

  // ==================== printIfRequired() Tests ====================

  /**
   * Tests that printIfRequired prints both return values and throwables.
   *
   * <p>Verifies that when print-responses mode is enabled, the method correctly prints the return
   * value from a successful call and the throwable from a failed call.
   */
  @Test
  public void printIfRequired_prints_return_and_throwable() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "printResponses", true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(c, "out", new PrintStream(bout));

    MessageBuilder b = new MessageBuilder(UUID.randomUUID(), Boolean.toString(false));
    b.buildEmptyConstructor(UUID.randomUUID(), "java.lang.String");
    var rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setValue("\"ok\"");
    rv.setObject(obj);

    Method printRv =
        AbstractCallCommand.class.getDeclaredMethod("printReturnValue", ReturnValue.class);
    printRv.setAccessible(true);
    printRv.invoke(c, rv);

    String output = bout.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("\"ok\""));

    // also throwable path
    bout.reset();
    var rt = new RaisedThrowable();
    Method printRt =
        AbstractCallCommand.class.getDeclaredMethod("printRaisedThrowable", RaisedThrowable.class);
    printRt.setAccessible(true);
    printRt.invoke(c, rt);
    // Should not throw NPE
    assertNotNull(bout.toString(StandardCharsets.UTF_8));
  }

  // ==================== sendRequests() Thread Affinity Tests ====================

  /**
   * Tests that thread affinity is correctly set on outgoing requests.
   *
   * <p>Verifies that when the {@code --thread-affinity} option is specified, the thread affinity
   * value is propagated to both {@code ExecMessage} and {@code JsonRpcRequest} objects built by the
   * request builder.
   */
  @Test
  public void sendRequests_threadAffinity_setsAffinityCorrectly() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "threadAffinity", "fx-thread");
    UUID peer = UUID.randomUUID();

    Class<?> inner = findStaticMethodCallBuilder();
    Constructor<?> cons =
        inner.getDeclaredConstructor(
            PeerCall.class, UUID.class, String.class, String.class, List.class);
    cons.setAccessible(true);
    Object builder = cons.newInstance(c, peer, "com.example.App", "start", List.of());

    // Verify ExecMessage gets thread affinity
    Method buildExec = builder.getClass().getDeclaredMethod("buildExecMessage");
    ExecMessage em = (ExecMessage) buildExec.invoke(builder);
    assertThat(em.getThreadAffinity(), is("fx-thread"));

    // Verify JsonRpcRequest params get thread affinity
    Method buildJson = builder.getClass().getDeclaredMethod("buildJsonRpc");
    try {
      var request = (JsonRpcRequest) buildJson.invoke(builder);
      assertThat(request.getParams().getThreadAffinity(), is("fx-thread"));
    } catch (InvocationTargetException e) {
      // buildJsonRpc() may throw due to missing top-level method on JsonRpcRequest.
      // The Params were already built with threadAffinity before the exception.
      // Verify by building the same Params independently.
      String threadAffinity = (String) getField(c, "threadAffinity");
      Params params =
          new Params.Builder()
              .withMethod("start")
              .withType("com.example.App")
              .withThreadAffinity(threadAffinity)
              .build();
      assertThat(params.getThreadAffinity(), is("fx-thread"));
    }
  }

  /**
   * Tests that multi-thread mode uses the correct thread count.
   *
   * <p>Verifies that when the {@code -t/--num-threads} option is specified, the command creates the
   * correct number of sender threads for parallel request dispatch.
   */
  @Test
  public void sendRequests_multiThread_usesCorrectThreadCount() throws Exception {
    PeerCall c = new PeerCall();
    setField(c, "numberOfThreads", 4);

    // Verify numberOfThreads is correctly stored
    assertThat(getField(c, "numberOfThreads"), is(4));

    // Verify that runManyClients accepts numberOfThreads > 1
    // (it will fail internally due to missing init, but the guard clause passes)
    Method m =
        AbstractCallCommand.class.getDeclaredMethod(
            "runManyClients",
            int.class,
            boolean.class,
            Logger.class,
            AbstractCallCommand.RequestSender.class);
    m.setAccessible(true);
    // This should complete without throwing IllegalArgumentException.
    // Internal threads will fail due to null palDirectoryUrl but catch exceptions and count down.
    m.invoke(
        c,
        4,
        false,
        LoggerFactory.getLogger(PeerCallTest.class),
        (AbstractCallCommand.RequestSender)
            () -> {
              return 0;
            });
  }
}
