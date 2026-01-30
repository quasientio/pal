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

import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Ignore;
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
  // Test specifications for resolveLogInfo() - Issue #368
  // ===========================================================================

  /**
   * Tests that resolveLogInfo() correctly parses a Chronicle log path with "file:" prefix.
   *
   * <p>Verifies that the "file:" prefix is stripped and a LogInfo with CHRONICLE type is returned.
   */
  @Test
  @Ignore("Awaiting implementation in #369")
  public void testResolveLogInfo_chronicleWithFilePrefix() throws Exception {
    // Given: logNameOrPath = "file:/tmp/mylog"
    //        Remove instance with no PAL directory connection

    // When: resolveLogInfo() called via reflection

    // Then: Returns LogInfo with:
    //       - CHRONICLE type
    //       - path "/tmp/mylog" (prefix stripped)

    // TODO(#369): Implement after #369 provides the implementation
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that resolveLogInfo() correctly creates a Kafka LogInfo when bootstrap servers are set.
   *
   * <p>Verifies that a Kafka-type LogInfo is returned with the provided bootstrap servers.
   */
  @Test
  @Ignore("Awaiting implementation in #369")
  public void testResolveLogInfo_kafkaWithBootstrapServers() throws Exception {
    // Given: logNameOrPath = "my-topic"
    //        kafkaServers field set to "localhost:29092"
    //        Remove instance with no PAL directory connection

    // When: resolveLogInfo() called via reflection

    // Then: Returns LogInfo with:
    //       - KAFKA type
    //       - name "my-topic"
    //       - bootstrap servers "localhost:29092"

    // TODO(#369): Implement after #369 provides the implementation
    throw new AssertionError("Not yet implemented");
  }

  /**
   * Tests that resolveLogInfo() returns null and logs error when Kafka servers are not available.
   *
   * <p>Verifies that when no "file:" prefix is present and no Kafka servers are configured (neither
   * via field nor KAFKA_SERVERS env var), the method returns null.
   */
  @Test
  @Ignore("Awaiting implementation in #369")
  public void testResolveLogInfo_failsWithoutKafkaServers() throws Exception {
    // Given: logNameOrPath = "my-topic" (no "file:" prefix)
    //        kafkaServers field is null
    //        KAFKA_SERVERS environment variable is not set
    //        Remove instance with no PAL directory connection

    // When: resolveLogInfo() called via reflection

    // Then: Returns null
    //       Logs error message about missing Kafka servers

    // TODO(#369): Implement after #369 provides the implementation
    throw new AssertionError("Not yet implemented");
  }
}
