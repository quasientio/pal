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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import io.quasient.pal.common.cli.PalCommand;
import java.lang.reflect.Field;
import java.security.Permission;
import java.util.Properties;
import org.junit.After;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/** Tests for {@link Main#call()}. */
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

  // ===== Tests for JAR handling and classpath =====

  /** Tests that a non-existent JAR file triggers a fatal exit with code 9. */
  @Test
  @SuppressWarnings("removal")
  public void call_withJarFile_nonExistentJar_throwsPeerException() throws Exception {
    SecurityManager original = System.getSecurityManager();
    System.setSecurityManager(
        new SecurityManager() {
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
            throw new SecurityException("exit(" + status + ")");
          }
        });
    try {
      Main app = new Main();
      CommandLine cl = new CommandLine(app);
      cl.parseArgs("-jar", "/nonexistent/path/fake.jar");
      setParentCommandToEmpty(app);
      try {
        app.call();
        fail("Expected SecurityException from System.exit() trap");
      } catch (SecurityException e) {
        assertThat(
            e.getMessage(),
            is(
                "exit("
                    + PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getCode()
                    + ")"));
      }
    } finally {
      System.setSecurityManager(original);
    }
  }

  /** Tests that -cp flag creates a custom classloader for the specified path. */
  @Test
  public void call_withCustomClasspath_setsClassloader() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    cl.parseArgs("-cp", "target/test-classes", "io.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(0));
    Field f = Main.class.getDeclaredField("customClassloader");
    f.setAccessible(true);
    assertThat(f.get(app), is(notNullValue()));
  }

  /** Tests that call() sets the out.pub property. */
  @Test
  public void call_setsOutPubProperty() throws Exception {
    Main app = new Main();
    CommandLine cl = new CommandLine(app);
    cl.parseArgs("io.quasient.pal.core.service.testdata.DummyMain");
    setParentCommandToEmpty(app);
    int code = app.call();
    assertThat(code, is(0));
    Field f = Main.class.getDeclaredField("properties");
    f.setAccessible(true);
    Properties p = (Properties) f.get(app);
    assertThat(p.getProperty("out.pub"), is(notNullValue()));
  }
}
