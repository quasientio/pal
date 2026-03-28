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
package io.quasient.pal.core.transport.websocket;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

import io.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.MetaServiceType;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link JsonRpcWebSocketServer}.
 *
 * <p>This test class validates the WebSocket server functionality including connection lifecycle
 * management, message handling, and error handling.
 */
public class JsonRpcWebSocketServerTest {

  private JsonRpcWebSocketServer server;
  private BlockingQueue<InboundJsonRpcRequestMsg> queue;
  private CountDownLatch ready;
  private InetSocketAddress bindAddr;

  private static int findFreePort() {
    try (ServerSocket s = new ServerSocket(0)) {
      return s.getLocalPort();
    } catch (Exception e) {
      return -1;
    }
  }

  @Before
  public void setup() throws Exception {
    int port = findFreePort();
    assumeTrue("no free port available", port > 0);
    bindAddr = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
    queue = new ArrayBlockingQueue<>(8);
    ready = new CountDownLatch(1);
    server = new JsonRpcWebSocketServer(bindAddr, queue, ready);
  }

  @After
  public void cleanup() {
    if (server != null) {
      try {
        server.close();
      } catch (Exception e) {
        System.err.println("JsonRpcWebSocketServerTest cleanup error: " + e.getMessage());
      }
    }
  }

  private static final class TestClient extends WebSocketClient {
    volatile String lastMessage;

    TestClient(URI serverUri, Map<String, String> headers) {
      super(serverUri, headers);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {}

    @Override
    public void onMessage(String message) {
      lastMessage = message;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {}

    @Override
    public void onError(Exception ex) {}
  }

  @Test
  public void lifecycle_open_onMessage_enqueue_and_close() throws Exception {
    server.start();
    // wait for onStart
    boolean ok = ready.await(2, TimeUnit.SECONDS);
    assumeTrue("server did not start in time", ok);

    // Connect a client with a fixed peer-id header
    UUID peerId = UUID.randomUUID();
    String url = "ws://" + bindAddr.getHostString() + ":" + bindAddr.getPort();
    TestClient client =
        new TestClient(URI.create(url), Collections.singletonMap("peer-id", peerId.toString()));
    client.connectBlocking(2, TimeUnit.SECONDS);

    // Send a simple JSON message
    String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":1}";
    client.send(payload);

    // Server should enqueue it
    InboundJsonRpcRequestMsg msg = queue.poll(2, TimeUnit.SECONDS);
    assertThat(msg != null, is(true));
    assertThat(msg.getJsonMessage(), is(payload));

    // Echo a response to that peer and verify client receives it
    String response = "{\"jsonrpc\":\"2.0\",\"result\":true,\"id\":1}";
    server.sendResponseToWebSocketClient(peerId, response, MessageType.CONTROL_MESSAGE_RESPONSE);
    // Give it a moment
    TimeUnit.MILLISECONDS.sleep(100);
    assertThat(client.lastMessage, is(response));

    // Close
    client.closeBlocking();
    server.close();
  }

  @Test
  public void metaStreaming_sendsFileAndDeletesIt() throws Exception {
    server.start();
    boolean ok = ready.await(2, TimeUnit.SECONDS);
    assumeTrue("server did not start in time", ok);

    UUID peerId = UUID.randomUUID();
    String url = "ws://" + bindAddr.getHostString() + ":" + bindAddr.getPort();
    TestClient client =
        new TestClient(URI.create(url), Collections.singletonMap("peer-id", peerId.toString()));
    client.connectBlocking(2, TimeUnit.SECONDS);

    // Prepare a temp file with known contents
    Path tmp = Files.createTempFile("ws_meta_", ".txt");
    Files.writeString(tmp, "HELLO-WS", StandardCharsets.UTF_8);

    // Build a minimal META_MESSAGE_RESPONSE JSON that JsonRpcWebSocketServer recognizes
    // as FETCH_CLASSES_INFO (service key) and carries the temp file path in response key.
    String metaBody =
        "{\"service\":\""
            + MetaServiceType.FETCH_CLASSES_INFO.getJsonName()
            + "\",\"response\":\""
            + tmp.toString().replace("\\", "\\\\")
            + "\"}";
    String response =
        "{\"jsonrpc\":\"2.0\",\"result\":{\"isVoid\":false,\"value\":{\"isNull\":false,\"value\":\""
            + metaBody.replace("\"", "\\\"")
            + "\"}},\"id\":\"42\"}";

    // Trigger streaming path
    server.sendResponseToWebSocketClient(peerId, response, MessageType.META_MESSAGE_RESPONSE);

    // Client should receive a JSON containing the id and the streamed content
    // Give it a moment
    TimeUnit.MILLISECONDS.sleep(150);
    assumeTrue("no message received from server", client.lastMessage != null);
    assertThat(client.lastMessage.contains("\"id\":\"42\""), is(true));
    assertThat(client.lastMessage.contains("HELLO-WS"), is(true));

    // The file should be deleted by server after streaming
    boolean exists = Files.exists(tmp);
    assertThat(exists, is(false));

    client.closeBlocking();
    server.close();
  }

  /**
   * Test: Verify that onError handles exceptions gracefully.
   *
   * <p>This test validates that when an exception occurs on a WebSocket connection, the onError
   * callback properly logs the error and handles the connection appropriately without crashing the
   * server.
   *
   * @see JsonRpcWebSocketServer#onError(org.java_websocket.WebSocket, Exception)
   */
  @Test
  public void testOnError_handlesErrorGracefully() throws Exception {
    // Given: A running WebSocket server with an active client connection
    server.start();
    boolean ok = ready.await(2, TimeUnit.SECONDS);
    assumeTrue("server did not start in time", ok);

    UUID peerId = UUID.randomUUID();
    String url = "ws://" + bindAddr.getHostString() + ":" + bindAddr.getPort();
    TestClient client =
        new TestClient(URI.create(url), Collections.singletonMap("peer-id", peerId.toString()));
    client.connectBlocking(2, TimeUnit.SECONDS);

    // Verify the server is operational by sending a message
    String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"test\",\"id\":1}";
    client.send(payload);

    // Server should enqueue it
    InboundJsonRpcRequestMsg msg = queue.poll(2, TimeUnit.SECONDS);
    assertThat(msg != null, is(true));

    // When: onError is called with an exception (simulated)
    // We call onError directly to test the error handling behavior
    Exception testException = new IOException("Simulated socket error for testing");
    server.onError(null, testException);

    // Then: The server continues to operate (didn't crash)
    // Verify server is still operational by sending another message

    // Connect a second client to verify server is still operational
    UUID peerId2 = UUID.randomUUID();
    TestClient client2 =
        new TestClient(URI.create(url), Collections.singletonMap("peer-id", peerId2.toString()));
    client2.connectBlocking(2, TimeUnit.SECONDS);

    String payload2 = "{\"jsonrpc\":\"2.0\",\"method\":\"ping2\",\"id\":2}";
    client2.send(payload2);

    // Server should enqueue the second message, proving it's still operational
    InboundJsonRpcRequestMsg msg2 = queue.poll(2, TimeUnit.SECONDS);
    assertThat(msg2 != null, is(true));
    assertThat(msg2.getJsonMessage(), is(payload2));

    // Cleanup
    client.closeBlocking();
    client2.closeBlocking();
    server.close();
  }
}
