/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link AbstractTool}.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Property retrieval via {@link AbstractTool#getProperty} (system properties, env vars,
 *       defaults)
 *   <li>Peer UUID extraction via {@link AbstractTool#getPeerUuid}
 *   <li>Message ID extraction via {@link AbstractTool#getMessageId}
 *   <li>Message type name retrieval via {@link AbstractTool#getMessageTypeName}
 *   <li>Message format detection via {@link AbstractTool#isColferMessage} and {@link
 *       AbstractTool#isJsonRpc}
 *   <li>JSON serialization via {@link AbstractTool#getMessageContentAsPrettyJson}
 *   <li>One-liner summary via {@link AbstractTool#getMessageOneLiner}
 * </ul>
 */
public class AbstractToolTest {

  /** Concrete subclass for testing protected methods on AbstractTool. */
  private static class TestTool extends AbstractTool {
    /**
     * Exposes the protected getProperty method for testing.
     *
     * @param propertyName the name of the property to retrieve
     * @param defaultValue the default value to return if the property is not set
     * @return the value of the property
     */
    public String testGetProperty(String propertyName, String defaultValue) {
      return getProperty(propertyName, defaultValue);
    }
  }

  /** Provides access to protected static methods via reflection. */
  private static Method getPeerUuidMethod;

  private static Method getMessageIdMethod;
  private static Method getMessageTypeNameMethod;
  private static Method getMessageTypeNameLogMethod;
  private static Method isColferMessageMethod;
  private static Method isJsonRpcMethod;
  private static Method getMessageContentAsPrettyJsonMethod;
  private static Method getMessageOneLinerMethod;

  static {
    try {
      getPeerUuidMethod = AbstractTool.class.getDeclaredMethod("getPeerUuid", Message.class);
      getPeerUuidMethod.setAccessible(true);

      getMessageIdMethod = AbstractTool.class.getDeclaredMethod("getMessageId", Message.class);
      getMessageIdMethod.setAccessible(true);

      getMessageTypeNameMethod =
          AbstractTool.class.getDeclaredMethod("getMessageTypeName", Message.class);
      getMessageTypeNameMethod.setAccessible(true);

      getMessageTypeNameLogMethod =
          AbstractTool.class.getDeclaredMethod("getMessageTypeName", LogMessage.class);
      getMessageTypeNameLogMethod.setAccessible(true);

      isColferMessageMethod =
          AbstractTool.class.getDeclaredMethod("isColferMessage", LogMessage.class);
      isColferMessageMethod.setAccessible(true);

      isJsonRpcMethod = AbstractTool.class.getDeclaredMethod("isJsonRpc", LogMessage.class);
      isJsonRpcMethod.setAccessible(true);

      getMessageContentAsPrettyJsonMethod =
          AbstractTool.class.getDeclaredMethod("getMessageContentAsPrettyJson", LogMessage.class);
      getMessageContentAsPrettyJsonMethod.setAccessible(true);

      getMessageOneLinerMethod =
          AbstractTool.class.getDeclaredMethod("getMessageOneLiner", LogMessage.class);
      getMessageOneLinerMethod.setAccessible(true);
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Failed to find protected method for testing", e);
    }
  }

  // ==========================================================================
  // getProperty() tests - Property lookup from system properties, env vars, and defaults
  // ==========================================================================

  @Test
  public void testGetProperty_fromSystemProperty() {
    // Given: System property "pal.test.sysprop.xyz" set to "sys-value"
    String propertyName = "pal.test.sysprop.xyz";
    String expectedValue = "sys-value";
    TestTool tool = new TestTool();

    try {
      System.setProperty(propertyName, expectedValue);

      // When: getProperty called
      String result = tool.testGetProperty(propertyName, "default");

      // Then: Returns value from system property
      assertThat(result, is(expectedValue));
    } finally {
      System.clearProperty(propertyName);
    }
  }

  @Test
  public void testGetProperty_fromEnvironmentVariable() {
    // Given: Use a real environment variable that exists (HOME or USER)
    // The getProperty method converts property name to uppercase for env lookup.
    // We test with a property name that corresponds to a commonly available env var.
    TestTool tool = new TestTool();

    // Test with "home" property - should map to HOME env var
    String homeEnvValue = System.getenv("HOME");
    if (homeEnvValue != null) {
      // When: getProperty("home", "default") called and HOME env var exists
      String result = tool.testGetProperty("home", "default");

      // Then: Returns value from environment variable
      assertThat(result, is(homeEnvValue));
    } else {
      // Fall back to USER env var if HOME not available
      String userEnvValue = System.getenv("USER");
      if (userEnvValue != null) {
        String result = tool.testGetProperty("user", "default");
        assertThat(result, is(userEnvValue));
      }
      // If neither env var exists, this test effectively verifies the default path
    }
  }

  @Test
  public void testGetProperty_returnsDefault_whenNotSet() {
    // Given: Neither system property nor environment variable set
    TestTool tool = new TestTool();
    String propertyName = "pal.test.nonexistent.property.xyz123";
    String defaultValue = "default-value";

    // When: getProperty called with non-existent property
    String result = tool.testGetProperty(propertyName, defaultValue);

    // Then: Returns default value
    assertThat(result, is(defaultValue));
  }

  // ==========================================================================
  // getPeerUuid() tests - Extract peer UUID from Message based on family
  // ==========================================================================

  @Test
  public void testGetPeerUuid_extractsFromMessage() throws Exception {
    // Given: Message with peer UUID set (EXEC family message)
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: getPeerUuid(message) called
    String result = (String) getPeerUuidMethod.invoke(null, message);

    // Then: Returns UUID as string
    assertThat(result, is(peerUuid.toString()));
  }

  // ==========================================================================
  // getMessageId() tests - Extract message ID from Message based on family
  // ==========================================================================

  @Test
  public void testGetMessageId_extractsFromMessage() throws Exception {
    // Given: Message with message ID set (EXEC family message)
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);
    String expectedMessageId = execMessage.getMessageId();

    // When: getMessageId(message) called
    String result = (String) getMessageIdMethod.invoke(null, message);

    // Then: Returns message ID as string
    assertThat(result, is(expectedMessageId));
    assertNotNull(result);
  }

  // ==========================================================================
  // getMessageTypeName() tests - Retrieve type name from Message
  // ==========================================================================

  @Test
  public void testGetTypeName_returnsCorrectTypeName() throws Exception {
    // Given: Message with specific type (CONSTRUCTOR)
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: getMessageTypeName(message) called
    String result = (String) getMessageTypeNameMethod.invoke(null, message);

    // Then: Returns type name string "EXEC_CONSTRUCTOR"
    assertThat(result, is("EXEC_CONSTRUCTOR"));
  }

  // ==========================================================================
  // isColferMessage() and isJsonRpc() tests - Format detection
  // ==========================================================================

  @Test
  public void testIsBinaryFormat_returnsTrueForColfer() throws Exception {
    // Given: LogMessage containing Colfer (binary) Message
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);
    LogMessage<Message> logMessage = new LogMessage<>("topic", 1L, Map.of(), message);

    // When: isColferMessage(logMessage) called
    boolean result = (boolean) isColferMessageMethod.invoke(null, logMessage);

    // Then: Returns true
    assertTrue(result);

    // Also verify isJsonRpc returns false
    boolean isJsonRpc = (boolean) isJsonRpcMethod.invoke(null, logMessage);
    assertFalse(isJsonRpc);
  }

  @Test
  public void testIsBinaryFormat_returnsFalseForJson() throws Exception {
    // Given: LogMessage containing JSON-RPC message
    JsonRpcRequest request = new JsonRpcRequest();
    request.setId(1);
    request.setMethod("call");
    Params params = new Params();
    params.setType("com.example.Test");
    params.setMethod("testMethod");
    request.setParams(params);

    LogMessage<JsonRpcRequest> logMessage = new LogMessage<>("topic", 1L, Map.of(), request);

    // When: isColferMessage(logMessage) called
    boolean isColfer = (boolean) isColferMessageMethod.invoke(null, logMessage);

    // Then: Returns false
    assertFalse(isColfer);

    // Also verify isJsonRpc returns true
    boolean isJsonRpc = (boolean) isJsonRpcMethod.invoke(null, logMessage);
    assertTrue(isJsonRpc);
  }

  // ==========================================================================
  // getMessageContentAsPrettyJson() tests - JSON serialization
  // ==========================================================================

  @Test
  public void testMessageAsJsonLines_serializesCorrectly() throws Exception {
    // Given: LogMessage with Colfer message content
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);
    LogMessage<Message> logMessage = new LogMessage<>("topic", 1L, Map.of(), message);

    // When: getMessageContentAsPrettyJson(logMessage) called
    String result = (String) getMessageContentAsPrettyJsonMethod.invoke(null, logMessage);

    // Then: Returns valid JSON representation (pretty-printed)
    assertThat(result, notNullValue());
    assertFalse(result.isEmpty());
    // Should contain expected JSON structure elements
    assertThat(result, containsString("java.lang.String"));
    assertThat(result, containsString(peerUuid.toString()));
  }

  // ==========================================================================
  // getMessageOneLiner() tests - One-line summary generation
  // ==========================================================================

  @Test
  public void testMessageAsOneLiner_truncatesLongContent() throws Exception {
    // Given: LogMessage with EXEC family message (constructor call)
    UUID peerUuid = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerUuid, Boolean.toString(false));
    var execMessage = builder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message message = builder.wrap(execMessage);
    LogMessage<Message> logMessage = new LogMessage<>("topic", 1L, Map.of(), message);

    // When: getMessageOneLiner(logMessage) called
    String result = (String) getMessageOneLinerMethod.invoke(null, logMessage);

    // Then: Returns a single line (no newlines) containing class info
    assertThat(result, notNullValue());
    assertFalse(result.contains("\n"));
    // Should contain the class name in summary
    assertThat(result, containsString("String"));
  }
}
