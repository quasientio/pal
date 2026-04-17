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
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import io.quasient.pal.PeerProcess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal peer call} and {@code pal log call} commands.
 *
 * <p>The {@code pal peer call} command supports two distinct invocation modes:
 *
 * <ul>
 *   <li><b>CLI Mode</b>: Invokes methods with String[] signature using command-line arguments. Uses
 *       StaticMethodCallBuilder which requires the {@code -m methodName} option. Example: {@code
 *       pal peer call -d dir peerName -m processArgs com.example.Class arg1 arg2}
 *   <li><b>JSON-RPC Stdin Mode</b>: Sends JSON-RPC requests via stdin to invoke methods,
 *       constructors, and field operations with any signatures. Supports all operation types (call,
 *       new, get, put) and arbitrary parameter types. Example: {@code echo '{"jsonrpc": "2.0",
 *       "method": "call", ...}' | pal peer call -d dir ws://localhost:9001}
 * </ul>
 *
 * <p>The {@code pal log call} command writes messages to logs (Kafka or Chronicle).
 *
 * <p>Tests also cover writing messages to logs and async RPC invocations.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class CallerIT extends AbstractCliIT {

  /** The fully-qualified class name of the quantized Methods class for RPC tests. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** The fully-qualified class name of the Variables class for field access tests. */
  private static final String VARIABLES_CLASS = "io.quasient.foobar.apps.rpc.Variables";

  /** A peer process handle for tests that launch peers, or null if none launched. */
  private PeerProcess peerProcess;

  /** Initializes test state before each test method. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Tears down test state after each test method, stopping any launched peer.
   *
   * @throws Exception if stopping the peer fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  // ==========================================================================
  // Peer call tests via ZMQ RPC: pal peer call
  // command: pal peer call <peer> ...
  // ==========================================================================

  /**
   * Tests that {@code pal peer call -m} can invoke a non-main static method with String[] parameter
   * via ZMQ RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_zmqRpc_nonMainMethod() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-zmq-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result =
        runPeerCall(
            "-d",
            palDir,
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            METHODS_CLASS,
            "arg1",
            "arg2",
            "arg3");

    assertThat("Expected exit code 0 for peer call", result.exitCode(), is(0));
    assertThat("Expected PROCESSED in output", result.stdout(), containsString("PROCESSED"));
    assertThat(
        "Expected arg1,arg2,arg3 in output", result.stdout(), containsString("arg1,arg2,arg3"));
  }

  /**
   * Tests that {@code pal peer call} can invoke a method via ZMQ RPC protocol.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_zmqRpc_methodInvocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-zmq-inv-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result =
        runPeerCall(
            "-d",
            palDir,
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "staticStringWithStringArgs",
            METHODS_CLASS,
            "hello",
            "world");

    assertThat("Expected exit code 0 for peer call", result.exitCode(), is(0));
    assertThat("Expected RESULT: in output", result.stdout(), containsString("RESULT:"));
    assertThat("Expected hello,world in output", result.stdout(), containsString("hello,world"));
  }

  /**
   * Tests that {@code pal peer call} can invoke a constructor via JSON-RPC stdin to a ZMQ+JSON
   * peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_zmqRpc_constructorInvocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-ctor-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"new\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\"}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for constructor invocation", result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal peer call} can read a field value via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_zmqRpc_fieldGet() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-fget-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"get\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aClassString\"}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for field get", result.exitCode(), is(0));
    assertThat(
        "Expected 'classy' in output for aClassString field",
        result.stdout(),
        containsString("classy"));
  }

  /**
   * Tests that {@code pal peer call} can set a field value via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_zmqRpc_fieldSet() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-fset-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String putRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"put\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aStaticPublicInteger\",\"value\":12345}}";
    String getRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"get\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aStaticPublicInteger\"}}";

    String stdinData = putRequest + "\n" + getRequest + "\n";

    CliProcessResult result =
        runPeerCallWithStdin(stdinData, "-d", palDir, jsonRpcAddr, "--add-ids");

    assertThat("Expected exit code 0 for field set+get", result.exitCode(), is(0));
    assertThat(
        "Expected 12345 in output after setting field", result.stdout(), containsString("12345"));
  }

  /**
   * Tests that {@code pal peer call} handles unreachable peer gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_unreachablePeer_failsGracefully() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result =
        runPeerCall(
            "-d",
            palDir,
            UUID.randomUUID().toString(),
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            METHODS_CLASS,
            "arg1");

    assertThat("Expected non-zero exit code for unreachable peer", result.exitCode(), is(not(0)));
  }

  /**
   * Tests that {@code pal peer call} returns an error for a non-existent method.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_invalidMethod_returnsError() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-invalid-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult result =
        runPeerCall(
            "-d",
            palDir,
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "nonExistentMethod",
            METHODS_CLASS,
            "arg1");

    // Invalid method should result in a non-zero exit or error in output
    boolean hasError =
        result.exitCode() != 0
            || result.stdout().contains("Exception")
            || result.stdout().contains("error")
            || result.stdout().contains("RaisedThrowable")
            || result.stderr().contains("Exception")
            || result.stderr().contains("error");
    assertThat("Expected error for non-existent method", hasError, is(true));
  }

  // ==========================================================================
  // Peer call tests via JSON-RPC: pal peer call
  // command: pal peer call <peer> (with stdin)
  // ==========================================================================

  /**
   * Tests that {@code pal peer call} can invoke a method via JSON-RPC sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_methodInvocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-inv-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\","
            + "\"params\":{\"type\":\""
            + METHODS_CLASS
            + "\","
            + "\"method\":\"staticStringWithStringArg\","
            + "\"args\":[\"json-rpc-test\"]}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for JSON-RPC method invocation", result.exitCode(), is(0));
    assertThat("Expected RESULT: in output", result.stdout(), containsString("RESULT:"));
    assertThat(
        "Expected json-rpc-test in output", result.stdout(), containsString("json-rpc-test"));
  }

  /**
   * Tests that {@code pal peer call} can create an object via JSON-RPC constructor call.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_constructor() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-ctor-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"new\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\"}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for JSON-RPC constructor call", result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal peer call} can read a field via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_fieldGet() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-fget-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"get\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aClassString\"}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for JSON-RPC field get", result.exitCode(), is(0));
    assertThat("Expected 'classy' in output", result.stdout(), containsString("classy"));
  }

  /**
   * Tests that {@code pal peer call} can set a field via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_fieldSet() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-fset-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String putRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"put\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aStaticPublicInteger\",\"value\":9999}}";
    String getRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"get\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aStaticPublicInteger\"}}";

    String stdinData = putRequest + "\n" + getRequest + "\n";

    CliProcessResult result =
        runPeerCallWithStdin(stdinData, "-d", palDir, jsonRpcAddr, "--add-ids");

    assertThat("Expected exit code 0 for JSON-RPC field set", result.exitCode(), is(0));
    assertThat(
        "Expected 9999 in output after setting field", result.stdout(), containsString("9999"));
  }

  /**
   * Tests that {@code pal peer call} can process multiple JSON-RPC requests from stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_multipleRequests() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-multi-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String callRequest1 =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\","
            + "\"params\":{\"type\":\""
            + METHODS_CLASS
            + "\","
            + "\"method\":\"staticStringWithStringArg\","
            + "\"args\":[\"first\"]}}";
    String callRequest2 =
        "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"call\","
            + "\"params\":{\"type\":\""
            + METHODS_CLASS
            + "\","
            + "\"method\":\"staticStringWithStringArg\","
            + "\"args\":[\"second\"]}}";
    String getRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"get\","
            + "\"params\":{\"type\":\""
            + VARIABLES_CLASS
            + "\","
            + "\"field\":\"aClassString\"}}";

    String stdinData = callRequest1 + "\n" + callRequest2 + "\n" + getRequest + "\n";

    CliProcessResult result = runPeerCallWithStdin(stdinData, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for multiple JSON-RPC requests", result.exitCode(), is(0));
    assertThat("Expected first call result in output", result.stdout(), containsString("first"));
    assertThat("Expected second call result in output", result.stdout(), containsString("second"));
    assertThat("Expected field get result in output", result.stdout(), containsString("classy"));
  }

  /**
   * Tests that {@code pal peer call} with JSON-RPC returns method invocation result.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpc_methodInvocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-jrpc-meth-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\","
            + "\"params\":{\"type\":\""
            + METHODS_CLASS
            + "\","
            + "\"method\":\"staticStringWithStringArg\","
            + "\"args\":[\"rpc-method-test\"]}}\n";

    CliProcessResult result = runPeerCallWithStdin(jsonRequest, "-d", palDir, jsonRpcAddr);

    assertThat("Expected exit code 0 for JSON-RPC method invocation", result.exitCode(), is(0));
    assertThat("Expected RESULT: in output", result.stdout(), containsString("RESULT:"));
    assertThat(
        "Expected rpc-method-test in output", result.stdout(), containsString("rpc-method-test"));
  }

  // ==========================================================================
  // Regression tests for PeerCall JSON-RPC product bugs
  // ==========================================================================

  /**
   * Tests that {@code pal peer call ws://<addr> ClassName} works — i.e., calling a method via
   * JSON-RPC using positional class/method args rather than stdin.
   *
   * <p>Regression test for a bug where {@code StaticMethodCallBuilder.buildJsonRpc()} failed to set
   * the top-level JSON-RPC {@code "method"} field (should be {@code "call"}), causing the server to
   * reject the request with "Method is missing".
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpc_directWsWithClassName() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-ws-class-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);

    CliProcessResult result =
        runPeerCall(jsonRpcAddr, "-m", "processArgs", METHODS_CLASS, "ws-direct");

    assertThat("Expected exit code 0 for ws:// + class positional call", result.exitCode(), is(0));
    assertThat("Expected PROCESSED: in output", result.stdout(), containsString("PROCESSED:"));
    assertThat("Expected ws-direct in output", result.stdout(), containsString("ws-direct"));
  }

  /**
   * Tests that {@code pal peer call -d <dir> -r JSON_RPC <peer-name>} with stdin works — i.e.,
   * JSON-RPC via directory lookup when the peer has both ZMQ-RPC and JSON-RPC endpoints.
   *
   * <p>Regression test for a bug where {@code validateInput()} rejected {@code -r JSON_RPC} when
   * the peer was identified by name (directory lookup) rather than by a direct {@code ws://}
   * address, throwing "Peer address must start with ws:// when using JSON-RPC."
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCall_jsonRpcStdin_directoryLookupWithRpcTypeFlag() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "call-dir-jrpc-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    String jsonRequest =
        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\","
            + "\"params\":{\"type\":\""
            + METHODS_CLASS
            + "\","
            + "\"method\":\"staticStringWithStringArg\","
            + "\"args\":[\"dir-lookup-test\"]}}\n";

    CliProcessResult result =
        runPeerCallWithStdin(jsonRequest, "-d", palDir, "-r", "JSON_RPC", peerName);

    assertThat("Expected exit code 0 for JSON-RPC via directory lookup", result.exitCode(), is(0));
    assertThat("Expected RESULT: in output", result.stdout(), containsString("RESULT:"));
    assertThat(
        "Expected dir-lookup-test in output", result.stdout(), containsString("dir-lookup-test"));
  }

  // ==========================================================================
  // Log call tests: pal log call
  // command: pal log call <log> ...
  // ==========================================================================

  /**
   * Tests that {@code pal log call} writes a message to a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_toKafkaLog_writesMessage() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "logcall-kafka-" + generateId();
    String sourceName = "logcall-kafka-src-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--source-log",
            sourceName,
            "--wal",
            walName,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult callResult =
        runLogCall(
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--output-log",
            sourceName,
            "--input-log",
            walName,
            "-m",
            "staticStringWithStringArg",
            METHODS_CLASS,
            "test-to-log");

    assertThat("Expected exit code 0 for log call", callResult.exitCode(), is(0));

    stopPeer(peerProcess);
    peerProcess = null;

    // Allow Kafka time to commit messages
    Thread.sleep(1000);

    // Verify the message was written to the WAL
    CliProcessResult printResult = runLogPrint("-d", palDir, walName, "--full");
    assertThat("Expected exit code 0 for log print", printResult.exitCode(), is(0));
    assertThat(
        "Expected test-to-log in WAL output", printResult.stdout(), containsString("test-to-log"));
  }

  /**
   * Tests that {@code pal log call} writes a message to a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_toChronicleLog_writesMessage() throws Exception {
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String suffix = generateId();
    String sourceName = "/tmp/logcall-csrc-" + suffix;
    String walName = "/tmp/logcall-cwal-" + suffix;

    trackChronicleLog(sourceName);
    trackChronicleLog(walName);

    // Pre-create source directory — the peer's source log reader expects it to exist
    Files.createDirectories(Path.of(sourceName));

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "--source-log",
            "file:" + sourceName,
            "--wal",
            "file:" + walName,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    CliProcessResult callResult =
        runLogCall(
            "-d",
            palDir,
            "--output-log",
            "file:" + sourceName,
            "--input-log",
            "file:" + walName,
            "-m",
            "staticStringWithStringArgs",
            "file:" + sourceName,
            METHODS_CLASS,
            "chronicle-log-test");

    assertThat("Expected exit code 0 for Chronicle log call", callResult.exitCode(), is(0));
    assertThat(
        "Expected RESULT: chronicle-log-test in output",
        callResult.stdout(),
        containsString("RESULT: chronicle-log-test"));
  }

  // ==========================================================================
  // Async / fire-and-forget tests: pal peer call / pal log call
  // command: pal log call --forget-response ... / pal peer call --forget-response ...
  // ==========================================================================

  /**
   * Tests that {@code pal log call --forget-response} returns immediately without waiting.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_forgetResponse_returnsImmediately() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "logcall-forget-" + generateId();
    String sourceName = "logcall-forget-src-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--source-log",
            sourceName,
            "--wal",
            walName,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    long startTime = System.currentTimeMillis();

    CliProcessResult result =
        runLogCall(
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--output-log",
            sourceName,
            "--forget-response",
            "-m",
            "staticStringWithStringArg",
            METHODS_CLASS,
            "test-forget");

    long elapsed = System.currentTimeMillis() - startTime;

    assertThat("Expected exit code 0 for forget-response log call", result.exitCode(), is(0));
    assertThat("Expected fast completion (< 5000ms) for forget-response", elapsed < 5000, is(true));
  }

  /**
   * Tests async fire-and-forget invocation via {@code pal log call}.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCall_async_fireAndForget() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String walName = "logcall-async-" + generateId();
    String sourceName = "logcall-async-src-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--source-log",
            sourceName,
            "--wal",
            walName,
            "--wal-all-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    long startTime = System.currentTimeMillis();

    CliProcessResult result =
        runLogCall(
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--output-log",
            sourceName,
            "--forget-response",
            "-m",
            "staticStringWithStringArg",
            METHODS_CLASS,
            "async-test");

    long elapsed = System.currentTimeMillis() - startTime;

    assertThat("Expected exit code 0 for async fire-and-forget", result.exitCode(), is(0));
    assertThat(
        "Expected fast completion (< 5000ms) for async fire-and-forget", elapsed < 5000, is(true));
  }
}
