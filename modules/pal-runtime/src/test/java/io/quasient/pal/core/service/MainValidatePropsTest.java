/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;

public class MainValidatePropsTest {

  private static void setField(Object target, String field, Object value) throws Exception {
    Field f = Main.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static Object getField(Object target, String field) throws Exception {
    Field f = Main.class.getDeclaredField(field);
    f.setAccessible(true);
    return f.get(target);
  }

  private static void callValidateInput(Main m) throws Exception {
    Method validate = Main.class.getDeclaredMethod("validateInput");
    validate.setAccessible(true);
    validate.invoke(m);
    Method addMisc = Main.class.getDeclaredMethod("addMiscProperties");
    addMisc.setAccessible(true);
    addMisc.invoke(m);
  }

  @Test
  public void validate_tcpPub_setsTcpOutPubProperty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "tcpPub", "127.0.0.1:45679");
    // set runOptions-indifferent fields and props; no System.exit triggered
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String out = p.getProperty("out.pub");
    assertThat(out, containsString("tcp://127.0.0.1:45679"));
  }

  @Test
  public void validate_jsonRpc_setsWsAddressAndRunOption() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "jsonRpc", "127.0.0.1:8080");
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String addr = p.getProperty("in.json.rpc");
    assertThat(addr, is("ws://127.0.0.1:8080"));
    // runOptions should contain WITH_JSON_RPC; reflect it and check string form
    EnumSet<?> ro = (EnumSet<?>) getField(m, "runOptions");
    assertThat(ro.toString().contains("WITH_JSON_RPC"), is(true));
  }

  @Test
  public void validate_noTcpPub_setsInprocOutPubProperty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    // leave tcpPub null
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String out = p.getProperty("out.pub");
    assertThat(out, containsString("inproc://"));
  }

  // ===== Test stubs for #633 (awaiting implementation in #634) =====

  /**
   * Tests that a Chronicle WAL path (file:/tmp/wal) sets the chronicle log property.
   *
   * <p>Acceptance criterion:
   * [TEST:MainValidatePropsTest.validate_chronicleLog_setsChronicleLogProperty]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_chronicleLog_setsChronicleLogProperty() throws Exception {
    // Given: Main instance with wal set to a Chronicle path (file:/tmp/wal)
    // When: validateInput() and addMiscProperties() are called
    // Then: properties should contain "wal.type" = "CHRONICLE"

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that specifying a source log without Kafka servers (for non-Chronicle log) triggers an
   * error.
   *
   * <p>Acceptance criterion:
   * [TEST:MainValidatePropsTest.validate_sourceLogWithoutKafka_throwsPeerException]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_sourceLogWithoutKafka_throwsPeerException() throws Exception {
    // Given: Main instance with sourceLog set to a Kafka topic (non-Chronicle)
    //        but kafkaServers is null
    // When: validateInput() is called
    // Then: fatalExit is triggered with ERROR_NO_KAFKA_SERVERS_GIVEN
    //       (use ExitTrappingSecurityManager pattern from MainErrorHandlingTest)

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that zmq-rpc=auto finds an available port and sets the property.
   *
   * <p>Acceptance criterion: [TEST:MainValidatePropsTest.validate_zmqRpcAuto_findsAvailablePort]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_zmqRpcAuto_findsAvailablePort() throws Exception {
    // Given: Main instance with zmqRpc set to "auto"
    // When: validateInput() and addMiscProperties() are called
    // Then: properties "in.zmq.rpc" should contain "tcp://localhost:" with a valid port
    //       and runOptions should contain WITH_ZMQ_RPC

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that json-rpc=auto finds an available port and sets the property.
   *
   * <p>Acceptance criterion: [TEST:MainValidatePropsTest.validate_jsonRpcAuto_findsAvailablePort]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_jsonRpcAuto_findsAvailablePort() throws Exception {
    // Given: Main instance with jsonRpc set to "auto"
    // When: validateInput() and addMiscProperties() are called
    // Then: properties "in.json.rpc" should contain "ws://localhost:" with a valid port
    //       and runOptions should contain WITH_JSON_RPC

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that --interceptable without -d (directory) logs a warning and does not add
   * WITH_INTERCEPTS to runOptions.
   *
   * <p>Acceptance criterion:
   * [TEST:MainValidatePropsTest.validate_interceptsWithoutDirectory_throwsException]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_interceptsWithoutDirectory_throwsException() throws Exception {
    // Given: Main instance with interceptable=true but palDirectoryUrl=null
    // When: validateInput() is called
    // Then: runOptions should NOT contain WITH_INTERCEPTS
    //       (warning is logged to stderr about --interceptable without directory)

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that both WAL and source log can be configured together.
   *
   * <p>Acceptance criterion: [TEST:MainValidatePropsTest.validate_walAndSourceLog_bothSet]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_walAndSourceLog_bothSet() throws Exception {
    // Given: Main instance with both wal and sourceLog set to Chronicle paths
    //        (no kafkaServers needed for Chronicle)
    // When: validateInput() and addMiscProperties() are called
    // Then: runOptions should contain both WITH_WAL and WITH_SOURCE_LOG
    //       properties should contain both "wal.type" and "source_log.type"

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that the -d flag sets the paldir URL and adds WITH_PALDIR to runOptions.
   *
   * <p>Acceptance criterion: [TEST:MainValidatePropsTest.validate_palDirUrl_setsRunOption]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_palDirUrl_setsRunOption() throws Exception {
    // Given: Main instance with palDirectoryUrl set to "localhost:2379"
    // When: validateInput() is called
    // Then: runOptions should contain WITH_PALDIR
    //       properties "paldir_url" should be "localhost:2379" (after addMiscProperties)

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that enabling sessions (via RPC) sets WITH_SESSIONS in runOptions.
   *
   * <p>Acceptance criterion: [TEST:MainValidatePropsTest.validate_sessionsEnabled_setsRunOption]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void validate_sessionsEnabled_setsRunOption() throws Exception {
    // Given: Main instance with zmqRpc set (which triggers session enabling)
    // When: validateInput() is called
    // Then: runOptions should contain WITH_SESSIONS

    // TODO(#634): Implement test logic
    fail("Not yet implemented");
  }
}
