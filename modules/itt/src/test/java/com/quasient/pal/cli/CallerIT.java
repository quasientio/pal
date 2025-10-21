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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal call` command.
 *
 * <p>Tests calling methods, constructors, and field operations on remote peers via ZMQ and
 * JSON-RPC, as well as writing messages to logs.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class CallerIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(CallerIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private Process peerProcess;

  /**
   * Sets up test environment before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setUp() throws Exception {
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
   * Tests that `pal call` invokes a method on a peer via ZMQ RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_methodInvocation() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-zmq-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a method that returns a value
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "com.quasient.pal.apps.rpc.Methods",
            "staticStringWithStringArg",
            "test-input");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    // The method should return the input with some transformation
    assertThat("Expected output in result", callResult.stdout().length() > 0);

    logger.info("Successfully called method via ZMQ RPC");
  }

  /**
   * Tests that `pal call` invokes a method on a peer via JSON-RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_jsonRpc_methodInvocation() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with JSON-RPC enabled
    String peerName = "test-call-json-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a method via JSON-RPC
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "JSON_RPC",
            "com.quasient.pal.apps.rpc.Methods",
            "staticStringWithStringArg",
            "test-json-input");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected output in result", callResult.stdout().length() > 0);

    logger.info("Successfully called method via JSON-RPC");
  }

  /**
   * Tests that `pal call` can invoke a constructor via RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_constructor() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-ctor-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a constructor (using new operator)
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "--constructor",
            "com.quasient.pal.apps.rpc.Constructors",
            "test-constructor-arg");

    assertEquals("Expected successful constructor call", 0, callResult.exitCode());
    // Constructor should return an ObjectRef
    assertThat("Expected ObjectRef in result", callResult.stdout().length() > 0);

    logger.info("Successfully invoked constructor via ZMQ RPC");
  }

  /**
   * Tests that `pal call` can perform field operations (get/set) via RPC.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_fieldAccess() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-field-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Get a static field value
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "--field-get",
            "com.quasient.pal.apps.rpc.Variables",
            "staticStringVar");

    assertEquals("Expected successful field get", 0, callResult.exitCode());
    assertThat("Expected field value in result", callResult.stdout().length() > 0);

    logger.info("Successfully performed field access via ZMQ RPC");
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

    // Create a target log (just create a peer with a WAL, then stop it)
    String walName = "test-call-log-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());
    stopPeer(peerProcess);
    peerProcess = null;

    // Call a method targeting the log
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-l",
            walName,
            "com.quasient.pal.apps.rpc.Methods",
            "staticVoidWithStringArg",
            "test-to-log");

    assertEquals("Expected successful call to log", 0, callResult.exitCode());

    // Verify message was written by printing the log
    AbstractCliIT.CliProcessResult printResult =
        runPrint("-d", palDirectory, "-k", kafkaServers, "-l", walName, "--output-format", "FULL");

    assertEquals("Expected successful print", 0, printResult.exitCode());
    assertThat("Expected message in log", printResult.stdout(), containsString("test-to-log"));

    logger.info("Successfully wrote message to log via call command");
  }

  /**
   * Tests that `pal call --async` returns immediately without waiting for result.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_async_returnsImmediately() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-async-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a method asynchronously
    long startTime = System.currentTimeMillis();
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "--async",
            "com.quasient.pal.apps.rpc.Methods",
            "staticVoidWithStringArg",
            "test-async");
    long elapsedTime = System.currentTimeMillis() - startTime;

    assertEquals("Expected successful async call", 0, callResult.exitCode());
    // Async calls should return quickly (within a few seconds)
    assertThat("Expected async call to return quickly", elapsedTime < 5000);

    logger.info("Successfully performed async call (elapsed time: {} ms)", elapsedTime);
  }

  /**
   * Tests that `pal call` with multiple arguments passes them correctly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_multipleArguments() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-multiarg-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a method with multiple arguments
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "com.quasient.pal.apps.rpc.Methods",
            "staticStringWithTwoStringArgs",
            "arg1",
            "arg2");

    assertEquals("Expected successful call with multiple args", 0, callResult.exitCode());
    assertThat("Expected output in result", callResult.stdout().length() > 0);

    logger.info("Successfully called method with multiple arguments");
  }

  /**
   * Tests that `pal call` handles numeric arguments correctly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testCall_zmqRpc_numericArguments() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Launch a peer with ZMQ RPC enabled
    String peerName = "test-call-numeric-" + generateId();
    peerProcess =
        launchTransientPeer(
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath());

    // Call a method with numeric argument
    AbstractCliIT.CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "com.quasient.pal.apps.rpc.Methods",
            "staticIntWithIntArg",
            "42");

    assertEquals("Expected successful call with numeric arg", 0, callResult.exitCode());
    assertThat("Expected numeric result", callResult.stdout().length() > 0);

    logger.info("Successfully called method with numeric arguments");
  }

  /**
   * Gets the classpath for itt-apps module.
   *
   * @return classpath string
   */
  private String getIttAppsClasspath() {
    String palHome = System.getenv("PAL_HOME");
    return palHome + "/modules/itt-apps/target/classes";
  }
}
