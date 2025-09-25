/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.websocket;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

import com.quasient.pal.core.internal.messages.InboundJsonRpcRequestMsg;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
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
    bindAddr = new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port);
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
    server.sendResponseToWebSocketClient(
        peerId, response, com.quasient.pal.messages.types.MessageType.CONTROL_MESSAGE_RESPONSE);
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
    java.nio.file.Path tmp = java.nio.file.Files.createTempFile("ws_meta_", ".txt");
    java.nio.file.Files.writeString(tmp, "HELLO-WS", java.nio.charset.StandardCharsets.UTF_8);

    // Build a minimal META_MESSAGE_RESPONSE JSON that JsonRpcWebSocketServer recognizes
    // as FETCH_CLASSES_INFO (service key) and carries the temp file path in response key.
    String metaBody =
        "{\"service\":\""
            + com.quasient.pal.messages.types.MetaServiceType.FETCH_CLASSES_INFO.getJsonName()
            + "\",\"response\":\""
            + tmp.toString().replace("\\", "\\\\")
            + "\"}";
    String response =
        "{\"jsonrpc\":\"2.0\",\"result\":{\"isVoid\":false,\"value\":{\"isNull\":false,\"value\":\""
            + metaBody.replace("\"", "\\\"")
            + "\"}},\"id\":\"42\"}";

    // Trigger streaming path
    server.sendResponseToWebSocketClient(
        peerId, response, com.quasient.pal.messages.types.MessageType.META_MESSAGE_RESPONSE);

    // Client should receive a JSON containing the id and the streamed content
    // Give it a moment
    TimeUnit.MILLISECONDS.sleep(150);
    assumeTrue("no message received from server", client.lastMessage != null);
    assertThat(client.lastMessage.contains("\"id\":\"42\""), is(true));
    assertThat(client.lastMessage.contains("HELLO-WS"), is(true));

    // The file should be deleted by server after streaming
    boolean exists = java.nio.file.Files.exists(tmp);
    assertThat(exists, is(false));

    client.closeBlocking();
    server.close();
  }
}
