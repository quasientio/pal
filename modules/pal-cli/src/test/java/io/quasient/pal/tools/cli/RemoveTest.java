/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;

public class RemoveTest {

  @Test
  public void runCommand_withoutFlags_printsUsageAndReturnsOne() throws Exception {
    Remove rm = new Remove();
    // inject palCommand returning NO_URL
    var pc = Pal.class.getDeclaredConstructor();
    pc.setAccessible(true);
    Pal pal = pc.newInstance();
    var palUrl = Pal.class.getDeclaredField("palDirectoryUrl");
    palUrl.setAccessible(true);
    palUrl.set(pal, PalDirectory.NO_URL);
    var parent = Remove.class.getDeclaredField("palCommand");
    parent.setAccessible(true);
    parent.set(rm, pal);

    // wire err/out to capture
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    var outF = AbstractPalSubcommand.class.getDeclaredField("out");
    outF.setAccessible(true);
    outF.set(rm, new PrintStream(bout));

    // go through call() pipeline
    int code = rm.call();
    assertThat(code, is(1));
    assertThat(bout.toString(UTF_8), containsString("Use -L/--logs to remove logs"));
  }

  // ===========================================================================
  // Tests for resolveLogInfo() - Issue #368, #369
  // ===========================================================================

  /**
   * Tests that resolveLogInfo() correctly parses a Chronicle log path with "file:" prefix.
   *
   * <p>Verifies that the "file:" prefix is stripped and a LogInfo with CHRONICLE type is returned.
   */
  @Test
  public void testResolveLogInfo_chronicleWithFilePrefix() throws Exception {
    // Given: logNameOrPath = "file:/tmp/mylog"
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "file:/tmp/mylog");

    // Then: Returns LogInfo with:
    //       - CHRONICLE type
    //       - path "/tmp/mylog" (prefix stripped)
    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(result.getName(), is("/tmp/mylog"));
  }

  /**
   * Tests that resolveLogInfo() correctly creates a Kafka LogInfo when bootstrap servers are set.
   *
   * <p>Verifies that a Kafka-type LogInfo is returned with the provided bootstrap servers.
   */
  @Test
  public void testResolveLogInfo_kafkaWithBootstrapServers() throws Exception {
    // Given: logNameOrPath = "my-topic"
    //        kafkaServers field set to "localhost:29092"
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Set kafkaServers field via reflection
    Field kafkaServersField = Remove.class.getDeclaredField("kafkaServers");
    kafkaServersField.setAccessible(true);
    kafkaServersField.set(rm, "localhost:29092");

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "my-topic");

    // Then: Returns LogInfo with:
    //       - KAFKA type
    //       - name "my-topic"
    //       - bootstrap servers "localhost:29092"
    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.KAFKA));
    assertThat(result.getName(), is("my-topic"));
    assertThat(result.getBootstrapServers(), is("localhost:29092"));
  }

  /**
   * Tests that resolveLogInfo() returns null and logs error when Kafka servers are not available.
   *
   * <p>Verifies that when no "file:" prefix is present and no Kafka servers are configured (neither
   * via field nor KAFKA_SERVERS env var), the method returns null.
   *
   * <p>Note: This test assumes KAFKA_SERVERS environment variable is not set. If the test is run in
   * an environment where KAFKA_SERVERS is set, the test may fail. In CI/CD pipelines, ensure this
   * env var is not set when running unit tests.
   */
  @Test
  public void testResolveLogInfo_failsWithoutKafkaServers() throws Exception {
    // Given: logNameOrPath = "my-topic" (no "file:" prefix)
    //        kafkaServers field is null
    //        KAFKA_SERVERS environment variable is not set
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Ensure kafkaServers field is null (it should be by default, but be explicit)
    Field kafkaServersField = Remove.class.getDeclaredField("kafkaServers");
    kafkaServersField.setAccessible(true);
    kafkaServersField.set(rm, null);

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    // Note: This test assumes KAFKA_SERVERS env var is not set. If it is set,
    // the method will return a valid LogInfo instead of null.
    String kafkaServersEnv = System.getenv("KAFKA_SERVERS");
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "my-topic");

    // Then: Returns null if KAFKA_SERVERS is not set
    //       Logs error message about missing Kafka servers
    if (kafkaServersEnv == null || kafkaServersEnv.isEmpty()) {
      assertThat(result, is(nullValue()));
    } else {
      // If KAFKA_SERVERS is set, the result should be a valid Kafka LogInfo
      // using the environment variable value
      assertThat(result, is(notNullValue()));
      assertThat(result.getLogType(), is(LogType.KAFKA));
      assertThat(result.getName(), is("my-topic"));
      assertThat(result.getBootstrapServers(), is(kafkaServersEnv));
    }
  }
}
