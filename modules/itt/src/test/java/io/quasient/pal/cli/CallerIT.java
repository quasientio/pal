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

import static org.junit.Assert.fail;

import org.junit.Ignore;
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

  // ==========================================================================
  // Peer call tests via ZMQ RPC: pal peer call
  // Old command: pal call -p <peer> ...
  // New command: pal peer call <peer> ...
  // ==========================================================================

  /**
   * Tests that {@code pal peer call -m} can invoke a non-main static method with String[] parameter
   * via ZMQ RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_zmqRpc_nonMainMethod() throws Exception {
    // Given: A peer launched with ZMQ RPC enabled and a specific name
    // When: `pal peer call -d <palDirectory> <peerName> --rpc-type ZMQ_RPC -m processArgs
    //       io.quasient.foobar.apps.quantized.rpc.Methods arg1 arg2 arg3` is executed
    //       via runPeerCall()
    // Then: Exit code is 0, stdout contains "PROCESSED" and "arg1,arg2,arg3"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can invoke a method via ZMQ RPC protocol.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_zmqRpc_methodInvocation() throws Exception {
    // Given: A peer launched with ZMQ RPC enabled
    // When: `pal peer call -d <palDirectory> <peerName> --rpc-type ZMQ_RPC -m
    //       staticStringWithStringArgs io.quasient.foobar.apps.quantized.rpc.Methods hello world`
    //       is executed via runPeerCall()
    // Then: Exit code is 0, stdout contains "RESULT:" and "hello,world"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can invoke a constructor via JSON-RPC stdin to a ZMQ+JSON
   * peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_zmqRpc_constructorInvocation() throws Exception {
    // Given: A peer launched with both ZMQ RPC and JSON-RPC enabled
    // When: JSON-RPC "new" request sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains ObjectRef (reference number)

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can read a field value via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_zmqRpc_fieldGet() throws Exception {
    // Given: A peer launched with both ZMQ RPC and JSON-RPC enabled
    // When: JSON-RPC "get" request for aClassString field sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains "classy"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can set a field value via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_zmqRpc_fieldSet() throws Exception {
    // Given: A peer launched with both ZMQ RPC and JSON-RPC enabled
    // When: JSON-RPC "put" then "get" requests for aStaticPublicInteger sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress> --add-ids`
    //       via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains the new value "12345"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} handles unreachable peer gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_unreachablePeer_failsGracefully() throws Exception {
    // Given: No peer running at specified UUID
    // When: `pal peer call -d <palDirectory> <nonExistentUuid> --rpc-type ZMQ_RPC -m processArgs
    //       io.quasient.foobar.apps.quantized.rpc.Methods arg1` is executed via runPeerCall()
    // Then: Non-zero exit code, error message in stderr/stdout about peer not found

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} returns an error for a non-existent method.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_invalidMethod_returnsError() throws Exception {
    // Given: A peer launched with ZMQ RPC enabled
    // When: `pal peer call -d <palDirectory> <peerName> --rpc-type ZMQ_RPC -m nonExistentMethod
    //       io.quasient.foobar.apps.quantized.rpc.Methods arg1` is executed via runPeerCall()
    // Then: Non-zero exit code or error indicator in output (NoSuchMethodException,
    // RaisedThrowable)

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Peer call tests via JSON-RPC: pal peer call
  // Old command: pal call -p <peer> (with stdin)
  // New command: pal peer call <peer> (with stdin)
  // ==========================================================================

  /**
   * Tests that {@code pal peer call} can invoke a method via JSON-RPC sent through stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpcStdin_methodInvocation() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: JSON-RPC "call" request sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains "RESULT:" and the input value

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can create an object via JSON-RPC constructor call.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpcStdin_constructor() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: JSON-RPC "new" request sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains ObjectRef

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can read a field via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpcStdin_fieldGet() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: JSON-RPC "get" request for aClassString sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains "classy"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can set a field via JSON-RPC stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpcStdin_fieldSet() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: JSON-RPC "put" then "get" requests for aStaticPublicInteger sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress> --add-ids`
    //       via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains "9999"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} can process multiple JSON-RPC requests from stdin.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpcStdin_multipleRequests() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: Multiple JSON-RPC requests (two "call" + one "get") sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains results from all three requests

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal peer call} with JSON-RPC returns method invocation result.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testPeerCall_jsonRpc_methodInvocation() throws Exception {
    // Given: A peer launched with JSON-RPC enabled
    // When: JSON-RPC "call" request sent via stdin to
    //       `pal peer call -d <palDirectory> <jsonRpcAddress>` via runPeerCallWithStdin()
    // Then: Exit code is 0, stdout contains "RESULT:" and the input value

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Log call tests: pal log call
  // Old command: pal call -l <log> ...
  // New command: pal log call <log> ...
  // ==========================================================================

  /**
   * Tests that {@code pal log call} writes a message to a Kafka log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_toKafkaLog_writesMessage() throws Exception {
    // Given: A Kafka WAL created by launching a peer
    // When: `pal log call -d <palDirectory> <logName> --forget-response
    //       io.quasient.foobar.apps.quantized.rpc.Methods staticVoidWithStringArg test-to-log`
    //       is executed via runLogCall()
    // Then: Exit code is 0, and `pal log print` output contains "test-to-log"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that {@code pal log call} writes a message to a Chronicle log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_toChronicleLog_writesMessage() throws Exception {
    // Given: A peer launched with split Chronicle logs (source + WAL) and --wal-all-incoming-rpc
    // When: `pal log call -d <palDirectory> --output-log file:<source> --input-log file:<wal>
    //       io.quasient.foobar.apps.quantized.rpc.Methods -m staticStringWithStringArgs
    //       chronicle-log-test` is executed via runLogCall()
    // Then: Exit code is 0, stdout contains "RESULT: chronicle-log-test"

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  // ==========================================================================
  // Async / fire-and-forget tests: pal peer call / pal log call
  // Old command: pal call --forget-response ...
  // New command: pal log call --forget-response ... / pal peer call --forget-response ...
  // ==========================================================================

  /**
   * Tests that {@code pal log call --forget-response} returns immediately without waiting.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_forgetResponse_returnsImmediately() throws Exception {
    // Given: A Kafka WAL created by launching a peer
    // When: `pal log call -d <palDirectory> --log <logName> --forget-response
    //       io.quasient.foobar.apps.quantized.rpc.Methods staticVoidWithStringArg test-forget`
    //       is executed via runLogCall()
    // Then: Exit code is 0, elapsed time < 5000ms

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests async fire-and-forget invocation via {@code pal log call}.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1205")
  public void testLogCall_async_fireAndForget() throws Exception {
    // Given: A Kafka WAL created by launching a peer
    // When: `pal log call -d <palDirectory> --log <logName> --forget-response
    //       io.quasient.foobar.apps.quantized.rpc.Methods staticVoidWithStringArg async-test`
    //       is executed via runLogCall()
    // Then: Exit code is 0, command returns quickly (< 5000ms)

    // TODO(#1205): Implement test logic
    fail("Not yet implemented");
  }
}
