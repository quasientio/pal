/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

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
  private Process peerProcess;

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
            "com.quasient.pal.apps.rpc.Methods",
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

    // Call a method targeting the log
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            logName,
            "com.quasient.pal.apps.rpc.Methods",
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
            "com.quasient.pal.apps.rpc.Methods",
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
        {"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.quasient.pal.apps.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"test-input"}]}}
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
        {"jsonrpc":"2.0","id":"1","method":"new","params":{"type":"com.quasient.pal.apps.rpc.Methods"}}
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
        {"jsonrpc":"2.0","id":"1","method":"get","params":{"type":"com.quasient.pal.apps.rpc.Variables","field":"aClassString"}}
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
        {"jsonrpc":"2.0","id":"1","method":"put","params":{"type":"com.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger","value":9999}}
        {"jsonrpc":"2.0","id":"2","method":"get","params":{"type":"com.quasient.pal.apps.rpc.Variables","field":"aStaticPublicInteger"}}
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
        {"jsonrpc":"2.0","id":"1","method":"call","params":{"type":"com.quasient.pal.apps.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"req1"}]}}
        {"jsonrpc":"2.0","id":"2","method":"call","params":{"type":"com.quasient.pal.apps.rpc.Methods","method":"staticStringWithStringArg","args":[{"type":"java.lang.String","value":"req2"}]}}
        {"jsonrpc":"2.0","id":"3","method":"get","params":{"type":"com.quasient.pal.apps.rpc.Variables","field":"aClassString"}}
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
}
