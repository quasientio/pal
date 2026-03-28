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
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
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

  /** Saved value of cli.logging system property to restore after tests. */
  private String savedCliLogging;

  /** Temporary file used for custom logging configuration tests. */
  private File tempLogFile;

  /** Sets up test fixtures. Saves current cli.logging property if set. */
  @Before
  public void setUp() {
    savedCliLogging = System.getProperty("cli.logging");
  }

  /** Tears down test fixtures. Restores cli.logging property and deletes temp files. */
  @After
  public void tearDown() {
    if (savedCliLogging != null) {
      System.setProperty("cli.logging", savedCliLogging);
    } else {
      System.clearProperty("cli.logging");
    }
    if (tempLogFile != null && tempLogFile.exists()) {
      tempLogFile.delete();
    }
  }

  /**
   * Test subclass for testing AbstractPalSubcommand methods.
   *
   * <p>This class provides a minimal implementation for testing protected/package-private methods.
   */
  private static class TestableCmd extends AbstractPalSubcommand {
    /** Tracks whether validateInput was called. */
    boolean validateCalled = false;

    /** Tracks whether initialize was called. */
    boolean initializeCalled = false;

    /** Tracks whether runCommand was called. */
    boolean runCommandCalled = false;

    @Override
    protected void validateInput() {
      validateCalled = true;
    }

    @Override
    protected void initialize() {
      initializeCalled = true;
    }

    @Override
    protected int runCommand() {
      runCommandCalled = true;
      return 0;
    }
  }

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

  /**
   * Tests that getKafkaServers returns the environment variable value when set.
   *
   * <p>Note: This test behavior depends on the actual KAFKA_SERVERS environment variable. If
   * KAFKA_SERVERS is set in the environment, the test verifies it returns that value. If not set,
   * the test documents this assumption and verifies null is returned.
   */
  @Test
  public void testGetKafkaServers_returnsEnvValue_whenSet() throws Exception {
    // Given: KAFKA_SERVERS env var state is determined by the runtime environment
    // When: getKafkaServers() called via reflection (it's protected static)
    Method getKafkaServersMethod = AbstractPalSubcommand.class.getDeclaredMethod("getKafkaServers");
    getKafkaServersMethod.setAccessible(true);

    String result = (String) getKafkaServersMethod.invoke(null);

    // Then: Returns the env var value if set, or null if not set
    String envValue = System.getenv("KAFKA_SERVERS");
    if (envValue != null && !envValue.isEmpty()) {
      assertThat(result, is(envValue));
    } else {
      // Document assumption: KAFKA_SERVERS is not set in this test environment
      assertThat(result, is((String) null));
    }
  }

  /**
   * Tests that getKafkaServers returns null when the environment variable is not set.
   *
   * <p>Note: This test assumes KAFKA_SERVERS is not set. If run in an environment where
   * KAFKA_SERVERS is set, the test verifies the non-null path instead.
   */
  @Test
  public void testGetKafkaServers_returnsNull_whenEnvNotSet() throws Exception {
    // Given: KAFKA_SERVERS env var state - this test documents behavior in both scenarios
    // When: getKafkaServers() called via reflection
    Method getKafkaServersMethod = AbstractPalSubcommand.class.getDeclaredMethod("getKafkaServers");
    getKafkaServersMethod.setAccessible(true);

    String result = (String) getKafkaServersMethod.invoke(null);

    // Then: Returns null if env var not set, otherwise returns the env value
    String envValue = System.getenv("KAFKA_SERVERS");
    if (envValue == null) {
      assertThat(result, is((String) null));
    } else if (envValue.isEmpty()) {
      // Empty env var should also return null
      assertThat(result, is((String) null));
    } else {
      // Env var is set, document this and verify behavior
      assertThat(result, is(envValue));
    }
  }

  /**
   * Tests that getKafkaServers returns null when the environment variable is empty.
   *
   * <p>Note: Environment variables cannot be easily set to empty in Java tests. This test verifies
   * the logic by checking the actual implementation handles empty strings correctly. If
   * KAFKA_SERVERS is set to a non-empty value, the test documents this.
   */
  @Test
  public void testGetKafkaServers_returnsNull_whenEnvEmpty() throws Exception {
    // Given: KAFKA_SERVERS env var state
    // When: getKafkaServers() called via reflection
    Method getKafkaServersMethod = AbstractPalSubcommand.class.getDeclaredMethod("getKafkaServers");
    getKafkaServersMethod.setAccessible(true);

    String result = (String) getKafkaServersMethod.invoke(null);

    // Then: Verify the method's behavior based on actual env state
    // The implementation returns null for null or empty env vars
    String envValue = System.getenv("KAFKA_SERVERS");
    if (envValue == null || envValue.isEmpty()) {
      assertThat(result, is((String) null));
    } else {
      // When env var is set to non-empty value, it returns that value
      assertThat(result, is(envValue));
    }
  }

  // ==================== optionGiven() tests ====================

  /**
   * Tests that optionGiven returns false when the option is an empty string.
   *
   * <p>Empty strings are considered "not given" by the optionGiven method.
   */
  @Test
  public void testOptionGiven_returnsFalse_whenEmpty() throws Exception {
    // Given: option = ""
    Method optionGivenMethod =
        AbstractPalSubcommand.class.getDeclaredMethod("optionGiven", String.class);
    optionGivenMethod.setAccessible(true);

    // When: optionGiven(option) called
    boolean result = (boolean) optionGivenMethod.invoke(null, "");

    // Then: Returns false
    assertThat(result, is(false));
  }

  /**
   * Tests that optionGiven returns false when the option is null.
   *
   * <p>Null options are considered "not given" by the optionGiven method.
   */
  @Test
  public void testOptionGiven_returnsFalse_whenNull() throws Exception {
    // Given: option = null
    Method optionGivenMethod =
        AbstractPalSubcommand.class.getDeclaredMethod("optionGiven", String.class);
    optionGivenMethod.setAccessible(true);

    // When: optionGiven(option) called
    boolean result = (boolean) optionGivenMethod.invoke(null, (String) null);

    // Then: Returns false
    assertThat(result, is(false));
  }

  /**
   * Tests that optionGiven returns true when the option is a non-empty string.
   *
   * <p>Non-empty strings are considered "given" by the optionGiven method.
   */
  @Test
  public void testOptionGiven_returnsTrue_whenNonEmpty() throws Exception {
    // Given: option = "value"
    Method optionGivenMethod =
        AbstractPalSubcommand.class.getDeclaredMethod("optionGiven", String.class);
    optionGivenMethod.setAccessible(true);

    // When: optionGiven(option) called
    boolean result = (boolean) optionGivenMethod.invoke(null, "value");

    // Then: Returns true
    assertThat(result, is(true));
  }

  // ==================== configureLogging() tests ====================

  /**
   * Tests that configureLogging uses the default configuration when no cli.logging property is set.
   *
   * <p>The default configuration is loaded from the classpath resource /cli-logging-fallback.xml.
   * This test verifies that call() completes successfully when no custom config is specified.
   */
  @Test
  public void testConfigureLogging_usesDefaultConfig_whenNoPropertySet() throws Exception {
    // Given: cli.logging system property not set
    System.clearProperty("cli.logging");

    // Create a TestableCmd which will go through configureLogging() via call()
    TestableCmd cmd = new TestableCmd();

    // Inject the directoryConnectionProvider to avoid NPE in closeResources()
    Field dcpField = AbstractPalSubcommand.class.getDeclaredField("directoryConnectionProvider");
    dcpField.setAccessible(true);
    dcpField.set(cmd, new DirectoryConnectionProvider(PalDirectory.NO_URL));

    // When: call() is invoked (which calls configureLogging() internally)
    int result = cmd.call();

    // Then: Command executes successfully with default config (no exception thrown)
    assertThat(result, is(0));
    assertThat(cmd.validateCalled, is(true));
    assertThat(cmd.initializeCalled, is(true));
    assertThat(cmd.runCommandCalled, is(true));
  }

  /**
   * Tests that configureLogging uses a custom configuration file when cli.logging property is set.
   *
   * <p>A temporary file with valid Logback XML configuration is created and its path is set as the
   * cli.logging system property.
   */
  @Test
  public void testConfigureLogging_usesCustomFile_whenPropertySet() throws Exception {
    // Given: cli.logging system property set to existing file path
    tempLogFile = File.createTempFile("test-logback-", ".xml");
    String validLogbackConfig =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <configuration>
          <root level="WARN"/>
        </configuration>
        """;
    Files.writeString(tempLogFile.toPath(), validLogbackConfig);
    System.setProperty("cli.logging", tempLogFile.getAbsolutePath());

    // Create a TestableCmd which will go through configureLogging() via call()
    TestableCmd cmd = new TestableCmd();

    // Inject the directoryConnectionProvider to avoid NPE in closeResources()
    Field dcpField = AbstractPalSubcommand.class.getDeclaredField("directoryConnectionProvider");
    dcpField.setAccessible(true);
    dcpField.set(cmd, new DirectoryConnectionProvider(PalDirectory.NO_URL));

    // When: call() is invoked (which calls configureLogging() internally)
    int result = cmd.call();

    // Then: Command executes successfully with custom config loaded
    assertThat(result, is(0));
    assertThat(cmd.validateCalled, is(true));
    assertThat(cmd.initializeCalled, is(true));
    assertThat(cmd.runCommandCalled, is(true));
  }

  /**
   * Tests that configureLogging falls back to default when cli.logging points to non-existent file.
   *
   * <p>When the specified configuration file does not exist, the method should fall back to the
   * default classpath configuration without failing.
   */
  @Test
  public void testConfigureLogging_fallsBackToDefault_whenFileNotExists() throws Exception {
    // Given: cli.logging system property set to non-existent file
    System.setProperty("cli.logging", "/non/existent/path/logback-config.xml");

    // Create a TestableCmd which will go through configureLogging() via call()
    TestableCmd cmd = new TestableCmd();

    // Inject the directoryConnectionProvider to avoid NPE in closeResources()
    Field dcpField = AbstractPalSubcommand.class.getDeclaredField("directoryConnectionProvider");
    dcpField.setAccessible(true);
    dcpField.set(cmd, new DirectoryConnectionProvider(PalDirectory.NO_URL));

    // When: call() is invoked (which calls configureLogging() internally)
    int result = cmd.call();

    // Then: Command executes successfully with fallback to default config
    assertThat(result, is(0));
    assertThat(cmd.validateCalled, is(true));
    assertThat(cmd.initializeCalled, is(true));
    assertThat(cmd.runCommandCalled, is(true));
  }

  // ==================== getPalDirectory() tests ====================

  /**
   * Tests that getPalDirectory throws RuntimeException when the directory is not configured.
   *
   * <p>When the DirectoryConnectionProvider is initialized with PalDirectory.NO_URL, it returns an
   * empty Optional, causing getPalDirectory() to throw a RuntimeException with a helpful message.
   */
  @Test
  public void testGetPalDirectory_throwsWhenDirectoryNotConfigured() throws Exception {
    // Given: directoryConnectionProvider configured to return empty Optional
    TestableCmd cmd = new TestableCmd();
    Field dcpField = AbstractPalSubcommand.class.getDeclaredField("directoryConnectionProvider");
    dcpField.setAccessible(true);
    dcpField.set(cmd, new DirectoryConnectionProvider(PalDirectory.NO_URL));

    // When: getPalDirectory() called
    RuntimeException thrown = null;
    try {
      cmd.getPalDirectory();
    } catch (RuntimeException e) {
      thrown = e;
    }

    // Then: RuntimeException thrown with message about -d option
    assertThat(thrown != null, is(true));
    assertThat(thrown.getMessage(), containsString("-d (--dir)"));
    assertThat(thrown.getMessage(), containsString("PAL_DIRECTORY"));
  }
}
