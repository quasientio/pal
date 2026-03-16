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
 * Unit test specifications for the {@code pal intercept status} CLI command.
 *
 * <p>These tests verify that the status command correctly reports which intercepts from a bundle
 * are active in the directory, supporting both file-based ({@code -f}) and bundle-name-based
 * ({@code --bundle}) lookups. Uses the same reflection-based mock injection pattern as {@link
 * PeerRemoveTest}.
 *
 * @see PeerRemoveTest
 */
public class InterceptStatusCommandTest {

  /** YAML content with three intercepts for status testing. */
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
   * Creates an InterceptStatusCommand instance with a mock PalDirectory and output streams
   * injected.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @param berr the output stream to capture error output
   * @return a configured InterceptStatusCommand instance
   */
  private static InterceptStatusCommand createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout, ByteArrayOutputStream berr)
      throws Exception {
    InterceptStatusCommand cmd = new InterceptStatusCommand();
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
  private static int invokeRunCommand(InterceptStatusCommand cmd) throws Exception {
    Method runCommand = InterceptStatusCommand.class.getDeclaredMethod("runCommand");
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
   * Verifies that the status command shows per-intercept active/not-found status when invoked with
   * the {@code -f} file flag, and displays a summary like "2/3 active".
   */
  @Test
  public void runCommand_statusByFile() throws Exception {
    // Given: 3 intercepts, 2 active in directory
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    // Only uuid1 and uuid2 exist in directory
    UUID uuid1 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "placeOrder",
            InterceptType.BEFORE,
            "com.acme.FraudChecker",
            "verify");
    UUID uuid2 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "refund",
            InterceptType.AFTER,
            "com.acme.FraudChecker",
            "audit");

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

    Set<InterceptRequest<?>> existing = new HashSet<>();
    existing.add(r1);
    existing.add(r2);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existing);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("2/3 active"));
  }

  /**
   * Verifies that the status command works with the {@code --bundle} flag by reading bundle
   * metadata from the directory via {@code getBundleMetadata()}.
   */
  @Test
  public void runCommand_statusByBundle() throws Exception {
    // Given: bundle metadata with 3 UUIDs, 2 of which exist
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    UUID uuid3 = UUID.randomUUID();

    BundleMetadata metadata =
        new BundleMetadata("my-bundle", peerUuid, List.of(uuid1, uuid2, uuid3), Instant.now(), 1);
    when(mockDir.getBundleMetadata("my-bundle")).thenReturn(metadata);

    // Only uuid1 and uuid2 exist in directory
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

    Set<InterceptRequest<?>> existing = new HashSet<>();
    existing.add(r1);
    existing.add(r2);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(existing);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "bundleName", "my-bundle");

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("2/3 active"));
  }

  /**
   * Verifies that when all intercepts are found in the directory, the summary shows all active
   * (e.g., "3/3 active").
   */
  @Test
  public void runCommand_allActive() throws Exception {
    // Given: 3 intercepts, all active in directory
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    UUID uuid1 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "placeOrder",
            InterceptType.BEFORE,
            "com.acme.FraudChecker",
            "verify");
    UUID uuid2 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "refund",
            InterceptType.AFTER,
            "com.acme.FraudChecker",
            "audit");
    UUID uuid3 =
        deterministicUuid(
            "test-bundle",
            "com.acme.OrderService",
            "status",
            InterceptType.AROUND,
            "com.acme.Monitor",
            "observe");

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
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("3/3 active"));
  }

  /**
   * Verifies that when no intercepts are found in the directory, the summary shows none active
   * (e.g., "0/3 active").
   */
  @Test
  public void runCommand_noneActive() throws Exception {
    // Given: 3 intercepts, none active in directory
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(THREE_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("0/3 active"));
  }

  /**
   * Verifies that when {@code getBundleMetadata()} returns null for the given bundle name, the
   * command reports an error with exit code 1.
   */
  @Test
  public void runCommand_bundleNotFound_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getBundleMetadata("nonexistent-bundle")).thenReturn(null);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "bundleName", "nonexistent-bundle");

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("No bundle metadata found"));
  }

  /**
   * Verifies that when neither {@code -f} nor {@code --bundle} is provided, the command prints a
   * usage or error message and returns exit code 1.
   */
  @Test
  public void runCommand_noFileOrBundle_printsUsage() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptStatusCommand cmd = createWithMockDirAndOutput(mockDir, bout, berr);

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Specify either"));
  }
}
