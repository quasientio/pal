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
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Permission;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/** Tests for {@link Main#validateInput()} and {@link Main#addMiscProperties()}. */
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

  // ===== Chronicle log, Kafka requirements, auto ports, intercepts, and more =====

  /** Tests that a Chronicle WAL sets the chronicle log type property. */
  @Test
  public void validate_chronicleLog_setsChronicleLogProperty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "file:/tmp/wal");
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    assertThat(p.getProperty("wal.type"), is("CHRONICLE"));
  }

  /** Tests that a source log without Kafka servers triggers a fatal exit. */
  @Test
  @SuppressWarnings("removal")
  public void validate_sourceLogWithoutKafka_throwsPeerException() throws Exception {
    SecurityManager original = System.getSecurityManager();
    System.setSecurityManager(new ExitTrappingSecurityManager());
    try {
      Main m = new Main();
      setField(m, "uuid", UUID.randomUUID());
      setField(m, "sourceLog", "my-kafka-topic");
      // kafkaServers is null by default — should trigger fatalExit with code 6
      Method validate = Main.class.getDeclaredMethod("validateInput");
      validate.setAccessible(true);
      int exitCode = -1;
      try {
        validate.invoke(m);
      } catch (java.lang.reflect.InvocationTargetException e) {
        if (e.getCause() instanceof ExitTrappedException) {
          exitCode = ((ExitTrappedException) e.getCause()).getExitCode();
        } else {
          throw e;
        }
      }
      assertThat(exitCode, is(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode()));
    } finally {
      System.setSecurityManager(original);
    }
  }

  /** Tests that zmq-rpc=auto finds an available port. */
  @Test
  public void validate_zmqRpcAuto_findsAvailablePort() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "zmqRpc", "auto");
    setField(m, "rpcThreads", 1);
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String zmqRpcAddr = p.getProperty("in.zmq.rpc");
    assertThat(zmqRpcAddr, containsString("tcp://localhost:"));
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_ZMQ_RPC), is(true));
  }

  /** Tests that json-rpc=auto finds an available port. */
  @Test
  public void validate_jsonRpcAuto_findsAvailablePort() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "jsonRpc", "auto");
    setField(m, "rpcThreads", 1);
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    String jsonRpcAddr = p.getProperty("in.json.rpc");
    assertThat(jsonRpcAddr, containsString("ws://localhost:"));
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_JSON_RPC), is(true));
  }

  /** Tests that --interceptable without a directory does not enable intercepts. */
  @Test
  public void validate_interceptsWithoutDirectory_throwsException() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "interceptable", true);
    // palDirectoryUrl is null — so WITH_INTERCEPTS should NOT be added
    callValidateInput(m);
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_INTERCEPTS), is(false));
  }

  /** Tests that WAL and source log can both be set as Chronicle. */
  @Test
  public void validate_walAndSourceLog_bothSet() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "sourceLog", "file:/tmp/source");
    callValidateInput(m);
    Properties p = (Properties) getField(m, "properties");
    assertThat(p.getProperty("wal.type"), is("CHRONICLE"));
    assertThat(p.getProperty("source_log.type"), is("CHRONICLE"));
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_WAL), is(true));
    assertThat(ro.contains(RunOptions.WITH_SOURCE_LOG), is(true));
  }

  /** Tests that the -d flag sets the paldir URL and run option. */
  @Test
  public void validate_palDirUrl_setsRunOption() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "palDirectoryUrl", "localhost:2379");
    callValidateInput(m);
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_PALDIR), is(true));
    Properties p = (Properties) getField(m, "properties");
    assertThat(p.getProperty("paldir_url"), is("localhost:2379"));
  }

  /** Tests that enabling RPC enables sessions in runOptions. */
  @Test
  public void validate_sessionsEnabled_setsRunOption() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "zmqRpc", "auto");
    setField(m, "rpcThreads", 1);
    callValidateInput(m);
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro.contains(RunOptions.WITH_SESSIONS), is(true));
  }

  /** Tests that runOptions is populated after validation. */
  @Test
  public void validate_defaultRunOptions_isNonEmpty() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    callValidateInput(m);
    @SuppressWarnings("unchecked")
    Set<RunOptions> ro = (Set<RunOptions>) getField(m, "runOptions");
    assertThat(ro, is(notNullValue()));
  }

  // ===== Helper classes for System.exit() trapping =====

  /** Exception thrown when System.exit() is called during testing. */
  private static class ExitTrappedException extends SecurityException {
    private final int exitCode;

    ExitTrappedException(int exitCode) {
      super("System.exit(" + exitCode + ") was trapped");
      this.exitCode = exitCode;
    }

    int getExitCode() {
      return exitCode;
    }
  }

  /** SecurityManager that traps System.exit() calls. */
  @SuppressWarnings("removal")
  private static class ExitTrappingSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
      // Allow all
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      // Allow all
    }

    @Override
    public void checkExit(int status) {
      throw new ExitTrappedException(status);
    }
  }
}
