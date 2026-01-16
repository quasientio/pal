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

import static io.quasient.pal.core.service.SelfBootstrapInvoker.EXIT_INVALID_RETURN_VALUE;
import static io.quasient.pal.core.service.SelfBootstrapInvoker.EXIT_MAIN_THREW_EXCEPTION;
import static io.quasient.pal.core.service.SelfBootstrapInvoker.EXIT_SUCCESS;
import static io.quasient.pal.core.service.SelfBootstrapInvoker.EXIT_UNEXPECTED_RESPONSE_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.net.URL;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Unit tests for {@link SelfBootstrapInvoker} exit code handling.
 *
 * <p>Tests verify that different return types and error conditions from main method invocations
 * produce the correct exit codes according to the new exit code constants.
 */
public class SelfBootstrapInvokerExitCodeTest {
  private ZContext ctx;
  private UUID peerId;
  private IncomingMessageDispatcher dispatcher;
  private MessageBuilder messageBuilder;
  private CustomClassloader classloader;
  private SelfBootstrapInvoker invoker;

  @Before
  public void setup() {
    ctx = new ZContext(1);
    peerId = UUID.randomUUID();
    dispatcher = mock(IncomingMessageDispatcher.class);
    messageBuilder = new MessageBuilder(peerId);
    classloader = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());

    invoker =
        new SelfBootstrapInvoker(
            peerId,
            dispatcher,
            messageBuilder,
            classloader,
            ctx,
            "inproc://offs",
            Collections.emptySet());
  }

  @After
  public void cleanup() {
    ctx.close();
  }

  @Test
  public void callMain_withVoidReturn_returnsExitSuccess() throws Exception {
    // Simulate a void main() method that returns null
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a return value with null object (void return)
              // Use Object.wait() as a sample void method
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              return messageBuilder.buildReturnValue(
                  null, voidMethod, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Void main() should return EXIT_SUCCESS", exitCode, is(EXIT_SUCCESS));
  }

  @Test
  public void callMain_withIntegerReturnZero_returnsZero() throws Exception {
    // Simulate main() returning Integer 0
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method m = Integer.class.getMethod("intValue");
              return messageBuilder.buildReturnValue(
                  Integer.valueOf(0), m, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Integer return 0 should return 0", exitCode, is(0));
  }

  @Test
  public void callMain_withIntegerReturnNonZero_returnsValue() throws Exception {
    // Simulate main() returning Integer 42
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method m = Integer.class.getMethod("intValue");
              return messageBuilder.buildReturnValue(
                  Integer.valueOf(42), m, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Integer return 42 should return 42", exitCode, is(42));
  }

  @Test
  public void callMain_withThrowable_returnsExitMainThrewException() throws Exception {
    // Simulate main() throwing an exception
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              RuntimeException ex = new RuntimeException("Test exception");
              return messageBuilder.buildAccessibleObjectThrowable(
                  peerId, null, ex, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat(
        "Exception from main() should return EXIT_MAIN_THREW_EXCEPTION",
        exitCode,
        is(EXIT_MAIN_THREW_EXCEPTION));
  }

  @Test
  public void callMain_withNonIntegerReturn_returnsExitInvalidReturnValue() throws Exception {
    // Simulate main() returning a String (invalid return type)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Manually build a message with String return value
              ExecMessage response = new ExecMessage();
              response.messageId = req.getMessageId();

              ReturnValue rv = new ReturnValue();
              Obj ov =
                  Wrapper.wrapInto(
                      new Obj(), "invalid string return", "java.lang.String", null, null);
              rv.setObject(ov);
              response.returnValue = rv;

              return response;
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat(
        "Non-Integer return should return EXIT_INVALID_RETURN_VALUE",
        exitCode,
        is(EXIT_INVALID_RETURN_VALUE));
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void callMain_withUnexpectedMessageType_returnsExitUnexpectedResponseType()
      throws Exception {
    // Simulate an unexpected message type in response
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a message with an unexpected type (e.g., EXEC_INSTANCE_METHOD)
              // by setting instanceMethodCall field instead of returnValue or raisedThrowable
              ExecMessage response = new ExecMessage();
              response.messageId = req.getMessageId();
              response.instanceMethodCall =
                  new io.quasient.pal.messages.colfer.InstanceMethodCall();
              return response;
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat(
        "Unexpected message type should return EXIT_UNEXPECTED_RESPONSE_TYPE",
        exitCode,
        is(EXIT_UNEXPECTED_RESPONSE_TYPE));
  }

  @Test
  public void callMain_withNegativeIntegerReturn_returnsNegativeValue() throws Exception {
    // Simulate main() returning a negative Integer (edge case)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method m = Integer.class.getMethod("intValue");
              return messageBuilder.buildReturnValue(
                  Integer.valueOf(-1), m, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Negative Integer return should be preserved", exitCode, is(-1));
  }

  @Test
  public void callMain_withLargeIntegerReturn_returnsValue() throws Exception {
    // Simulate main() returning a large Integer (will be truncated to byte by OS)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method m = Integer.class.getMethod("intValue");
              return messageBuilder.buildReturnValue(
                  Integer.valueOf(300), m, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Large Integer return should be preserved (OS will truncate)", exitCode, is(300));
  }

  @Test
  public void exitCodeConstants_haveExpectedValues() {
    // Verify the exit code constants have sensible values
    assertThat("EXIT_SUCCESS should be 0", EXIT_SUCCESS, is(0));
    assertThat("EXIT_MAIN_THREW_EXCEPTION should be 1", EXIT_MAIN_THREW_EXCEPTION, is(1));
    assertThat(
        "EXIT_UNEXPECTED_RESPONSE_TYPE should be 125", EXIT_UNEXPECTED_RESPONSE_TYPE, is(125));
    assertThat("EXIT_INVALID_RETURN_VALUE should be 126", EXIT_INVALID_RETURN_VALUE, is(126));
  }
}
