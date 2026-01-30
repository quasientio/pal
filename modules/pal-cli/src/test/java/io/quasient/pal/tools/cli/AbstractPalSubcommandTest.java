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
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractPalSubcommand}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Kafka servers environment variable lookup via {@link AbstractPalSubcommand#getKafkaServers}
 *   <li>Option string validation via {@link AbstractPalSubcommand#optionGiven}
 *   <li>Logging configuration behavior via configureLogging (private, tested indirectly)
 *   <li>PAL directory retrieval via {@link AbstractPalSubcommand#getPalDirectory}
 * </ul>
 */
public class AbstractPalSubcommandTest {

  private static class FailingCmd extends AbstractPalSubcommand {
    @Override
    protected void validateInput() {
      throw new RuntimeException("bad input");
    }

    @Override
    protected void initialize() {}

    @Override
    protected int runCommand() {
      return 0;
    }
  }

  @Test
  public void call_whenValidateFails_printsErrorAndReturnsOne() throws Exception {
    FailingCmd cmd = new FailingCmd();
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    // inject custom err stream
    var f = AbstractPalSubcommand.class.getDeclaredField("err");
    f.setAccessible(true);
    f.set(cmd, new PrintStream(err));

    int code = cmd.call();
    assertThat(code, is(1));
    assertThat(err.toString(UTF_8), containsString("bad input"));
  }

  // ==================== getKafkaServers() tests ====================

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testGetKafkaServers_returnsEnvValue_whenSet() {
    // Given: KAFKA_SERVERS env var set to "localhost:29092"
    // When: getKafkaServers() called
    // Then: Returns "localhost:29092"

    // TODO(#361): Implement - requires mocking or test helper for System.getenv()
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testGetKafkaServers_returnsNull_whenEnvNotSet() {
    // Given: KAFKA_SERVERS env var not set
    // When: getKafkaServers() called
    // Then: Returns null

    // TODO(#361): Implement - requires mocking or test helper for System.getenv()
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testGetKafkaServers_returnsNull_whenEnvEmpty() {
    // Given: KAFKA_SERVERS env var set to empty string
    // When: getKafkaServers() called
    // Then: Returns null

    // TODO(#361): Implement - requires mocking or test helper for System.getenv()
    fail("Not yet implemented");
  }

  // ==================== optionGiven() tests ====================

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testOptionGiven_returnsFalse_whenEmpty() {
    // Given: option = ""
    // When: optionGiven(option) called
    // Then: Returns false

    // TODO(#361): Implement - call via reflection or test subclass
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testOptionGiven_returnsFalse_whenNull() {
    // Given: option = null
    // When: optionGiven(option) called
    // Then: Returns false

    // TODO(#361): Implement
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testOptionGiven_returnsTrue_whenNonEmpty() {
    // Given: option = "value"
    // When: optionGiven(option) called
    // Then: Returns true

    // TODO(#361): Implement
    fail("Not yet implemented");
  }

  // ==================== configureLogging() tests ====================

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testConfigureLogging_usesDefaultConfig_whenNoPropertySet() {
    // Given: cli.logging system property not set
    // When: configureLogging() called via call()
    // Then: Default logging configuration loaded from classpath

    // TODO(#361): Implement - test indirectly via call(), verify default config loaded
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testConfigureLogging_usesCustomFile_whenPropertySet() {
    // Given: cli.logging system property set to existing file path
    // When: configureLogging() called
    // Then: Custom logging configuration loaded

    // TODO(#361): Implement - create temp file with valid logback config
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testConfigureLogging_fallsBackToDefault_whenFileNotExists() {
    // Given: cli.logging system property set to non-existent file
    // When: configureLogging() called
    // Then: Falls back to default configuration

    // TODO(#361): Implement - set cli.logging to non-existent path, verify fallback
    fail("Not yet implemented");
  }

  // ==================== getPalDirectory() tests ====================

  @Test
  @Ignore("Awaiting implementation in #361")
  public void testGetPalDirectory_throwsWhenDirectoryNotConfigured() {
    // Given: directoryConnectionProvider returns empty Optional
    // When: getPalDirectory() called
    // Then: RuntimeException thrown with message about -d option

    // TODO(#361): Implement - mock directoryConnectionProvider returning Optional.empty()
    fail("Not yet implemented");
  }
}
