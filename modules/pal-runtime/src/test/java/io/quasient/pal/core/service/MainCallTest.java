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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import io.quasient.pal.common.cli.PalCommand;
import java.lang.reflect.Field;
import java.util.Properties;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class MainCallTest {

  @After
  public void resetLogback() {
    // Reset Logback to prevent state pollution across tests
    // Main.call() reconfigures Logback, which affects subsequent tests
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.reset();
    // Reinitialize with default configuration
    ContextInitializer ci = new ContextInitializer(context);
    try {
      ci.autoConfig();
    } catch (Exception e) {
      // If auto-config fails, just leave it reset
      // This is better than polluting other tests
    }
  }

  @Test
  public void call_withDummyMainClass_returnsDefaultExit() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    // Provide only the target class; no services enabled
    cl.parseArgs("io.quasient.pal.core.service.testdata.DummyMain");
    // Avoid env-provided <pal_directory> by overriding parent PalCommand to return empty
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(0));
  }

  @Test
  public void call_setsDefaultQueueProperties() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    cl.parseArgs("io.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(0));

    // Reflect properties and assert some defaults populated by validateProperties
    Field f = Main.class.getDeclaredField("properties");
    f.setAccessible(true);
    Properties p = (Properties) f.get(app);
    // Defaults from Main: wal/pub queues and pub spsc size
    assertNotNull(p.getProperty("wal.queue.initial"));
    assertNotNull(p.getProperty("wal.queue.chunk"));
    assertNotNull(p.getProperty("wal.queue.max"));
    assertNotNull(p.getProperty("pub.queue.initial"));
    assertNotNull(p.getProperty("pub.queue.chunk"));
    assertNotNull(p.getProperty("pub.queue.max"));
    assertNotNull(p.getProperty("pub.spsc_size"));
  }

  @Test
  public void call_withVoidMainMethod_returnsZero() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    // DummyMain has a void main() method that does nothing
    cl.parseArgs("io.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);

    int code = app.call();

    assertThat("Void main() should return 0 (EXIT_SUCCESS)", code, is(0));
  }

  @Test
  public void call_withMainThatThrows_returnsOne() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    // MainThatThrows throws a RuntimeException
    cl.parseArgs("io.quasient.pal.core.service.testdata.MainThatThrows");
    setParentCommandToEmpty(app);

    int code = app.call();

    assertThat(
        "Main that throws exception should return 1 (EXIT_MAIN_THREW_EXCEPTION)", code, is(1));
  }

  @Test
  public void call_withNonExistentClass_returnsOne() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    // Non-existent class should trigger ClassNotFoundException handling
    cl.parseArgs("io.quasient.pal.NonExistentClass");
    setParentCommandToEmpty(app);

    int code = app.call();

    assertThat("Non-existent class should return 1", code, is(1));
  }

  private static void setParentCommandToEmpty(Main app) throws Exception {
    PalCommand dummy = () -> ""; // empty string forces Main to treat as no paldir
    Field pf = Main.class.getDeclaredField("palCommand");
    pf.setAccessible(true);
    pf.set(app, dummy);
  }

  // ===== Test stubs for #633 (awaiting implementation in #634) =====

  /**
   * Tests that specifying a non-existent JAR file results in exit code 9
   * (ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST).
   *
   * <p>Acceptance criterion:
   * [TEST:MainCallTest.call_withJarFile_nonExistentJar_throwsPeerException]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void call_withJarFile_nonExistentJar_throwsPeerException() throws Exception {
    // Given: Main instance configured with -jar pointing to a non-existent JAR file
    // When: call() is invoked
    // Then: The call should result in a PeerException with
    //       FatalCode ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST (exit code 9)

    // TODO(#634): Implement test logic
    // Hint: Use CommandLine.parseArgs("-jar", "/nonexistent/path/fake.jar")
    //       and setParentCommandToEmpty(app), then call app.call()
    //       Verify via exit code or ExitTrappingSecurityManager pattern
    fail("Not yet implemented");
  }

  /**
   * Tests that the -cp flag creates a classloader with the specified classpath entries.
   *
   * <p>Acceptance criterion: [TEST:MainCallTest.call_withCustomClasspath_setsClassloader]
   */
  @Test
  @Ignore("Awaiting implementation in #634")
  public void call_withCustomClasspath_setsClassloader() throws Exception {
    // Given: Main instance configured with -cp pointing to a valid directory
    // When: call() is invoked with a valid main class
    // Then: The customClassloader field should be non-null and configured
    //       with the specified classpath entries

    // TODO(#634): Implement test logic
    // Hint: Use CommandLine.parseArgs("-cp", "/some/path",
    //       "io.quasient.pal.core.service.testdata.DummyMain")
    //       and setParentCommandToEmpty(app), then call app.call()
    //       Reflect the "customClassloader" field to verify it's set
    fail("Not yet implemented");
  }
}
