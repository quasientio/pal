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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import io.quasient.pal.core.execution.java.CustomClassloader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for private utility methods in {@link Main}.
 *
 * <p>These tests use reflection to access private static methods that perform simple logic
 * transformations without side effects.
 */
public class MainUtilityMethodsTest {

  private Method isChronicleLogMethod;
  private Method extractLogNameMethod;

  @Before
  public void setUp() throws Exception {
    isChronicleLogMethod = Main.class.getDeclaredMethod("isChronicleLog", String.class);
    isChronicleLogMethod.setAccessible(true);

    extractLogNameMethod = Main.class.getDeclaredMethod("extractLogName", String.class);
    extractLogNameMethod.setAccessible(true);
  }

  // ===== Tests for isChronicleLog() =====

  @Test
  public void isChronicleLog_fileColonPrefix_returnsTrue() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "file:/tmp/mylog");
    assertThat(result, is(true));
  }

  @Test
  public void isChronicleLog_fileColonRelativePath_returnsTrue() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "file:mylog");
    assertThat(result, is(true));
  }

  @Test
  public void isChronicleLog_fileColonOnly_returnsTrue() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "file:");
    assertThat(result, is(true));
  }

  @Test
  public void isChronicleLog_kafkaTopic_returnsFalse() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "my-kafka-topic");
    assertThat(result, is(false));
  }

  @Test
  public void isChronicleLog_emptyString_returnsFalse() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "");
    assertThat(result, is(false));
  }

  @Test
  public void isChronicleLog_null_returnsFalse() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, (Object) null);
    assertThat(result, is(false));
  }

  @Test
  public void isChronicleLog_fileWithoutColon_returnsFalse() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "file");
    assertThat(result, is(false));
  }

  @Test
  public void isChronicleLog_upperCaseFile_returnsFalse() throws Exception {
    // "FILE:" is not recognized - case sensitive
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "FILE:/tmp/log");
    assertThat(result, is(false));
  }

  @Test
  public void isChronicleLog_fileColonInMiddle_returnsFalse() throws Exception {
    boolean result = (boolean) isChronicleLogMethod.invoke(null, "mylog-file:/path");
    assertThat(result, is(false));
  }

  // ===== Tests for extractLogName() =====

  @Test
  public void extractLogName_chronicleAbsolutePath_removesPrefix() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "file:/tmp/mylog");
    assertThat(result, is("/tmp/mylog"));
  }

  @Test
  public void extractLogName_chronicleRelativePath_removesPrefix() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "file:mylog");
    assertThat(result, is("mylog"));
  }

  @Test
  public void extractLogName_chronicleEmptyPath_returnsEmpty() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "file:");
    assertThat(result, is(""));
  }

  @Test
  public void extractLogName_kafkaTopic_returnsUnchanged() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "my-kafka-topic");
    assertThat(result, is("my-kafka-topic"));
  }

  @Test
  public void extractLogName_emptyString_returnsEmpty() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "");
    assertThat(result, is(""));
  }

  @Test
  public void extractLogName_chronicleNestedPath_preservesStructure() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "file:/var/log/pal/myqueue");
    assertThat(result, is("/var/log/pal/myqueue"));
  }

  @Test
  public void extractLogName_kafkaTopicWithDots_returnsUnchanged() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "my.kafka.topic.v1");
    assertThat(result, is("my.kafka.topic.v1"));
  }

  @Test
  public void extractLogName_kafkaTopicWithHyphens_returnsUnchanged() throws Exception {
    String result = (String) extractLogNameMethod.invoke(null, "my-kafka-topic-v1");
    assertThat(result, is("my-kafka-topic-v1"));
  }

  @Test
  public void extractLogName_null_returnsNull() throws Exception {
    // isChronicleLog returns false for null, so extractLogName returns null unchanged
    String result = (String) extractLogNameMethod.invoke(null, (Object) null);
    assertThat(result, nullValue());
  }

  /** Original peer.logging system property value, saved for restoration. */
  private String originalPeerLogging;

  @After
  public void resetLogback() {
    // Restore system property
    if (originalPeerLogging != null) {
      System.setProperty("peer.logging", originalPeerLogging);
    } else {
      System.clearProperty("peer.logging");
    }
    // Reset Logback to prevent state pollution across tests
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.reset();
    ContextInitializer ci = new ContextInitializer(context);
    try {
      ci.autoConfig();
    } catch (Exception e) {
      // If auto-config fails, just leave it reset
    }
  }

  // ===== Tests for createCustomClassloader and initLogging =====

  /** Tests that createCustomClassloader() with a classpath creates the right classloader type. */
  @Test
  public void createCustomClassloader_withClasspath_createsLoader() throws Exception {
    Main m = new Main();
    Field cpField = Main.class.getDeclaredField("classpath");
    cpField.setAccessible(true);
    cpField.set(m, "target/test-classes");

    Method createCcl = Main.class.getDeclaredMethod("createCustomClassloader");
    createCcl.setAccessible(true);
    createCcl.invoke(m);

    Field cclField = Main.class.getDeclaredField("customClassloader");
    cclField.setAccessible(true);
    Object ccl = cclField.get(m);
    assertThat(ccl, is(notNullValue()));
    assertThat(ccl, instanceOf(CustomClassloader.class));
  }

  /** Tests that createCustomClassloader() with null classpath still creates a classloader. */
  @Test
  public void createCustomClassloader_emptyClasspath_usesDefault() throws Exception {
    Main m = new Main();
    // classpath is null by default

    Method createCcl = Main.class.getDeclaredMethod("createCustomClassloader");
    createCcl.setAccessible(true);
    createCcl.invoke(m);

    Field cclField = Main.class.getDeclaredField("customClassloader");
    cclField.setAccessible(true);
    Object ccl = cclField.get(m);
    assertThat(ccl, is(notNullValue()));
    assertThat(ccl, instanceOf(CustomClassloader.class));
  }

  /** Tests that initLogging() applies a custom logback configuration. */
  @Test
  public void initLogging_withCustomLogbackConfig_usesCustomConfig() throws Exception {
    originalPeerLogging = System.getProperty("peer.logging");

    // Create a temp logback config file with a known root level
    Path tmpConfig = Files.createTempFile("logback-test-", ".xml");
    Files.writeString(
        tmpConfig,
        """
        <configuration>
          <root level="WARN"/>
        </configuration>
        """);
    System.setProperty("peer.logging", tmpConfig.toString());

    Main m = new Main();
    Method initLogging = Main.class.getDeclaredMethod("initLogging");
    initLogging.setAccessible(true);
    initLogging.invoke(m);

    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    assertThat(context.getLogger(ROOT_LOGGER_NAME).getLevel(), is(Level.WARN));

    Files.deleteIfExists(tmpConfig);
  }

  /** Tests that initLogging() uses the fallback config when no custom config is set. */
  @Test
  public void initLogging_withoutCustomConfig_usesDefault() throws Exception {
    originalPeerLogging = System.getProperty("peer.logging");
    System.clearProperty("peer.logging");

    PrintStream originalErr = System.err;
    ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));
    try {
      Main m = new Main();
      Method initLogging = Main.class.getDeclaredMethod("initLogging");
      initLogging.setAccessible(true);
      initLogging.invoke(m);

      // The default config sets root level to ERROR
      LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
      assertThat(context.getLogger(ROOT_LOGGER_NAME).getLevel(), is(Level.ERROR));

      // No errors should be printed to stderr
      String errOutput = capturedErr.toString(UTF_8);
      assertThat(errOutput.contains("Error loading logging configuration"), is(false));
    } finally {
      System.setErr(originalErr);
    }
  }

  /** Tests that createCustomClassloader() stores the classloader in the expected field. */
  @Test
  public void createCustomClassloader_setsFieldOnMainInstance() throws Exception {
    Main m = new Main();
    Field cclField = Main.class.getDeclaredField("customClassloader");
    cclField.setAccessible(true);
    assertThat(cclField.get(m), is(nullValue()));

    Method createCcl = Main.class.getDeclaredMethod("createCustomClassloader");
    createCcl.setAccessible(true);
    createCcl.invoke(m);

    assertThat(cclField.get(m), is(notNullValue()));
  }
}
