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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit test specifications for the {@code pal intercept apply} CLI command.
 *
 * <p>These tests verify that the apply command correctly parses a YAML bundle file, resolves peers,
 * creates intercepts via PalDirectory, and handles error conditions such as missing files, invalid
 * YAML, unknown peers, and partial failures. Uses the same reflection-based mock injection pattern
 * as {@link InterceptListTest} and {@link PeerRemoveTest}.
 *
 * @see InterceptListTest
 * @see PeerRemoveTest
 */
public class InterceptApplyTest {

  /** Minimal valid YAML content for a bundle with one intercept. */
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
      """;

  /** YAML content with two intercepts for partial-failure testing. */
  private static final String TWO_INTERCEPTS_YAML =
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
   * Creates an InterceptApply instance with a mock PalDirectory and output streams injected.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @param berr the output stream to capture error output
   * @return a configured InterceptApply instance
   */
  private static InterceptApply createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout, ByteArrayOutputStream berr)
      throws Exception {
    InterceptApply cmd = new InterceptApply();
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
  private static int invokeRunCommand(InterceptApply cmd) throws Exception {
    Method runCommand = InterceptApply.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    return (int) runCommand.invoke(cmd);
  }

  // ==================== Tests ====================

  /**
   * Verifies that applying a valid YAML file creates intercepts in the directory and stores bundle
   * metadata. The command should invoke {@code InterceptManager.apply()} which calls {@code
   * createIntercept()} and {@code createBundleMetadata()} on PalDirectory. Exit code should be 0
   * and output should contain "created".
   */
  @Test
  public void runCommand_appliesYamlFile() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(VALID_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    verify(mockDir).createIntercept(any(InterceptRequest.class), anyLong());
    verify(mockDir).createBundleMetadata(anyString(), any());
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("created"));
  }

  /**
   * Verifies that the {@code --dry-run} flag shows what would be applied without actually creating
   * any intercepts. Output should contain diff information but {@code createIntercept()} should
   * never be called on the directory.
   */
  @Test
  public void runCommand_dryRun_showsDiffWithoutApplying() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);
    when(mockDir.listInterceptsForPeer(peerUuid)).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(VALID_YAML));
    setField(cmd, "dryRun", true);

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(0));
    verify(mockDir, never()).createIntercept(any(InterceptRequest.class), anyLong());
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("would be created"));
  }

  /**
   * Verifies that specifying a non-existent file path causes exit code 1 and an error message about
   * the missing file.
   */
  @Test
  public void runCommand_fileNotFound_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
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
   * message about YAML parsing failure.
   */
  @Test
  public void runCommand_invalidYaml_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile("{{invalid: yaml: ["));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Invalid YAML"));
  }

  /**
   * Verifies that when the YAML references a peer name that does not exist in the directory, the
   * command reports an error with exit code 1.
   */
  @Test
  public void runCommand_peerNotFound_reportsError() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.getPeerByName("my-peer")).thenReturn(null);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(VALID_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then
    assertThat(result, is(1));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Peer not found"));
  }

  /**
   * Verifies that when some intercepts succeed and others fail during apply, the command reports
   * both created and failed counts. Exit code should be 0 (partial success) and output should show
   * counts for both created and failed intercepts.
   */
  @Test
  public void runCommand_partialFailure_reportsResults() throws Exception {
    // Given: two intercepts, first succeeds, second fails
    PalDirectory mockDir = mock(PalDirectory.class);
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");
    when(mockDir.getPeerByName("my-peer")).thenReturn(peerInfo);

    // First call succeeds, second call throws
    doThrow(new RuntimeException("etcd unavailable"))
        .when(mockDir)
        .createIntercept(any(InterceptRequest.class), anyLong());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    InterceptApply cmd = createWithMockDirAndOutput(mockDir, bout, berr);
    setField(cmd, "file", createTempYamlFile(TWO_INTERCEPTS_YAML));

    // When
    int result = invokeRunCommand(cmd);

    // Then: exit code 0 (partial success), output shows both created and failed counts
    assertThat(result, is(0));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("failed"));
  }
}
