/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal call` command.
 *
 * <p>The `pal call` command supports two distinct invocation modes:
 *
 * <ul>
 *   <li><b>CLI Mode</b>: Invokes methods with String[] signature using command-line arguments. Uses
 *       StaticMethodCallBuilder which requires the {@code -m methodName} option. Example: {@code
 *       pal call -p peer-name -m processArgs com.example.Class arg1 arg2}
 *   <li><b>JSON-RPC Stdin Mode</b>: Sends JSON-RPC requests via stdin to invoke methods,
 *       constructors, and field operations with any signatures. Supports all operation types (call,
 *       new, get, put) and arbitrary parameter types. Example: {@code echo '{"jsonrpc": "2.0",
 *       "method": "call", ...}' | pal call -p ws://localhost:9001}
 * </ul>
 *
 * <p>Tests also cover writing messages to logs and async RPC invocations.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class CallerIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(CallerIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Tests that `pal call -m` can invoke a non-main static method with String[] parameter.
   *
   * <p>Verifies that StaticMethodCallBuilder works with methods other than main, as long as they
   * have the String[] signature.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_nonMainMethod() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-nonmain-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call processArgs method (not main) with multiple arguments
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "arg1",
            "arg2",
            "arg3");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected processed output", callResult.stdout(), containsString("PROCESSED"));
    assertThat("Expected all args", callResult.stdout(), containsString("arg1,arg2,arg3"));

    logger.info("Successfully called non-main method with -m option");
  }

  /**
   * Tests that `pal call` can target a log instead of a peer, writing a message to the log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_toLog_writesMessage() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a target log
    String logName = "test-call-log-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath());

    // Call a void method targeting the log with --forget-response (LOG_RPC AFTER is gated)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            logName,
            "--forget-response",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "staticVoidWithStringArg",
            "test-to-log");

    assertEquals("Expected successful call to log", 0, callResult.exitCode());

    // Verify message was written by printing the log
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", logName, "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected message in stdout", printResult.stdout(), containsString("test-to-log"));

    logger.info("Successfully wrote message to log via call command");
  }

  /**
   * Tests that `pal call --forget-response` returns immediately without waiting for result.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_forgetResponse_returnsImmediately() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a target log
    String logName = "test-call-forget-response-log-" + generateId();

    // Launch a peer with ZMQ RPC enabled
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath());

    // Call a method with --forget-response
    long startTime = System.currentTimeMillis();
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "--log",
            logName,
            "--forget-response",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "staticVoidWithStringArg",
            "test-forget-response");
    long elapsedTime = System.currentTimeMillis() - startTime;

    assertEquals("Expected successful call", 0, callResult.exitCode());
    // calls with '--forget-response' should return quickly (within a few seconds)
    assertThat("Expected call to return quickly", elapsedTime < 5000);

    logger.info("Successfully performed --forget-response call (elapsed time: {} ms)", elapsedTime);
  }

  /**
   * Tests that `pal call` can invoke a method via JSON-RPC sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpcStdin_methodInvocation() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-jsonrpc-method-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Create JSON-RPC request for method invocation
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"test-input"}]}}
        """;

    // Send JSON-RPC request via stdin
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected result in output", callResult.stdout(), containsString("RESULT:"));
    assertThat("Expected test-input in output", callResult.stdout(), containsString("test-input"));

    logger.info("Successfully called method via JSON-RPC stdin");
  }

  /**
   * Tests that `pal call` can create an object via JSON-RPC constructor call sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpcStdin_constructor() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-jsonrpc-ctor-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Create JSON-RPC request for constructor invocation
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods"}}
        """;

    // Send JSON-RPC request via stdin
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    assertEquals("Expected successful call", 0, callResult.exitCode());
    // Constructor should return an ObjectRef (reference number)
    assertThat("Expected ObjectRef in output", !callResult.stdout().isEmpty());

    logger.info("Successfully called constructor via JSON-RPC stdin");
  }

  /**
   * Tests that `pal call` can read a field value via JSON-RPC sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpcStdin_fieldGet() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-jsonrpc-get-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Create JSON-RPC request for field get
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aClassString"}}
        """;

    // Send JSON-RPC request via stdin
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected field value", callResult.stdout(), containsString("classy"));

    logger.info("Successfully read field via JSON-RPC stdin");
  }

  /**
   * Tests that `pal call` can set a field value via JSON-RPC sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpcStdin_fieldSet() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-jsonrpc-set-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Create JSON-RPC requests for field set and get
    String jsonRpcRequests =
        """
        {"jsonrpc":"2.0","id":"1","method":"put","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger","value":9999}}
        {"jsonrpc":"2.0","id":"2","method":"get","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger"}}
        """;

    // Send JSON-RPC requests via stdin
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequests, "-d", palDirectory, "-p", jsonRpcAddress, "--add-ids");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected new value", callResult.stdout(), containsString("9999"));

    logger.info("Successfully set field via JSON-RPC stdin");
  }

  /**
   * Tests that `pal call` can process multiple JSON-RPC requests from stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpcStdin_multipleRequests() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-jsonrpc-multi-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Create multiple JSON-RPC requests (one per line)
    String jsonRpcRequests =
        """
        {"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"req1"}]}}
        {"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"req2"}]}}
        {"jsonrpc":"2.0","id":"3","method":"get","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aClassString"}}
        """;

    // Send JSON-RPC requests via stdin
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequests, "-d", palDirectory, "-p", jsonRpcAddress);

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected req1 result", callResult.stdout(), containsString("req1"));
    assertThat("Expected req2 result", callResult.stdout(), containsString("req2"));
    assertThat("Expected field value", callResult.stdout(), containsString("classy"));

    logger.info("Successfully processed multiple JSON-RPC requests via stdin");
  }

  // ==========================================================================
  // ZMQ RPC Test Specifications (Issue #374)
  // These tests exercise Caller.sendRequestsWithSingleClient() via ZMQ_RPC protocol
  // Implementation: Issue #375
  // ==========================================================================

  /**
   * Tests method invocation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can invoke a static method on a remote
   * peer using ZMQ RPC transport. The Caller.sendRequestsWithSingleClient() method handles the
   * actual ZMQ RPC communication.
   *
   * <p><b>Note:</b> testCall_zmqRpc_nonMainMethod already tests a similar scenario. This test
   * provides additional coverage for the specific acceptance criterion.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_methodInvocation() throws Exception {
    // Given: Peer running with ZMQ RPC; method target class available
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with --zmq-rpc auto
    String peerName = "test-call-zmq-method-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: `pal call -d localhost:2379 -p <peer-name> --rpc-type ZMQ_RPC -m <method> <class> args`
    // Call staticStringWithStringArgs method (has String[] signature compatible with CLI mode)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "staticStringWithStringArgs",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "hello",
            "world");

    // Then: Exit code 0; stdout contains return value
    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected result in output", callResult.stdout(), containsString("RESULT:"));
    assertThat("Expected args in output", callResult.stdout(), containsString("hello,world"));

    logger.info("Successfully called method via ZMQ RPC");
  }

  /**
   * Tests constructor invocation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can invoke a constructor on a remote
   * peer. Note: Constructor invocation requires JSON-RPC format via stdin since the CLI mode only
   * supports static method calls with String[] signature. The peer runs with both ZMQ and JSON-RPC
   * enabled.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_constructorInvocation() throws Exception {
    // Given: Peer running with ZMQ RPC and JSON-RPC (for constructor support)
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with both --zmq-rpc and --json-rpc auto
    String peerName = "test-call-zmq-ctor-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: Send constructor invocation via JSON-RPC stdin to peer
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods"}}
        """;

    // Get peer's JSON-RPC address for stdin mode
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    // Then: Exit code 0; object reference returned
    assertEquals("Expected successful call", 0, callResult.exitCode());
    // Constructor should return an ObjectRef (contains UUID or reference number)
    assertThat("Expected result in output", !callResult.stdout().isEmpty());

    logger.info("Successfully invoked constructor via JSON-RPC to ZMQ+JSON peer");
  }

  /**
   * Tests field get operation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can read a field value from a remote
   * peer. Note: Field operations require JSON-RPC format via stdin since the CLI mode only supports
   * static method calls with String[] signature. The peer runs with both ZMQ and JSON-RPC enabled.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_fieldGet() throws Exception {
    // Given: Peer running with ZMQ RPC and JSON-RPC (for field get support)
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with both --zmq-rpc and --json-rpc auto
    String peerName = "test-call-zmq-fieldget-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: Send field get via JSON-RPC stdin to peer
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aClassString"}}
        """;

    // Get peer's JSON-RPC address for stdin mode
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    // Then: Exit code 0; field value returned
    assertEquals("Expected successful call", 0, callResult.exitCode());
    // aClassString = "I'm classy"
    assertThat("Expected field value", callResult.stdout(), containsString("classy"));

    logger.info("Successfully read field via JSON-RPC to ZMQ+JSON peer");
  }

  /**
   * Tests field set operation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can write a field value on a remote
   * peer. Note: Field operations require JSON-RPC format via stdin since the CLI mode only supports
   * static method calls with String[] signature. The peer runs with both ZMQ and JSON-RPC enabled.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_fieldSet() throws Exception {
    // Given: Peer running with ZMQ RPC and JSON-RPC (for field set support)
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with both --zmq-rpc and --json-rpc auto
    String peerName = "test-call-zmq-fieldset-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: Send field set and get via JSON-RPC stdin to peer
    String jsonRpcRequests =
        """
        {"jsonrpc":"2.0","id":"1","method":"put","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger","value":12345}}
        {"jsonrpc":"2.0","id":"2","method":"get","params":{"type":"io.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger"}}
        """;

    // Get peer's JSON-RPC address for stdin mode
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequests, "-d", palDirectory, "-p", jsonRpcAddress, "--add-ids");

    // Then: Exit code 0; field updated and new value returned
    assertEquals("Expected successful call", 0, callResult.exitCode());
    // Verify the new value is returned
    assertThat("Expected new field value", callResult.stdout(), containsString("12345"));

    logger.info("Successfully set field via JSON-RPC to ZMQ+JSON peer");
  }

  /**
   * Tests error handling when peer is unreachable via ZMQ RPC.
   *
   * <p>This test verifies that the {@code pal call} command fails gracefully when attempting to
   * communicate with a peer that is not running or unreachable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_unreachablePeer_failsGracefully() throws Exception {
    // Given: No peer running at specified address
    String palDirectory = getPalDirectoryUrl();

    // Use a random UUID that doesn't exist - no peer launched
    UUID nonexistentPeerId = UUID.randomUUID();

    // When: `pal call -d localhost:2379 -p <nonexistent-uuid> --rpc-type ZMQ_RPC -m <method>
    // <class>`
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            nonexistentPeerId.toString(),
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "arg1");

    // Then: Non-zero exit code; error message in stderr
    assertThat("Expected non-zero exit code for unreachable peer", callResult.exitCode() != 0);
    // stderr or stdout should contain error message about peer not found
    String combinedOutput = callResult.stdout() + callResult.stderr();
    assertThat(
        "Expected error message about peer",
        combinedOutput.toLowerCase(Locale.ROOT).contains("peer")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("not found")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("error")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("failed")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("exception"));

    logger.info(
        "Unreachable peer test passed with exit code: {} and output: {}",
        callResult.exitCode(),
        combinedOutput);
  }

  /**
   * Tests error handling when invoking a non-existent method via ZMQ RPC.
   *
   * <p>This test verifies that the {@code pal call} command returns an appropriate error when
   * attempting to invoke a method that does not exist on the target class.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_invalidMethod_returnsError() throws Exception {
    // Given: Peer running with ZMQ RPC
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with --zmq-rpc auto
    String peerName = "test-call-invalid-method-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: `pal call` with non-existent method via ZMQ RPC
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "nonExistentMethod", // This method does not exist
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "arg1");

    // Then: The call should complete (exit 0) but return an error in the response
    // or the exit code may be non-zero depending on error handling
    String combinedOutput = callResult.stdout() + callResult.stderr();

    // The peer should report NoSuchMethodException or similar error
    // Check that either exit code is non-zero OR output contains error indicator
    boolean hasError =
        callResult.exitCode() != 0
            || combinedOutput.toLowerCase(Locale.ROOT).contains("nosuchmethodexception")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("exception")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("error")
            || combinedOutput.toLowerCase(Locale.ROOT).contains("not found")
            || combinedOutput.contains("RaisedThrowable");

    assertThat("Expected error for invalid method", hasError);

    logger.info(
        "Invalid method test passed with exit code: {} and output: {}",
        callResult.exitCode(),
        combinedOutput);
  }

  // ==========================================================================
  // JSON RPC and Async Test Specifications (Issue #375)
  // These tests exercise Caller's JSON_RPC protocol and async functionality
  // Implementation: Issue #376
  // ==========================================================================

  /**
   * Tests method invocation via JSON RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can invoke a method on a remote peer
   * using JSON RPC transport with stdin input. The Caller handles JSON-RPC requests sent via stdin
   * and returns JSON-formatted responses.
   *
   * <p><b>Note:</b> This test is similar to testCall_jsonRpcStdin_methodInvocation but is specified
   * as a distinct acceptance criterion for issue #376 coverage.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpc_methodInvocation() throws Exception {
    // Given: Peer running with JSON RPC enabled
    String palDirectory = getPalDirectoryUrl();

    // Launch peer with --json-rpc auto
    String peerName = "test-call-jsonrpc-invoke-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // When: `pal call` using JSON RPC with stdin input
    String jsonRpcRequest =
        """
        {"jsonrpc":"2.0","id":"test-1","method":"call","params":{"type":"io.quasient.pal.apps.quantized.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"hello-jsonrpc"}]}}
        """;

    // Get peer's JSON-RPC address for stdin mode
    String jsonRpcAddress = getPeerJsonRpcAddress(peerId);
    if (jsonRpcAddress == null) {
      throw new RuntimeException("Peer JSON-RPC address not found for peer " + peerId);
    }

    AbstractCliIT.CliProcessResult callResult =
        runCallWithStdin(jsonRpcRequest, "-d", palDirectory, "-p", jsonRpcAddress);

    // Then: Exit code 0; JSON response returned
    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected result in output", callResult.stdout(), containsString("RESULT:"));
    assertThat(
        "Expected input value in output", callResult.stdout(), containsString("hello-jsonrpc"));

    logger.info("Successfully invoked method via JSON-RPC stdin");
  }

  /**
   * Tests async fire-and-forget invocation.
   *
   * <p>This test verifies that the {@code pal call} command with the {@code --forget-response} flag
   * returns immediately without waiting for the method execution result. This is useful for
   * one-way/asynchronous messaging patterns where the caller does not need the response.
   *
   * <p><b>Note:</b> This test is similar to testCall_forgetResponse_returnsImmediately but is
   * specified as a distinct acceptance criterion for issue #376 coverage.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_async_fireAndForget() throws Exception {
    // Given: Peer running with log for async messaging
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a target log
    String logName = "test-call-async-fire-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath());

    // When: `pal call --forget-response` to send message without waiting for response
    long startTime = System.currentTimeMillis();
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "--log",
            logName,
            "--forget-response",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "staticVoidWithStringArg",
            "async-test");
    long elapsedTime = System.currentTimeMillis() - startTime;

    // Then: Exit code 0; command returns immediately without waiting for response
    assertEquals("Expected successful call", 0, callResult.exitCode());
    // Fire-and-forget should return quickly (within a few seconds)
    assertThat("Expected call to return quickly", elapsedTime < 5000);

    logger.info("Async fire-and-forget test passed (elapsed time: {} ms)", elapsedTime);
  }

  /**
   * Tests writing a message to a Kafka log via pal call.
   *
   * <p>This test verifies that the {@code pal call} command can write a message to a Kafka log
   * using the {@code --log} option. The message is serialized and appended to the specified Kafka
   * topic.
   *
   * <p><b>Note:</b> This test is similar to testCall_toLog_writesMessage but explicitly verifies
   * Kafka log backend as specified in the acceptance criterion for issue #376.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_toKafkaLog_writesMessage() throws Exception {
    // Given: Kafka log exists
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with Kafka servers configured to create the log
    String logName = "test-call-kafka-log-" + generateId();
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath());

    // When: `pal call --log <log-name>` to write message to Kafka log (--forget-response
    // because LOG_RPC AFTER is gated)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            logName,
            "--forget-response",
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "staticVoidWithStringArg",
            "kafka-log-test-message");

    // Then: Exit code 0; message appears in log (verify with pal print)
    assertEquals("Expected successful call to Kafka log", 0, callResult.exitCode());

    // Verify message was written by printing the log
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-l", logName, "--full");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat(
        "Expected message in log output",
        printResult.stdout(),
        containsString("kafka-log-test-message"));

    logger.info("Successfully wrote message to Kafka log via call command");
  }

  /**
   * Tests writing a message to a Chronicle log via pal call.
   *
   * <p>This test verifies that the {@code pal call} command can write a message to a Chronicle
   * Queue log using the {@code --log} option with a file: URI. The message is serialized and
   * appended to the local Chronicle Queue.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_toChronicleLog_writesMessage() throws Exception {
    // Given: Chronicle log path (split source/WAL for LOG_RPC response delivery)
    String palDirectory = getPalDirectoryUrl();

    String sourceName = "test-call-chronicle-caller-src-" + generateId();
    String walName = "test-call-chronicle-caller-wal-" + generateId();
    trackChronicleLog(sourceName);
    trackChronicleLog(walName);

    // Pre-create the source Chronicle directory so the peer can start reading from it.
    // Chronicle source log reader requires the directory to exist when source != WAL.
    String palHome = System.getenv("PAL_HOME");
    Files.createDirectories(Paths.get(palHome, sourceName));

    // Launch a peer with split Chronicle logs and --wal-all-incoming-rpc
    UUID peerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-s",
            "file:" + sourceName,
            "-w",
            "file:" + walName,
            "--wal-all-incoming-rpc",
            "-cp",
            getIttAppsClasspath());

    // When: `pal call` writes to source log, reads response from WAL
    // Use -m to specify method name for CLI mode (requires String[] signature)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "--output-log",
            "file:" + sourceName,
            "--input-log",
            "file:" + walName,
            "io.quasient.pal.apps.quantized.rpc.Methods",
            "-m",
            "staticStringWithStringArgs",
            "chronicle-log-test-message");

    // Then: Exit code 0; response received from WAL
    assertEquals("Expected successful call to Chronicle log", 0, callResult.exitCode());
    assertThat(
        "Expected result in output",
        callResult.stdout(),
        containsString("RESULT: chronicle-log-test-message"));

    logger.info("Successfully wrote message to Chronicle log via call command");
  }
}
