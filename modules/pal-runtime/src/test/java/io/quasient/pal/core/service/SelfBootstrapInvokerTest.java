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
import io.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.colfer.Wrapper;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

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

  // ===========================================================================
  // Test specifications for SelfBootstrapInvoker
  // ===========================================================================

  /**
   * Tests that callJar successfully loads and executes a valid JAR file.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: Valid JAR file with main class
   *   <li>When: callJar called
   *   <li>Then: JAR loaded; main method executed; result returned
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Create a temporary JAR with a valid Main-Class manifest entry
   *   <li>Mock the IncomingMessageDispatcher to return a valid response (void return)
   *   <li>Verify the exit code from main method execution is returned
   * </ul>
   */
  @Test
  public void testCallJar_loadsAndExecutesJar() throws Exception {
    // Given: Valid JAR file with main class in manifest
    Path validJar = tempDir.resolve("valid-main.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.example.MainClass");

    try (FileOutputStream fos = new FileOutputStream(validJar.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // JAR with manifest only - callJar just extracts Main-Class and delegates to callMain
    }

    // Mock dispatcher to return a void (null) return value indicating success
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a return value with null object (void return)
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              return messageBuilder.buildReturnValue(
                  null, voidMethod, null, false, req.getMessageId());
            });

    // When: callJar called
    int exitCode = invoker.callJar(validJar.toString(), Collections.emptyList());

    // Then: JAR loaded; main method executed; result returned with EXIT_SUCCESS
    assertThat("Valid JAR execution should return EXIT_SUCCESS", exitCode, is(EXIT_SUCCESS));
  }

  /**
   * Tests that callJar throws appropriate exception when JAR manifest is missing Main-Class.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: JAR file without Main-Class in manifest
   *   <li>When: callJar called
   *   <li>Then: Appropriate exception thrown
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Create a JAR with MANIFEST.MF but no Main-Class attribute
   *   <li>Verify PeerException thrown with ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST code
   * </ul>
   *
   * <p>Note: This test overlaps with existing test {@link
   * #callJar_noMainClassInManifest_throwsPeerException()} but uses the naming convention specified
   * for acceptance criteria tracking.
   */
  @Test
  public void testCallJar_missingManifest_throwsException() throws IOException {
    // Given: JAR file without Main-Class in manifest
    Path jarWithoutMain = tempDir.resolve("no-main-class.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    // No Main-Class attribute

    try (FileOutputStream fos = new FileOutputStream(jarWithoutMain.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // Empty JAR with just manifest
    }

    // When: callJar called
    // Then: Appropriate exception thrown (PeerException with ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST)
    try {
      invoker.callJar(jarWithoutMain.toString(), Collections.emptyList());
      fail("Expected PeerException for JAR without Main-Class");
    } catch (PeerException e) {
      assertThat(e.getFatalCode(), is(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST));
    }
  }

  /**
   * Tests that exceptions thrown by main method in JAR are propagated with correct exit code.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: JAR whose main throws exception
   *   <li>When: callJar called
   *   <li>Then: Exception propagated with correct exit code
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Create a JAR with a valid Main-Class attribute
   *   <li>Mock dispatcher to return EXEC_THROWABLE message type
   *   <li>Verify exit code is EXIT_MAIN_THREW_EXCEPTION (1)
   * </ul>
   */
  @Test
  public void testCallJar_mainThrowsException_propagatesCorrectly() throws Exception {
    // Given: JAR whose main throws exception
    Path validJar = tempDir.resolve("throwing-main.jar");
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "com.example.ThrowingMain");

    try (FileOutputStream fos = new FileOutputStream(validJar.toFile());
        JarOutputStream jos = new JarOutputStream(fos, manifest)) {
      // JAR with manifest only - callJar extracts Main-Class and delegates to callMain
    }

    // Mock dispatcher to return EXEC_THROWABLE response
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              RuntimeException ex = new RuntimeException("Main threw an exception");
              return messageBuilder.buildAccessibleObjectThrowable(
                  peerId, null, ex, req.getMessageId());
            });

    // When: callJar called
    int exitCode = invoker.callJar(validJar.toString(), Collections.emptyList());

    // Then: Exception propagated with correct exit code (EXIT_MAIN_THREW_EXCEPTION = 1)
    assertThat(
        "Exception from main() should return EXIT_MAIN_THREW_EXCEPTION",
        exitCode,
        is(EXIT_MAIN_THREW_EXCEPTION));
  }

  /**
   * Tests that getIntFromReturnValue returns 0 (EXIT_SUCCESS) for null return value.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: Null return value
   *   <li>When: getIntFromReturnValue called
   *   <li>Then: Returns 0
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Null return value indicates void main() method
   *   <li>Standard Java convention: void main() completion = success (exit 0)
   *   <li>Test via callMain with mocked dispatcher returning null object in ReturnValue
   * </ul>
   *
   * <p>Note: This test overlaps with existing test {@link
   * #getIntFromReturnValue_voidReturn_returns0()} but uses the naming convention specified for
   * acceptance criteria tracking.
   */
  @Test
  public void testGetIntFromReturnValue_nullValue_returnsZero() throws Exception {
    // Given: Null return value (void main method)
    // Mock dispatcher to return ExecMessage with null returnValue.object
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              // Build a return value with null object (void return)
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              return messageBuilder.buildReturnValue(
                  null, voidMethod, null, false, req.getMessageId());
            });

    // When: getIntFromReturnValue called (indirectly via callMain)
    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());

    // Then: Returns 0 (EXIT_SUCCESS)
    assertThat("Null return value should return EXIT_SUCCESS", exitCode, is(EXIT_SUCCESS));
  }

  /**
   * Tests that getIntFromReturnValue returns the integer value for Integer return.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: Integer return value
   *   <li>When: getIntFromReturnValue called
   *   <li>Then: Returns the integer value
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>When main() returns an Integer, that value becomes the exit code
   *   <li>Test with various values: 0, positive, negative
   *   <li>Test via callMain with mocked dispatcher returning wrapped Integer
   * </ul>
   */
  @Test
  public void testGetIntFromReturnValue_integerValue_returnsValue() throws Exception {
    // Given: Integer return value (42)
    final int expectedValue = 42;

    // Mock dispatcher to return ExecMessage with Integer wrapped in returnValue
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method intMethod = Integer.class.getMethod("intValue");
              return messageBuilder.buildReturnValue(
                  Integer.valueOf(expectedValue), intMethod, null, false, req.getMessageId());
            });

    // When: getIntFromReturnValue called (indirectly via callMain)
    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());

    // Then: Returns the integer value (42)
    assertThat("Integer return value should be returned as exit code", exitCode, is(expectedValue));
  }

  /**
   * Tests that getIntFromReturnValue returns error code for non-integer return values.
   *
   * <p>Specification:
   *
   * <ul>
   *   <li>Given: Non-integer return value (e.g., String)
   *   <li>When: getIntFromReturnValue called
   *   <li>Then: Returns appropriate error code
   * </ul>
   *
   * <p>Implementation notes:
   *
   * <ul>
   *   <li>Non-Integer return values cannot be used as exit codes
   *   <li>Should return EXIT_INVALID_RETURN_VALUE (126)
   *   <li>Test with String return type
   * </ul>
   */
  @Test
  public void testGetIntFromReturnValue_nonIntegerValue_returnsErrorCode() throws Exception {
    // Given: Non-integer return value (String)
    // Mock dispatcher to return ExecMessage with String wrapped in returnValue
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

    // When: getIntFromReturnValue called (indirectly via callMain)
    int exitCode = invoker.callMain("com.example.TestClass", Collections.emptyList());

    // Then: Returns EXIT_INVALID_RETURN_VALUE (126)
    assertThat(
        "Non-Integer return should return EXIT_INVALID_RETURN_VALUE",
        exitCode,
        is(EXIT_INVALID_RETURN_VALUE));
  }

  // ===========================================================================
  // Offset wait behavior tests
  // ===========================================================================

  /**
   * Tests that callMain does NOT wait for offset when WITH_WAL is set but WITH_WAL_INCOMING_CLI is
   * NOT set.
   *
   * <ul>
   *   <li>Given: SelfBootstrapInvoker constructed with runOptions containing WITH_WAL but not
   *       WITH_WAL_INCOMING_CLI
   *   <li>When: callMain() is invoked
   *   <li>Then: The offset subscriber setup and wait loop are skipped; callMain returns without
   *       blocking on offset publication
   * </ul>
   *
   * <p>Context: The AFTER message for CLI_RPC is only written to WAL when WITH_WAL_INCOMING_CLI is
   * set. If the AFTER message is not written, no offset is published for it, and the offset wait
   * would block forever. This test ensures the wait is skipped when the AFTER message won't be
   * written.
   */
  @Test
  public void callMain_withWalWithoutIncomingCli_doesNotWaitForOffset() throws Exception {
    // Given: SelfBootstrapInvoker with WITH_WAL enabled but WITHOUT WITH_WAL_INCOMING_CLI
    SelfBootstrapInvoker walOnlyInvoker =
        new SelfBootstrapInvoker(
            peerId,
            dispatcher,
            messageBuilder,
            classloader,
            ctx,
            "inproc://offs-wal-only",
            EnumSet.of(RunOptions.WITH_WAL));

    // Mock dispatcher to return void (null) return value
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              return messageBuilder.buildReturnValue(
                  null, voidMethod, null, false, req.getMessageId());
            });

    // When: callMain() is invoked - should complete without blocking on offset
    int exitCode = walOnlyInvoker.callMain("com.example.TestClass", Collections.emptyList());

    // Then: callMain returns without blocking on offset publication
    assertThat(exitCode, is(EXIT_SUCCESS));
  }

  /**
   * Tests that callMain DOES wait for offset when both WITH_WAL and WITH_WAL_INCOMING_CLI are set.
   *
   * <ul>
   *   <li>Given: SelfBootstrapInvoker constructed with runOptions containing both WITH_WAL and
   *       WITH_WAL_INCOMING_CLI
   *   <li>When: callMain() is invoked
   *   <li>Then: The offset subscriber is set up and callMain waits for the offset publication
   *       matching the response message ID before returning
   * </ul>
   *
   * <p>Context: When WITH_WAL_INCOMING_CLI is enabled, the AFTER message for CLI_RPC is written to
   * WAL, so the offset will be published. The offset wait behavior should remain active to ensure
   * all messages have been durably written before the peer exits.
   */
  @Test
  public void callMain_withWalAndIncomingCli_waitsForOffset() throws Exception {
    // Given: SelfBootstrapInvoker with both WITH_WAL and WITH_WAL_INCOMING_CLI enabled
    String offsetAddr = "inproc://offs-wal-cli";
    SelfBootstrapInvoker walCliInvoker =
        new SelfBootstrapInvoker(
            peerId,
            dispatcher,
            messageBuilder,
            classloader,
            ctx,
            offsetAddr,
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_CLI));

    // Set up a PUB socket to publish offset messages
    ZMQ.Socket pubSocket = ctx.createSocket(SocketType.PUB);
    pubSocket.bind(offsetAddr);

    // Capture the response message ID so we can publish a matching offset
    AtomicReference<String> responseMessageId = new AtomicReference<>();
    CountDownLatch dispatchComplete = new CountDownLatch(1);

    // Mock dispatcher to return void (null) return value and capture message ID
    when(dispatcher.incomingCall(any(), any(), any()))
        .thenAnswer(
            inv -> {
              ExecMessage req = (ExecMessage) inv.getArguments()[0];
              java.lang.reflect.Method voidMethod = Object.class.getMethod("wait");
              ExecMessage response =
                  messageBuilder.buildReturnValue(
                      null, voidMethod, null, false, req.getMessageId());
              responseMessageId.set(response.getMessageId());
              dispatchComplete.countDown();
              return response;
            });

    // When: callMain() is invoked on a separate thread (it will block waiting for offset)
    AtomicReference<Integer> exitCodeRef = new AtomicReference<>();
    Thread callThread =
        new Thread(
            () -> {
              try {
                exitCodeRef.set(
                    walCliInvoker.callMain("com.example.TestClass", Collections.emptyList()));
              } catch (PeerException e) {
                throw new RuntimeException(e);
              }
            });
    callThread.start();

    // Wait for dispatch to complete so we have the response message ID
    assertThat("Dispatch should complete", dispatchComplete.await(5, TimeUnit.SECONDS), is(true));

    // Allow time for the SUB socket to connect and the offset wait loop to start
    Thread.sleep(100);

    // Publish the matching offset
    new PublishedOffsetMsg(42L, responseMessageId.get()).send(pubSocket);

    // Then: callMain should complete after receiving the matching offset
    callThread.join(5000);
    assertThat("callMain should have completed", callThread.isAlive(), is(false));
    assertThat(exitCodeRef.get(), is(EXIT_SUCCESS));

    pubSocket.close();
  }
}
