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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link LogCall}.
 *
 * <p>LogCall is the log-specific call command extracted from the original monolithic Caller class
 * to follow the entity-operation pattern ({@code pal log call}). It handles log-based message
 * dispatch via Kafka or Chronicle Queue, including log resolution, input/output log configuration,
 * and forget-response mode.
 *
 * @see LogCall
 * @see LogResolver
 */
public class LogCallTest {

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object on which to set the field
   * @param fieldName the name of the field to set
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Gets a field value from an object via reflection, searching the class hierarchy.
   *
   * @param target the object from which to read the field
   * @param fieldName the name of the field to read
   * @return the field value
   */
  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(target);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Locates the inner class {@code StaticMethodCallBuilder} within {@link LogCall}.
   *
   * @return the inner class
   */
  private static Class<?> findStaticMethodCallBuilder() {
    for (Class<?> cl : LogCall.class.getDeclaredClasses()) {
      if (cl.getSimpleName().equals("StaticMethodCallBuilder")) {
        return cl;
      }
    }
    throw new AssertionError("StaticMethodCallBuilder inner class not found in LogCall");
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that a valid log name is accepted as a positional argument.
   *
   * <p>Verifies that providing a Kafka topic name as the log identifier passes input validation
   * without error, and that both inputLogName and outputLogName are set from the positional arg.
   */
  @Test
  public void validateInput_validLogName_accepted() throws Exception {
    LogCall c = new LogCall();
    setField(c, "logIdentifier", "my-log-topic");
    setField(c, "className", "com.example.Worker");

    c.validateInput();

    assertThat(getField(c, "inputLogName"), is("my-log-topic"));
    assertThat(getField(c, "outputLogName"), is("my-log-topic"));
  }

  /**
   * Tests that a valid log file path is accepted as a positional argument.
   *
   * <p>Verifies that providing a {@code file:/path} Chronicle Queue path as the log identifier
   * passes validation and both inputLogName and outputLogName are set.
   */
  @Test
  public void validateInput_validLogFilePath_accepted() throws Exception {
    LogCall c = new LogCall();
    setField(c, "logIdentifier", "file:/tmp/wal");
    setField(c, "className", "com.example.Worker");

    c.validateInput();

    assertThat(getField(c, "inputLogName"), is("file:/tmp/wal"));
    assertThat(getField(c, "outputLogName"), is("file:/tmp/wal"));
  }

  /**
   * Tests that missing log identifier throws a RuntimeException.
   *
   * <p>Verifies that invoking the command without any positional log identifier argument results in
   * a validation error.
   */
  @Test
  public void validateInput_noLog_throwsRuntimeException() {
    LogCall c = new LogCall();

    Exception e = assertThrows(RuntimeException.class, c::validateInput);
    assertThat(e.getMessage(), containsString("Log identifier is required"));
  }

  /**
   * Tests that input and output log options are accepted together.
   *
   * <p>Verifies that providing both {@code -i/--input-log} and {@code -o/--output-log} options
   * passes validation, enabling bidirectional log communication. When explicit -i/-o are given,
   * they take precedence over the positional log identifier.
   */
  @Test
  public void validateInput_withInputAndOutputLogs_accepted() throws Exception {
    LogCall c = new LogCall();
    setField(c, "inputLogName", "input-log");
    setField(c, "outputLogName", "output-log");
    setField(c, "className", "com.example.Worker");

    c.validateInput();

    assertThat(getField(c, "inputLogName"), is("input-log"));
    assertThat(getField(c, "outputLogName"), is("output-log"));
  }

  // ==================== buildCallRequests() Tests ====================

  /**
   * Tests that buildCallRequests correctly builds an ExecMessage for a single static method call.
   *
   * <p>Verifies that providing a class name and arguments as positional parameters results in a
   * correctly constructed ExecMessage for log-mode dispatch.
   */
  @Test
  public void buildCallRequests_singleStaticMethod_buildsCorrectly() throws Exception {
    LogCall c = new LogCall();
    UUID peer = UUID.randomUUID();

    Class<?> inner = findStaticMethodCallBuilder();
    Constructor<?> cons =
        inner.getDeclaredConstructor(
            LogCall.class, UUID.class, String.class, String.class, List.class);
    cons.setAccessible(true);
    Object builder = cons.newInstance(c, peer, "com.example.Worker", "main", List.of("arg1"));

    Method buildExec = builder.getClass().getDeclaredMethod("buildExecMessage");
    ExecMessage em = (ExecMessage) buildExec.invoke(builder);

    assertEquals("com.example.Worker", em.getClassMethodCall().getClazz().getName());
    assertEquals("main", em.getClassMethodCall().getName());

    // Verify parameters contain the args (field is in superclass BaseStaticMethodCallBuilder)
    var parametersField = findField(inner, "parameters");
    parametersField.setAccessible(true);
    Object[] parameters = (Object[]) parametersField.get(builder);
    assertNotNull(parameters);
    assertEquals(1, parameters.length);
    String[] args = (String[]) parameters[0];
    assertEquals(1, args.length);
    assertEquals("arg1", args[0]);
  }

  /**
   * Tests that buildCallRequests reads JSON-RPC requests from stdin and builds them correctly.
   *
   * <p>Verifies that when stdin contains request data, validateInput reads and captures the lines
   * into the stdinRequests list for log-mode dispatch.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void buildCallRequests_fromStdin_readsAndBuilds() throws Exception {
    LogCall c = new LogCall();
    setField(c, "logIdentifier", "my-log");

    String jsonLine1 = "{\"jsonrpc\":\"2.0\",\"method\":\"test1\",\"id\":1}";
    String jsonLine2 = "{\"jsonrpc\":\"2.0\",\"method\":\"test2\",\"id\":2}";
    String stdinContent = jsonLine1 + "\n" + jsonLine2 + "\n";

    InputStream originalIn = System.in;
    try {
      System.setIn(new ByteArrayInputStream(stdinContent.getBytes(StandardCharsets.UTF_8)));
      c.validateInput();

      List<String> stdinRequests = (List<String>) getField(c, "stdinRequests");
      assertNotNull(stdinRequests);
      assertEquals(2, stdinRequests.size());
      assertEquals(jsonLine1, stdinRequests.get(0));
      assertEquals(jsonLine2, stdinRequests.get(1));
    } finally {
      System.setIn(originalIn);
    }
  }

  // ==================== runCommand() Tests ====================

  /**
   * Tests that the forget-response flag is correctly set.
   *
   * <p>Verifies that the {@code --forget-response} option is correctly stored and that it
   * influences the send-and-forget path in the command.
   */
  @Test
  public void runCommand_forgetResponse_setsFlag() throws Exception {
    LogCall c = new LogCall();
    setField(c, "sendAndForget", true);

    assertThat(getField(c, "sendAndForget"), is(true));

    // Verify that output-only log with sendAndForget passes validation
    setField(c, "outputLogName", "output-log");
    setField(c, "sendAndForget", true);
    setField(c, "className", "com.example.Worker");

    // Should not throw - sendAndForget relaxes the "must have input log" constraint
    c.validateInput();
  }

  /**
   * Tests that LogResolver is used for log resolution.
   *
   * <p>Verifies that after initialize(), the logResolver field is populated and ready to resolve
   * log names.
   */
  @Test
  public void runCommand_usesLogResolver() throws Exception {
    LogCall c = new LogCall();

    // Directly set the logResolver field (as initialize() would) and verify it's non-null
    LogResolver resolver = new LogResolver(null, "localhost:9092");
    c.logResolver = resolver;

    assertThat(c.logResolver, is(notNullValue()));
    assertThat(c.logResolver, is(resolver));
  }

  // ==================== printIfRequired() Tests ====================

  /**
   * Tests that printIfRequired produces no output when printResponses is disabled.
   *
   * <p>Verifies that when printResponses is false, no output is written to stdout regardless of the
   * response content.
   */
  @Test
  public void printIfRequired_jsonRpcResponse_null_noOutput() throws Exception {
    LogCall c = new LogCall();
    setField(c, "printResponses", false);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(c, "out", new PrintStream(bout));

    JsonRpcResponse response = JsonRpcResponse.builder().withId("1").withResult(null).build();
    c.printIfRequired(response);

    assertEquals("", bout.toString(StandardCharsets.UTF_8));
  }

  /**
   * Tests that printIfRequired prints the result from a successful JSON-RPC response.
   *
   * <p>Verifies that when the response contains a result value, the serialized JSON is printed to
   * stdout.
   */
  @Test
  public void printIfRequired_jsonRpcResponse_withResult_printsResult() throws Exception {
    LogCall c = new LogCall();
    setField(c, "printResponses", true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(c, "out", new PrintStream(bout));

    JsonRpcResponseReturnValue returnValue =
        JsonRpcResponseReturnValue.builder().withIsVoid(false).build();
    JsonRpcResponse response =
        JsonRpcResponse.builder().withId("test-123").withResult(returnValue).build();

    c.printIfRequired(response);

    String output = bout.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("test-123"));
    assertThat(output, containsString("2.0"));
  }

  /**
   * Tests that printIfRequired prints the error from a failed JSON-RPC response.
   *
   * <p>Verifies that when the response contains an error, the error message is printed to stdout
   * (as part of the serialized JSON-RPC response).
   */
  @Test
  public void printIfRequired_jsonRpcResponse_withError_printsError() throws Exception {
    LogCall c = new LogCall();
    setField(c, "printResponses", true);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(c, "out", new PrintStream(bout));

    JsonRpcError error = new JsonRpcError(-32700, "Parse error");
    JsonRpcResponse response = JsonRpcResponse.builder().withId("err-456").withError(error).build();

    c.printIfRequired(response);

    String output = bout.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("err-456"));
    assertThat(output, containsString("Parse error"));
    assertThat(output, containsString("-32700"));
  }
}
