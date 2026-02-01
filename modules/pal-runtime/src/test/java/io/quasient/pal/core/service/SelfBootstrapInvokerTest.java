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
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Unit tests for {@link SelfBootstrapInvoker}.
 *
 * <p>Tests cover error paths in JAR loading and exit value extraction.
 *
 * @see SelfBootstrapInvokerJarTest for additional JAR loading tests
 * @see SelfBootstrapInvokerExitCodeTest for additional exit code tests
 */
public class SelfBootstrapInvokerTest {

  private ZContext ctx;
  private UUID peerId;
  private IncomingMessageDispatcher dispatcher;
  private MessageBuilder messageBuilder;
  private CustomClassloader classloader;
  private SelfBootstrapInvoker invoker;
  private Path tempDir;

  /** Sets up the test fixtures. */
  @Before
  public void setup() throws IOException {
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

    tempDir = Files.createTempDirectory("selfbootstrap-test");
  }

  /** Cleans up the test fixtures. */
  @After
  public void cleanup() throws IOException {
    ctx.close();
    if (tempDir != null) {
      try (var walk = Files.walk(tempDir)) {
        walk.sorted((a, b) -> b.compareTo(a))
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    // ignore
                  }
                });
      }
    }
  }

  /**
   * Tests that callJar throws PeerException with ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST when given
   * a non-existent JAR file path.
   */
  @Test
  public void callJar_missingJarFile_throwsPeerException() {
    String nonExistentJar = "/path/to/nonexistent/file.jar";

    try {
      invoker.callJar(nonExistentJar, Collections.emptyList());
      fail("Expected PeerException for non-existent JAR");
    } catch (PeerException e) {
      assertThat(
          e.getFatalCode(), is(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST));
    }
  }

  /**
   * Tests that callJar throws PeerException with ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST when given a
   * JAR file without a Main-Class entry in its MANIFEST.MF.
   */
  @Test
  public void callJar_noMainClassInManifest_throwsPeerException() throws IOException {
    // Create a valid JAR without Main-Class attribute
    Path jarWithoutMain = tempDir.resolve("no-main.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    // No Main-Class attribute

    try (FileOutputStream fos = new FileOutputStream(jarWithoutMain.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // Empty JAR with just manifest
    }

    try {
      invoker.callJar(jarWithoutMain.toString(), Collections.emptyList());
      fail("Expected PeerException for JAR without Main-Class");
    } catch (PeerException e) {
      assertThat(e.getFatalCode(), is(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST));
    }
  }

  /**
   * Tests that getExitValueFromResponse returns EXIT_UNEXPECTED_RESPONSE_TYPE (125) when the
   * response message has an unexpected type (not RETURN_VALUE, GET_STATIC, GET_FIELD, or
   * THROWABLE).
   */
  @Test
  public void getExitValueFromResponse_unexpectedMessageType_returns125() throws Exception {
    // Mock dispatcher to return an ExecMessage with an unexpected type (EXEC_INSTANCE_METHOD)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a message with an unexpected type by setting instanceMethodCall field
              ExecMessage response = new ExecMessage();
              response.messageId = req.getMessageId();
              response.instanceMethodCall = new InstanceMethodCall();
              return response;
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat(
        "Unexpected message type should return EXIT_UNEXPECTED_RESPONSE_TYPE",
        exitCode,
        is(EXIT_UNEXPECTED_RESPONSE_TYPE));
  }

  /**
   * Tests that getIntFromReturnValue returns EXIT_INVALID_RETURN_VALUE (126) when unwrapping the
   * return value fails due to ClassNotFoundException.
   */
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getIntFromReturnValue_unwrapFails_returns126() throws Exception {
    // Mock dispatcher to return an ExecMessage with a return value containing
    // a class name that cannot be found (causes ClassNotFoundException in Unwrapper)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              ExecMessage response = new ExecMessage();
              response.messageId = req.getMessageId();

              // Create a return value with a non-existent class type
              ReturnValue rv = new ReturnValue();
              Obj obj = new Obj();
              obj.value = "\"some value\""; // JSON value
              obj.clazz = new io.quasient.pal.messages.colfer.Class();
              obj.clazz.name = "com.nonexistent.ClassThatDoesNotExist";
              obj.isNull = false;
              rv.setObject(obj);
              response.returnValue = rv;

              return response;
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat(
        "Unwrap failure should return EXIT_INVALID_RETURN_VALUE",
        exitCode,
        is(EXIT_INVALID_RETURN_VALUE));
  }

  /**
   * Tests that getIntFromReturnValue returns EXIT_SUCCESS (0) when the return value is null,
   * indicating a void return from main method.
   */
  @Test
  public void getIntFromReturnValue_voidReturn_returns0() throws Exception {
    // Mock dispatcher to return an ExecMessage with null return value (void main)
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a return value with null object (void return)
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              return messageBuilder.buildReturnValue(
                  null, voidMethod, null, false, req.getMessageId());
            });

    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());
    assertThat("Void main() should return EXIT_SUCCESS", exitCode, is(EXIT_SUCCESS));
  }

  /**
   * Tests that getExitValueFromResponse returns EXIT_MAIN_THREW_EXCEPTION (1) when the response
   * message is of type EXEC_THROWABLE.
   */
  @Test
  public void getExitValueFromResponse_throwableMessage_returns1() throws Exception {
    // Mock dispatcher to return an ExecMessage with EXEC_THROWABLE type
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
}
