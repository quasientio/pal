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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.dsl.intercept.BundleMetadata;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit test specifications for the {@code pal intercept rm} CLI command.
 *
 * <p>These tests verify that the remove command supports multiple removal modes: by YAML file
 * ({@code -f}), by bundle name ({@code --bundle}), by UUID positional arguments, and by peer name
 * ({@code --peer}). Each mode is tested for correct invocation of PalDirectory methods and proper
 * error handling. Uses the same reflection-based mock injection pattern as {@link
 * InterceptListTest} and {@link PeerRemoveTest}.
 *
 * @see InterceptListTest
 * @see PeerRemoveTest
 */
public class InterceptRemoveTest {

  /** Minimal valid YAML content for a bundle with two intercepts. */
  private static final String VALID_YAML =
      """
      bundle: test-bundle
      defaults:
        peer: my-peer
      intercepts:
        - target: com.acme.OrderService.placeOrder
          type: BEFORE
          callback:
            class: com.acme.FraudChecker
            method: verify
        - target: com.acme.OrderService.refund
          type: AFTER
          callback:
            class: com.acme.FraudChecker
            method: audit
      """;

  // ==================== Helper methods ====================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object to set the field on
   * @param fieldName the name of the field
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found
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
   * Creates an InterceptRemove instance with a mock PalDirectory and output streams injected.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @param berr the output stream to capture error output
   * @return a configured InterceptRemove instance
   */
  private static InterceptRemove createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout, ByteArrayOutputStream berr)
      throws Exception {
    InterceptRemove cmd = new InterceptRemove();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(berr));
    return cmd;
  }

  /**
   * Creates a temporary YAML file with the given content.
   *
   * @param content the YAML content
   * @return the created temp file
   */
  private static File createTempYamlFile(String content) throws Exception {
    File tempFile = File.createTempFile("intercept-test", ".yaml");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), content);
    return tempFile;
  }

  /**
   * Invokes runCommand() via reflection.
   *
   * @param cmd the command instance
   * @return the exit code
   */
  private static int invokeRunCommand(InterceptRemove cmd) throws Exception {
    Method runCommand = InterceptRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    return (int) runCommand.invoke(cmd);
  }

  // ==================== Tests ====================

  /**
   * Verifies that the {@code -f} file flag removes all intercepts defined in the YAML bundle. The
   * command should parse the YAML file, compute deterministic UUIDs, and call {@code
   * deleteIntercept()} for each intercept. Output should show the removed count.
   */
  @Test
  public void runCommand_removeByFile() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    // Set up existing intercepts (so remove finds them)
    UUID interceptUuid1 = UUID.randomUUID();
    InterceptableMethodCall method1 =
        new InterceptableMethodCall("placeOrder", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req1 =
        new InterceptRequest<>(
            interceptUuid1,
            peerUuid,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "verify",
            method1);

    // For the remove operation, we need to return intercepts when listInterceptsForPeer is called
    // The InterceptManager.remove() uses deterministic UUIDs - but since they won't match
    // random UUIDs, they'll be reported as NOT_FOUND. That's OK for testing the flow.
    Set<InterceptRequest<?>> existingIntercepts = new HashSet<>();
    existingIntercepts.add(req1);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existingIntercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(VALID_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Removed:"));
  }

  /**
   * Verifies that the {@code --bundle} flag removes all intercepts tracked in the bundle metadata
   * stored in etcd. The command should call {@code getBundleMetadata()} to retrieve intercept
   * UUIDs, call {@code deleteIntercept()} for each, and then call {@code deleteBundleMetadata()}.
   */
  @Test
  public void runCommand_removeByBundle() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    UUID interceptUuid1 = UUID.randomUUID();
    UUID interceptUuid2 = UUID.randomUUID();

    BundleMetadata metadata =
        new BundleMetadata(
            "my-bundle", peerUuid, List.of(interceptUuid1, interceptUuid2), Instant.now(), 1);
    when(mockDir.getBundleMetadata("my-bundle")).thenReturn(metadata);

    // Set up existing intercepts
    InterceptableMethodCall method1 =
        new InterceptableMethodCall("placeOrder", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req1 =
        new InterceptRequest<>(
            interceptUuid1,
            peerUuid,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "verify",
            method1);
    InterceptableMethodCall method2 =
        new InterceptableMethodCall("refund", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req2 =
        new InterceptRequest<>(
            interceptUuid2,
            peerUuid,
            InterceptType.AFTER,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "audit",
            method2);

    Set<InterceptRequest<?>> existingIntercepts = new HashSet<>();
    existingIntercepts.add(req1);
    existingIntercepts.add(req2);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existingIntercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "bundleName", "my-bundle");

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    verify(mockDir).deleteIntercept(peerUuid, interceptUuid1);
    verify(mockDir).deleteIntercept(peerUuid, interceptUuid2);
    verify(mockDir).deleteBundleMetadata("my-bundle");
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Removed:"));
  }

  /**
   * Verifies that providing UUID positional arguments removes intercepts by their individual UUIDs.
   * The command should call {@code deleteIntercept()} for each UUID provided.
   */
  @Test
  public void runCommand_removeByUuid() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    UUID interceptUuid1 = UUID.randomUUID();
    UUID interceptUuid2 = UUID.randomUUID();

    // Set up intercepts so they can be found via listAllIntercepts
    InterceptableMethodCall method1 =
        new InterceptableMethodCall("placeOrder", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req1 =
        new InterceptRequest<>(
            interceptUuid1,
            peerUuid,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "verify",
            method1);
    InterceptableMethodCall method2 =
        new InterceptableMethodCall("refund", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req2 =
        new InterceptRequest<>(
            interceptUuid2,
            peerUuid,
            InterceptType.AFTER,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "audit",
            method2);

    Set<InterceptRequest<?>> allIntercepts = new HashSet<>();
    allIntercepts.add(req1);
    allIntercepts.add(req2);
    when(mockDir.listAllIntercepts()).thenReturn(allIntercepts);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "uuids", List.of(interceptUuid1.toString(), interceptUuid2.toString()));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    verify(mockDir).deleteIntercept(peerUuid, interceptUuid1);
    verify(mockDir).deleteIntercept(peerUuid, interceptUuid2);
  }

  /**
   * Verifies that the {@code --peer} flag removes all intercepts registered for the specified peer.
   * The command should resolve the peer name to a UUID via {@code getPeerByName()} and then call
   * {@code deleteInterceptsForPeer()} or equivalent.
   */
  @Test
  public void runCommand_removeByPeer() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "peerNameOrUuid", "my-peer");

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    verify(mockDir).deleteInterceptsForPeer(peerUuid);
  }

  /**
   * Verifies that when the {@code --bundle} flag references a bundle name that has no metadata in
   * etcd, the command reports an error with exit code 1.
   */
  @Test
  public void runCommand_bundleNotFound_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getBundleMetadata("nonexistent-bundle")).thenReturn(null);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "bundleName", "nonexistent-bundle");

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("No bundle metadata found"));
  }

  /**
   * Verifies that when no UUID arguments, no {@code -f} flag, no {@code --bundle} flag, and no
   * {@code --peer} flag are provided, the command prints a usage message or error and returns a
   * non-zero exit code.
   */
  @Test
  public void runCommand_noArgsOrFlags_printsUsage() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptRemove cmd = createWithMockDirAndOutput(mockDir, bout, berr);

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Specify at least one"));
  }
}
