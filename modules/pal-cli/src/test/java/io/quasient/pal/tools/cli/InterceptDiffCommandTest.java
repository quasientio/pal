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
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit test specifications for the {@code pal intercept diff} CLI command.
 *
 * <p>These tests verify that the diff command correctly compares an intercept bundle YAML file
 * against the current directory state and displays create/unchanged/modified entries with a summary
 * line. Uses the same reflection-based mock injection pattern as {@link PeerRemoveTest}.
 *
 * @see PeerRemoveTest
 */
public class InterceptDiffCommandTest {

  /** YAML content with three intercepts for diff testing. */
  private static final String THREE_INTERCEPTS_YAML =
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
        - target: com.acme.OrderService.status
          type: AROUND
          callback:
            class: com.acme.Monitor
            method: observe
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
   * Creates an InterceptDiffCommand instance with a mock PalDirectory and output streams injected.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @param berr the output stream to capture error output
   * @return a configured InterceptDiffCommand instance
   */
  private static InterceptDiffCommand createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout, ByteArrayOutputStream berr)
      throws Exception {
    InterceptDiffCommand cmd = new InterceptDiffCommand();
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
  private static int invokeRunCommand(InterceptDiffCommand cmd) throws Exception {
    Method runCommand = InterceptDiffCommand.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    return (int) runCommand.invoke(cmd);
  }

  /**
   * Generates a deterministic UUID matching the InterceptManager convention.
   *
   * @param bundleName the bundle name
   * @param targetClass the target class
   * @param targetName the target method/field name
   * @param type the intercept type
   * @param callbackClass the callback class
   * @param callbackMethod the callback method
   * @return the deterministic UUID
   */
  private static UUID deterministicUuid(
      String bundleName,
      String targetClass,
      String targetName,
      InterceptType type,
      String callbackClass,
      String callbackMethod) {
    String seed =
        bundleName
            + "|"
            + targetClass
            + "|"
            + targetName
            + "|"
            + type
            + "|"
            + callbackClass
            + "|"
            + callbackMethod
            + "|";
    return UUID.nameUUIDFromBytes(seed.getBytes(UTF_8));
  }

  // ==================== Tests ====================

  /**
   * Verifies that the diff command shows a mix of create, unchanged, and modified entries when the
   * directory has some intercepts matching and some differing from the YAML spec.
   */
  @Test
  public void runCommand_showsDiff() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    // Intercept 1 (placeOrder/BEFORE) -> not in directory -> CREATE
    // Intercept 2 (refund/AFTER) -> exists, matches -> UNCHANGED
    UUID uuid2 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "refund",
            InterceptType.AFTER,
            "com.acme.FraudChecker",
            "audit");
    InterceptableMethodCall method2 =
        new InterceptableMethodCall("refund", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req2 =
        new InterceptRequest<>(
            uuid2,
            peerUuid,
            InterceptType.AFTER,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "audit",
            method2);

    // Intercept 3 (status/AROUND) -> exists, but different priority -> MODIFIED
    UUID uuid3 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "status",
            InterceptType.AROUND,
            "com.acme.Monitor",
            "observe");
    InterceptableMethodCall method3 =
        new InterceptableMethodCall("status", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> req3 =
        new InterceptRequest<>(
            uuid3,
            peerUuid,
            InterceptType.AROUND,
            "com.acme.OrderService",
            "com.acme.Monitor",
            "observe",
            method3,
            false,
            null,
            null,
            10,
            0L);

    Set<InterceptRequest<?>> existing = new HashSet<>();
    existing.add(req2);
    existing.add(req3);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existing);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptDiffCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("+"));
    assertThat(output, containsString("would be created"));
    assertThat(output, containsString("="));
    assertThat(output, containsString("already exists, matches"));
    assertThat(output, containsString("~"));
    assertThat(output, containsString("differs"));
    assertThat(output, containsString("1 to create, 1 unchanged, 1 to update"));
  }

  /**
   * Verifies that when the directory has no intercepts, all entries in the bundle are shown as "to
   * create".
   */
  @Test
  public void runCommand_allNew() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptDiffCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("3 to create, 0 unchanged, 0 to update"));
  }

  /**
   * Verifies that when all intercepts in the directory match the YAML spec exactly, all entries are
   * shown as "unchanged".
   */
  @Test
  public void runCommand_allUnchanged() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    // All three intercepts exist with matching configuration (default priority=0, etc.)
    UUID uuid1 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "placeOrder",
            InterceptType.BEFORE,
            "com.acme.FraudChecker",
            "verify");
    InterceptableMethodCall m1 = new InterceptableMethodCall("placeOrder", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> r1 =
        new InterceptRequest<>(
            uuid1,
            peerUuid,
            InterceptType.BEFORE,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "verify",
            m1);

    UUID uuid2 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "refund",
            InterceptType.AFTER,
            "com.acme.FraudChecker",
            "audit");
    InterceptableMethodCall m2 = new InterceptableMethodCall("refund", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> r2 =
        new InterceptRequest<>(
            uuid2,
            peerUuid,
            InterceptType.AFTER,
            "com.acme.OrderService",
            "com.acme.FraudChecker",
            "audit",
            m2);

    UUID uuid3 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "status",
            InterceptType.AROUND,
            "com.acme.Monitor",
            "observe");
    InterceptableMethodCall m3 = new InterceptableMethodCall("status", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> r3 =
        new InterceptRequest<>(
            uuid3,
            peerUuid,
            InterceptType.AROUND,
            "com.acme.OrderService",
            "com.acme.Monitor",
            "observe",
            m3);

    Set<InterceptRequest<?>> existing = new HashSet<>();
    existing.add(r1);
    existing.add(r2);
    existing.add(r3);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existing);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptDiffCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("0 to create, 3 unchanged, 0 to update"));
  }

  /** Verifies that specifying a non-existent file path causes exit code 1 and an error message. */
  @Test
  public void runCommand_fileNotFound_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptDiffCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", new File("/tmp/does-not-exist-" + UUID.randomUUID() + ".yaml"));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("File not found"));
  }

  /**
   * Verifies that providing a file with invalid YAML content causes exit code 1 and an error
   * message.
   */
  @Test
  public void runCommand_invalidYaml_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptDiffCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile("{{invalid: yaml: ["));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Invalid YAML"));
  }
}
