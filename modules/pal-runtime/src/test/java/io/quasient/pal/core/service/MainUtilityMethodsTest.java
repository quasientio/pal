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
import static org.hamcrest.Matchers.nullValue;

import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;

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
}
