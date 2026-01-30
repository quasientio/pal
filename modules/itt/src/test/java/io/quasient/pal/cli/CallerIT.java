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
import static org.junit.Assert.fail;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

    // Call a method targeting the log
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-l",
            logName,
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
  @Ignore("Awaiting implementation in #375")
  public void testCall_zmqRpc_methodInvocation() throws Exception {
    // Given: Peer running with ZMQ RPC; method target class available
    // - Launch peer with --zmq-rpc auto
    // - Use io.quasient.pal.apps.quantized.rpc.Methods class which has various test methods

    // When: `pal call -d localhost:2379 -p <peer-uuid> --rpc-type ZMQ_RPC -m <method> <class> args`
    // - Execute pal call command targeting the peer via ZMQ RPC
    // - Call a static method with arguments (e.g., staticStringWithIntArg)

    // Then: Exit code 0; stdout contains return value
    // - Command should complete successfully
    // - Output should contain the expected return value from the method

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests constructor invocation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can invoke a constructor on a remote
   * peer using ZMQ RPC transport to create a new object instance.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #375")
  public void testCall_zmqRpc_constructorInvocation() throws Exception {
    // Given: Peer running with ZMQ RPC
    // - Launch peer with --zmq-rpc auto
    // - Target class must have accessible constructor

    // When: `pal call -d localhost:2379 -p <peer-uuid> --rpc-type ZMQ_RPC -m new <class>`
    // - Execute pal call command targeting the peer via ZMQ RPC
    // - Use -m new to invoke the constructor

    // Then: Exit code 0; object reference returned
    // - Command should complete successfully
    // - Output should contain an ObjectRef (UUID reference to the created object)

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests field get operation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can read a field value from a remote
   * peer using ZMQ RPC transport.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #375")
  public void testCall_zmqRpc_fieldGet() throws Exception {
    // Given: Peer running with object instance or class with static field
    // - Launch peer with --zmq-rpc auto
    // - Use io.quasient.pal.apps.rpc.Variables class which has test fields

    // When: `pal call` with field get operation via ZMQ RPC
    // - Execute pal call command targeting the peer via ZMQ RPC
    // - Use appropriate syntax for field get (e.g., get operation on static field)

    // Then: Exit code 0; field value returned
    // - Command should complete successfully
    // - Output should contain the expected field value

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests field set operation via ZMQ RPC protocol.
   *
   * <p>This test verifies that the {@code pal call} command can write a field value on a remote
   * peer using ZMQ RPC transport.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #375")
  public void testCall_zmqRpc_fieldSet() throws Exception {
    // Given: Peer running with object instance or class with static field
    // - Launch peer with --zmq-rpc auto
    // - Use io.quasient.pal.apps.rpc.Variables class which has test fields

    // When: `pal call` with field set operation via ZMQ RPC
    // - Execute pal call command targeting the peer via ZMQ RPC
    // - Use appropriate syntax for field set (e.g., put operation on static field)

    // Then: Exit code 0; field updated
    // - Command should complete successfully
    // - Subsequent field get should return the new value

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #375")
  public void testCall_unreachablePeer_failsGracefully() throws Exception {
    // Given: No peer running at specified address
    // - Do NOT launch any peer
    // - Use a nonexistent peer UUID or unreachable address

    // When: `pal call -d localhost:2379 -p nonexistent-peer --rpc-type ZMQ_RPC -m foo <class>`
    // - Execute pal call command targeting a nonexistent peer
    // - The command should attempt ZMQ RPC connection and fail

    // Then: Non-zero exit code; error message in stderr
    // - Command should exit with non-zero code
    // - stderr should contain meaningful error message about peer not found or unreachable

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #375")
  public void testCall_invalidMethod_returnsError() throws Exception {
    // Given: Peer running with ZMQ RPC
    // - Launch peer with --zmq-rpc auto
    // - Target class exists but method does not

    // When: `pal call` with non-existent method via ZMQ RPC
    // - Execute pal call command targeting the peer via ZMQ RPC
    // - Specify a method name that does not exist (e.g., nonExistentMethod)

    // Then: Non-zero exit code; error message about method not found
    // - Command should exit with non-zero code
    // - Output should contain error message indicating method not found or NoSuchMethodException

    // TODO(#375): Implement after #375 provides the implementation
    fail("Not yet implemented");
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
   * as a distinct acceptance criterion for issue #375 coverage.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #376")
  public void testCall_jsonRpc_methodInvocation() throws Exception {
    // Given: Peer running with JSON RPC enabled
    // - Launch peer with --json-rpc auto
    // - Use io.quasient.pal.apps.quantized.rpc.Methods class which has various test methods

    // When: `pal call` using JSON RPC with stdin input
    // - Create JSON-RPC request: {"jsonrpc":"2.0","id":"1","method":"call",
    //   "params":{"type":"<class>","method":"<method>","args":[...]}}
    // - Send request via stdin to pal call command targeting the peer's JSON-RPC address
    // - The address should be retrieved via getPeerJsonRpcAddress(peerId)

    // Then: Exit code 0; JSON response returned
    // - Command should complete with exit code 0
    // - stdout should contain JSON-RPC response with result field
    // - Response should contain expected return value from method invocation

    // TODO(#376): Implement after #376 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests async fire-and-forget invocation.
   *
   * <p>This test verifies that the {@code pal call} command with the {@code --forget-response} flag
   * returns immediately without waiting for the method execution result. This is useful for
   * one-way/asynchronous messaging patterns where the caller does not need the response.
   *
   * <p><b>Note:</b> This test is similar to testCall_forgetResponse_returnsImmediately but is
   * specified as a distinct acceptance criterion for issue #375 coverage.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #376")
  public void testCall_async_fireAndForget() throws Exception {
    // Given: Peer running; --forget-response flag
    // - Launch peer with ZMQ RPC or JSON RPC enabled
    // - The method being called may take some time to execute

    // When: `pal call -d localhost:2379 -p <peer> --forget-response -c Class -m method`
    // - Execute pal call command with --forget-response flag
    // - The command should send the request and return immediately
    // - It should NOT wait for the method to complete execution

    // Then: Exit code 0; command returns immediately without waiting for response
    // - Command should complete with exit code 0
    // - Elapsed time should be minimal (< 5 seconds) regardless of method execution time
    // - No result output is expected (fire-and-forget semantics)

    // TODO(#376): Implement after #376 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests writing a message to a Kafka log via pal call.
   *
   * <p>This test verifies that the {@code pal call} command can write a message to a Kafka log
   * using the {@code --output-log} option. The message is serialized and appended to the specified
   * Kafka topic.
   *
   * <p><b>Note:</b> This test is similar to testCall_toLog_writesMessage but explicitly verifies
   * Kafka log backend as specified in the acceptance criterion for issue #375.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #376")
  public void testCall_toKafkaLog_writesMessage() throws Exception {
    // Given: Kafka log exists
    // - Ensure Kafka infrastructure is running (see modules/itt/README.md)
    // - Create or use an existing Kafka topic as the target log
    // - Launch a peer with Kafka servers configured (-k option)

    // When: `pal call -d localhost:2379 --output-log <log-name> -c Class -m method`
    // - Execute pal call command with --output-log pointing to a Kafka log
    // - The log name should be a Kafka topic name (not a file:/ path)
    // - Include appropriate class and method arguments

    // Then: Exit code 0; message appears in log (verify with pal print)
    // - Command should complete with exit code 0
    // - Run pal print command to verify the message was written to the Kafka log
    // - The printed output should contain the method invocation details

    // TODO(#376): Implement after #376 provides the implementation
    fail("Not yet implemented");
  }

  /**
   * Tests writing a message to a Chronicle log via pal call.
   *
   * <p>This test verifies that the {@code pal call} command can write a message to a Chronicle
   * Queue log using the {@code --output-log} option with a file:/ URI. The message is serialized
   * and appended to the local Chronicle Queue.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #376")
  public void testCall_toChronicleLog_writesMessage() throws Exception {
    // Given: Chronicle log path
    // - Choose a temporary directory for Chronicle Queue storage
    // - Use file:/ URI scheme to specify Chronicle backend (e.g., file:/tmp/test-log)
    // - No Kafka servers needed for Chronicle-only operation

    // When: `pal call --output-log file:/tmp/test-log -c Class -m method`
    // - Execute pal call command with --output-log pointing to a Chronicle log
    // - The log path must use file:/ URI scheme
    // - Include appropriate class and method arguments

    // Then: Exit code 0; message appears in Chronicle log
    // - Command should complete with exit code 0
    // - Verify the Chronicle Queue directory was created at the specified path
    // - Run pal print command to verify the message was written to the Chronicle log
    // - The printed output should contain the method invocation details

    // TODO(#376): Implement after #376 provides the implementation
    fail("Not yet implemented");
  }
}
